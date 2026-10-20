"""Cadastro inicial autorizado de administrador, com conexão local do Compose.

Não redefine senha de usuários existentes. Não envia e-mails.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
from uuid import UUID, uuid4

from argon2 import PasswordHasher
from argon2.profiles import RFC_9106_LOW_MEMORY
import psycopg
from psycopg.types.json import Jsonb

ROOT = Path(__file__).resolve().parents[1]


def connection_settings():
    docker = shutil.which('docker')
    if not docker and os.name == 'nt':
        candidate = Path(os.environ['LOCALAPPDATA']) / 'Programs/DockerDesktop/resources/bin/docker.exe'
        if candidate.is_file():
            docker = str(candidate)
    if not docker:
        raise RuntimeError('Docker CLI não encontrado.')
    result = subprocess.run([docker, 'compose', 'config', '--format', 'json'],
                            cwd=ROOT, capture_output=True, text=True, check=True)
    database = json.loads(result.stdout)['services']['db']
    port = next(p for p in database['ports'] if p['target'] == 5432)
    if port.get('host_ip') != '127.0.0.1':
        raise RuntimeError('Bootstrap permitido apenas no banco local em 127.0.0.1.')
    env = database['environment']
    return dict(host='127.0.0.1', port=port['published'], dbname=env['POSTGRES_DB'],
                user=env['POSTGRES_USER'], password=env['POSTGRES_PASSWORD'])


def write_private_credential(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    # Criar vazio, restringir acesso e só depois escrever a senha.
    with path.open('x', encoding='utf-8') as stream:
        if os.name == 'nt':
            sid = subprocess.run(['whoami', '/user', '/fo', 'csv', '/nh'],
                                 capture_output=True, text=True, check=True).stdout
            import csv
            user_sid = next(csv.reader([sid.strip()]))[1]
            result = subprocess.run(['icacls', str(path), '/inheritance:r', '/grant:r',
                                     f'*{user_sid}:(F)', '*S-1-5-18:(F)'],
                                    capture_output=True, text=True)
            if result.returncode:
                raise RuntimeError('Não foi possível restringir a credencial ao usuário local.')
        else:
            path.chmod(0o600)
        stream.write(content)
        stream.flush()
        os.fsync(stream.fileno())


def bootstrap(unit_id, name, email):
    name, email = name.strip(), email.strip().lower()
    if not name or '\n' in name or '\r' in name or len(name) > 150:
        raise ValueError('Nome inválido.')
    if email.count('@') != 1 or not all(email.split('@')) or any(c.isspace() for c in email) or len(email) > 254:
        raise ValueError('E-mail inválido.')
    hasher = PasswordHasher.from_parameters(RFC_9106_LOW_MEMORY)
    created = False
    password_path = None
    with psycopg.connect(**connection_settings()) as c:
        # Serializar cadastros iniciais concorrentes da mesma unidade.
        unit = c.execute('SELECT id,name FROM bar.establishments WHERE id=%s FOR UPDATE', (unit_id,)).fetchone()
        if not unit:
            raise RuntimeError('Unidade não encontrada. Nenhum cadastro alterado.')
        user = c.execute('SELECT id,name,active FROM bar.users WHERE establishment_id=%s AND email=%s FOR UPDATE',
                         (unit_id, email)).fetchone()
        if user:
            if user[1] != name or not user[2]:
                raise RuntimeError('Usuário existente com nome/situação diferente; cadastro preservado.')
            user_id = user[0]
        else:
            created = True
            user_id = uuid4()
            password = secrets.token_urlsafe(24)
            encoded = hasher.hash(password)
            assert hasher.verify(encoded, password)
            password_path = ROOT / '.tools' / 'credentials' / f'admin-{user_id}.txt'
            write_private_credential(password_path,
                f'Unidade: {unit[1]}\nNome: {name}\nE-mail: {email}\nSenha inicial: {password}\n\n'
                'Abra Iniciar Bar.cmd e entre com a senha inicial. Depois use Minha conta para trocá-la.\n')
            c.execute('''INSERT INTO bar.users(id,establishment_id,name,email,password_hash)
                         VALUES(%s,%s,%s,%s,%s)''', (user_id, unit_id, name, email, encoded))

        role = c.execute('''INSERT INTO bar.roles(establishment_id,name) VALUES(%s,'Administrador')
            ON CONFLICT(establishment_id,name) DO UPDATE SET name=EXCLUDED.name RETURNING id''', (unit_id,)).fetchone()[0]
        granted = c.execute('''INSERT INTO bar.role_permissions(establishment_id,role_id,permission_id)
            SELECT %s,%s,id FROM bar.permissions
            ON CONFLICT(establishment_id,role_id,permission_id) DO NOTHING RETURNING id''', (unit_id, role)).fetchall()
        linked = c.execute('''INSERT INTO bar.user_roles(establishment_id,user_id,role_id)
            VALUES(%s,%s,%s) ON CONFLICT(establishment_id,user_id,role_id) DO NOTHING RETURNING id''',
                           (unit_id, user_id, role)).fetchone()
        if created or linked or granted:
            correlation = 'bootstrap-' + uuid4().hex
            c.execute('''INSERT INTO bar.audit_events(establishment_id,action,resource_type,resource_id,
                reason,changes,correlation_id) VALUES(%s,'user.bootstrap_admin','user',%s,%s,%s,%s)''',
                (unit_id, user_id, 'Cadastro inicial autorizado pelo proprietário',
                 Jsonb(dict(name=name, email=email, role='Administrador')), correlation))
            c.execute('''INSERT INTO bar.outbox_events(establishment_id,aggregate_type,aggregate_id,event_type,payload)
                VALUES(%s,'user',%s,'user.admin_initialized',%s)''',
                (unit_id, user_id, Jsonb(dict(user_id=str(user_id), role_id=str(role)))))
        permission_count = c.execute('SELECT count(*) FROM bar.role_permissions WHERE establishment_id=%s AND role_id=%s',
                                     (unit_id, role)).fetchone()[0]
    print(json.dumps(dict(created=created, user_id=str(user_id), name=name, email=email, establishment=unit[1],
                          role='Administrador', permissions=permission_count,
                          credential_file=str(password_path) if password_path else None), ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--unidade', required=True, type=UUID)
    parser.add_argument('--nome', required=True)
    parser.add_argument('--email', required=True)
    args = parser.parse_args()
    bootstrap(args.unidade, args.nome, args.email)
