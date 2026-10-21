"""Testa bootstrap e API com acesso fechado/proxy HTTPS em banco descartável local.

Não conecta ao Azure, não publica serviços e não altera os bancos do bar.
"""
from concurrent.futures import ThreadPoolExecutor
import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from uuid import uuid4

import psycopg
from psycopg import sql
from psycopg.conninfo import make_conninfo
from cadastrar_admin import ROOT, connection_settings, write_private_credential
from java_tools import java_environment, java_tool


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--imagem',help='Executar a imagem Docker informada, somente com publicação em loopback.')
    args=parser.parse_args()
    settings = connection_settings()
    database = 'bar_cloud_test_' + uuid4().hex
    unit = uuid4()
    process = None
    container = None
    environment_file = None
    with psycopg.connect(**{**settings, 'dbname': 'postgres'}, autocommit=True) as admin:
        admin.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(database)))
        try:
            env = java_environment()
            for key in ('SPRING_PROFILES_ACTIVE', 'SERVER_PORT', 'SERVER_ADDRESS', 'DEBUG'):
                env.pop(key, None)
            env.update(FLYWAY_URL=f"jdbc:postgresql://127.0.0.1:{settings['port']}/{database}",
                       FLYWAY_USER=settings['user'], FLYWAY_PASSWORD=settings['password'],
                       FLYWAY_SCHEMAS='bar', FLYWAY_DEFAULT_SCHEMA='bar', FLYWAY_CLEAN_DISABLED='true',
                       FLYWAY_LOCATIONS='filesystem:' + (ROOT/'apps/api/src/main/resources/db/migration').as_posix())
            subprocess.run([str(ROOT/'.tools/flyway-13.10.3/flyway.cmd'), 'migrate'], env=env, cwd=ROOT, check=True)
            password = 'Teste-nuvem-' + uuid4().hex
            env.update(AZURE_ADMIN_DATABASE_URL=make_conninfo(**{**settings, 'dbname': database}),
                       BAR_UNIT_ID=str(unit), BAR_UNIT_NAME='Piloto descartável', BAR_ADMIN_NAME='Responsável do teste',
                       BAR_ADMIN_USERNAME='admin-piloto', BAR_ADMIN_PASSWORD=password)

            def bootstrap():
                return subprocess.run([sys.executable, 'scripts/bootstrap_azure.py'], env=env, cwd=ROOT, capture_output=True)

            with ThreadPoolExecutor(max_workers=2) as executor:
                results = list(executor.map(lambda _: bootstrap(), range(2)))
            assert all(result.returncode == 0 for result in results), 'Bootstrap simultâneo falhou.'
            with psycopg.connect(**{**settings, 'dbname': database}) as c:
                for table in ('establishments', 'users', 'roles', 'user_roles'):
                    assert c.execute(sql.SQL('SELECT count(*) FROM bar.{}').format(sql.Identifier(table))).fetchone()[0] == 1
                assert c.execute('SELECT count(*) FROM bar.role_permissions').fetchone()[0] == c.execute('SELECT count(*) FROM bar.permissions').fetchone()[0]
                original = c.execute('SELECT password_hash FROM bar.users').fetchone()[0]
            env['BAR_ADMIN_PASSWORD'] = 'Outra-senha-' + uuid4().hex
            assert bootstrap().returncode == 0
            with psycopg.connect(**{**settings, 'dbname': database}) as c:
                assert c.execute('SELECT password_hash FROM bar.users').fetchone()[0] == original
                c.execute('UPDATE bar.users SET active=false')
            assert bootstrap().returncode != 0, 'Bootstrap não pode reativar um administrador.'
            with psycopg.connect(**{**settings, 'dbname': database}) as c:
                c.execute('UPDATE bar.users SET active=true')
                c.execute('GRANT USAGE ON SCHEMA bar TO bar_app')
                c.execute('GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA bar TO bar_app')
                c.execute('REVOKE ALL ON bar.flyway_schema_history FROM bar_app')
                c.execute('REVOKE INSERT,UPDATE,DELETE ON bar.permissions FROM bar_app')
                c.execute('GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA bar TO bar_app')
                c.execute('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA bar FROM PUBLIC')
                c.execute('GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA bar TO bar_app')
            # Nenhuma credencial administrativa acompanha o processo da aplicação.
            env.pop('AZURE_ADMIN_DATABASE_URL')
            env.pop('BAR_ADMIN_PASSWORD')
            env.update(DB_URL=f"jdbc:postgresql://127.0.0.1:{settings['port']}/{database}?currentSchema=bar",
                       DB_USERNAME='bar_app', DB_PASSWORD=(ROOT/'.tools/docker-app-password.txt').read_text().strip(),
                       BAR_BIND='127.0.0.1', BAR_PORT='18082', BAR_OPEN_ACCESS='false', BAR_SECURE_COOKIE='true',
                       BAR_ALLOWED_HOSTS='bar-pilot.example', BAR_ANDROID_URLS='https://bar-pilot.example',
                       BAR_BACKUP_DIRECTORY='', BAR_TRAINING='false', SERVER_FORWARD_HEADERS_STRATEGY='framework')
            if args.imagem:
                container='bar-cloud-check-'+uuid4().hex
                environment_file=ROOT/'.tools'/('cloud-check-'+uuid4().hex+'.env')
                env.update(DB_URL=f'jdbc:postgresql://db:5432/{database}?currentSchema=bar',BAR_BIND='0.0.0.0',BAR_PORT='8080')
                names=('DB_URL','DB_USERNAME','DB_PASSWORD','BAR_UNIT_ID','BAR_BIND','BAR_PORT','BAR_OPEN_ACCESS',
                       'BAR_SECURE_COOKIE','BAR_ALLOWED_HOSTS','BAR_ANDROID_URLS','BAR_BACKUP_DIRECTORY','BAR_TRAINING','SERVER_FORWARD_HEADERS_STRATEGY')
                write_private_credential(environment_file,'\n'.join(key+'='+env[key] for key in names)+'\n')
                result=subprocess.run(['docker','run','--detach','--name',container,'--network','gestao-bar_default',
                                       '--publish','127.0.0.1:18082:8080','--env-file',str(environment_file),args.imagem],cwd=ROOT,capture_output=True,check=True)
            else:
                with (ROOT/'.tools/api-cloud-test.log').open('wb') as log:
                    process = subprocess.Popen([java_tool('java'), '-jar', str(ROOT/'apps/api/target/bar-api-0.1.0.jar')],
                                               cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT,
                                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)

            def request(path, body=None, extra=None):
                headers = {'Host': 'bar-pilot.example', 'X-Forwarded-Proto': 'https', 'Origin': 'https://bar-pilot.example', 'X-Bar-Request': '1'}
                headers.update(extra or {})
                data = None if body is None else json.dumps(body).encode()
                if data is not None: headers['Content-Type'] = 'application/json'
                req = urllib.request.Request('http://127.0.0.1:18082' + path, data=data, headers=headers)
                try: response = urllib.request.urlopen(req, timeout=3)
                except urllib.error.HTTPError as error: response = error
                with response: return response.status, response.headers, response.read()

            for _ in range(45):
                if process is not None and process.poll() is not None: raise RuntimeError('API de nuvem não iniciou. Consulte .tools/api-cloud-test.log.')
                try:
                    if request('/api/health')[0] == 200: break
                except OSError: pass
                time.sleep(1)
            else: raise RuntimeError('API de teste não respondeu.')
            assert request('/')[0] == 200
            assert request('/api/me')[0] == 401
            assert request('/api/state')[0] == 401
            assert request('/api/operator', {'userId': None})[0] == 401
            assert request('/api/login', {'username': 'admin-piloto', 'password': password}, {'Origin': 'https://outside.example'})[0] == 403
            status, headers, _ = request('/api/login', {'username': 'admin-piloto', 'password': password})
            assert status == 200, 'Login atrás do proxy HTTPS falhou.'
            cookie = headers['Set-Cookie']
            assert 'Secure' in cookie and 'HttpOnly' in cookie and 'SameSite=Strict' in cookie
            session = {'Cookie': cookie.split(';', 1)[0]}
            assert request('/api/me', extra=session)[0] == 200
            assert request('/api/setup/qr?address=https%3A%2F%2Fbar-pilot.example', extra=session)[0] == 200
            assert request('/api/operator', {'userId': None}, session)[0] == 403
            status, _, _ = request('/api/commands/category.save', {'name': 'Teste do proxy'}, {**session, 'Idempotency-Key': uuid4().hex})
            assert status == 200, 'Escrita autenticada atrás do proxy HTTPS falhou.'
            assert request('/api/me', extra={**session, 'Host': 'outside.example'})[0] == 403
            print('Nuvem validada localmente: bootstrap idempotente/simultâneo, senha preservada, acesso fechado, cookie seguro e proxy HTTPS. Azure não foi publicado.')
        finally:
            if process is not None:
                process.terminate()
                try: process.wait(timeout=15)
                except subprocess.TimeoutExpired: process.kill(); process.wait()
            try:
                if container is not None:
                    result=subprocess.run(['docker','logs',container],cwd=ROOT,capture_output=True)
                    (ROOT/'.tools/api-cloud-container-test.log').write_bytes(result.stdout+result.stderr)
                    # Só este nome aleatório, gerado nesta execução, é removido.
                    subprocess.run(['docker','rm','--force',container],cwd=ROOT,capture_output=True)
            finally:
                if environment_file is not None: environment_file.unlink(missing_ok=True)
                admin.execute(sql.SQL('DROP DATABASE {} WITH (FORCE)').format(sql.Identifier(database)))


if __name__ == '__main__': main()
