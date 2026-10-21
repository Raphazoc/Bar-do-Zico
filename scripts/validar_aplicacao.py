"""Valida a API Java em database gerado e removido somente por esta execução."""
import os
from pathlib import Path
import shutil
import subprocess
import argparse
import time
import urllib.request
import json
from uuid import uuid4
import psycopg
from psycopg import sql
from cadastrar_admin import ROOT,connection_settings
from java_tools import java_environment,java_tool


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--telas',action='store_true')
    parser.add_argument('--somente-telas',action='store_true')
    parser.add_argument('--treinamento',action='store_true')
    args=parser.parse_args()
    settings=connection_settings();database='bar_app_test_'+uuid4().hex;created=False
    admin=psycopg.connect(**{**settings,'dbname':'postgres'},autocommit=True)
    try:
        admin.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(database)));created=True
        env=java_environment();env.pop('DEBUG',None);env.pop('SPRING_PROFILES_ACTIVE',None);env.pop('SERVER_PORT',None);env.update(FLYWAY_URL=f"jdbc:postgresql://127.0.0.1:{settings['port']}/{database}",FLYWAY_USER=settings['user'],FLYWAY_PASSWORD=settings['password'],FLYWAY_SCHEMAS='bar',FLYWAY_DEFAULT_SCHEMA='bar',FLYWAY_CLEAN_DISABLED='true',FLYWAY_LOCATIONS='filesystem:'+str(ROOT/'apps/api/src/main/resources/db/migration').replace('\\','/'))
        subprocess.run([str(ROOT/'.tools/flyway-13.10.3/flyway.cmd'),'migrate'],cwd=ROOT,env=env,check=True)
        with psycopg.connect(**{**settings,'dbname':database}) as c:
            c.execute('GRANT USAGE ON SCHEMA bar TO bar_app')
            c.execute('GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA bar TO bar_app')
            c.execute('REVOKE ALL ON bar.flyway_schema_history FROM bar_app')
            c.execute('REVOKE INSERT,UPDATE,DELETE ON bar.permissions FROM bar_app')
            c.execute('GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA bar TO bar_app')
            c.execute('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA bar FROM PUBLIC')
            c.execute('GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA bar TO bar_app')
        env.update(DB_URL=f"jdbc:postgresql://127.0.0.1:{settings['port']}/{database}?currentSchema=bar",DB_USERNAME='bar_app',DB_PASSWORD=(ROOT/'.tools/docker-app-password.txt').read_text().strip(),BAR_TEST_DATABASE='true',BAR_UNIT_ID=str(uuid4()),BAR_TRAINING='true' if args.treinamento else 'false')
        if not args.somente_telas:
            subprocess.run([shutil.which('mvn') or 'mvn.cmd','-B','-ntp','-f','apps/api/pom.xml','test'],cwd=ROOT,env=env,check=True)
        if args.telas or args.somente_telas:
            subprocess.run([shutil.which('mvn') or 'mvn.cmd','-B','-ntp','-f','apps/api/pom.xml','package','-DskipTests'],cwd=ROOT,env=env,check=True)
            validate_web(settings,database,env)
        print('API validada com credencial bar_app e PostgreSQL real. Dados de desenvolvimento preservados.')
    finally:
        if created: admin.execute(sql.SQL('DROP DATABASE {} WITH (FORCE)').format(sql.Identifier(database)))
        admin.close()


def validate_web(settings,database,env):
    from argon2 import PasswordHasher
    unit=uuid4();user=uuid4();role=uuid4();email='teste-telas@example.test'
    password='Senha-temporaria-telas-'+uuid4().hex
    with psycopg.connect(**{**settings,'dbname':database}) as c:
        c.execute("INSERT INTO bar.establishments(id,name) VALUES(%s,'Bar de teste das telas')",(unit,))
        c.execute("INSERT INTO bar.users(id,establishment_id,name,email,password_hash) VALUES(%s,%s,'Operador de teste',%s,%s)",(user,unit,email,PasswordHasher().hash(password)))
        c.execute("INSERT INTO bar.roles(id,establishment_id,name) VALUES(%s,%s,'Administrador de teste')",(role,unit))
        c.execute('INSERT INTO bar.user_roles(establishment_id,user_id,role_id) VALUES(%s,%s,%s)',(unit,user,role))
        c.execute('INSERT INTO bar.role_permissions(establishment_id,role_id,permission_id) SELECT %s,%s,id FROM bar.permissions',(unit,role))
    env.update(BAR_UNIT_ID=str(unit),BAR_PORT='18080',BAR_BIND='127.0.0.1',BAR_DEVICE_ACCESS='true',SERVER_FORWARD_HEADERS_STRATEGY='NONE',BAR_E2E_URL='http://127.0.0.1:18080',BAR_E2E_EMAIL=email,BAR_E2E_PASSWORD=password,PLAYWRIGHT_BROWSERS_PATH=str(ROOT/'.tools/ms-playwright'))
    env['BAR_ANDROID_URLS']='https://192.168.0.8:'+('18443' if env.get('BAR_TRAINING')=='true' else '8443')
    env['BAR_BACKUP_DIRECTORY']=''
    env.pop('SPRING_PROFILES_ACTIVE',None)
    node=Path((ROOT/'.tools/node-path.txt').read_text().strip());env['PATH']=str(node)+os.pathsep+env['PATH']
    with (ROOT/'.tools/api-e2e.log').open('wb') as log:
        process=subprocess.Popen([java_tool('java'),'-jar',str(ROOT/'apps/api/target/bar-api-0.1.0.jar')],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    try:
        for _ in range(45):
            if process.poll() is not None: raise RuntimeError('API de teste não iniciou. Consulte .tools/api-e2e.log.')
            try:
                with urllib.request.urlopen('http://127.0.0.1:18080/api/health',timeout=1) as r:
                    if json.load(r).get('application')=='bar-do-zico':break
            except Exception: pass
            time.sleep(1)
        else:raise RuntimeError('API de teste indisponível.')
        subprocess.run([str(node/'npm.cmd'),'run','test:e2e'],cwd=ROOT/'apps/web',env=env,check=True)
    finally:
        process.terminate()
        try:process.wait(timeout=15)
        except subprocess.TimeoutExpired:process.kill();process.wait()


if __name__=='__main__':main()
