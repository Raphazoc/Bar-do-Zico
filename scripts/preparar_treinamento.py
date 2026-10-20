"""Prepara banco persistente e separado para o piloto. Nunca recria ou limpa bancos."""
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
from uuid import UUID, uuid4

import psycopg
from psycopg import sql
from argon2 import PasswordHasher
from cadastrar_admin import ROOT, connection_settings, write_private_credential

TRAINING_DIRECTORY = ROOT / '.tools' / 'training'


def validate_config(config, real_database):
    database = config.get('database', '')
    if not re.fullmatch(r'bar_training_[0-9a-f]{32}', database) or database == real_database:
        raise RuntimeError('Configuração de treinamento inválida. Banco real preservado.')
    for name in ('instance', 'unit', 'user'):
        UUID(config[name])
    return database


def prepare():
    settings = connection_settings()
    TRAINING_DIRECTORY.mkdir(parents=True, exist_ok=True)
    config_path = TRAINING_DIRECTORY / 'config.json'
    credential = TRAINING_DIRECTORY / 'acesso.txt'
    if not config_path.exists():
        config = dict(database='bar_training_' + uuid4().hex,
                      instance=str(uuid4()), unit=str(uuid4()), user=str(uuid4()))
        write_private_credential(config_path, json.dumps(config, indent=2))
    config = json.loads(config_path.read_text(encoding='utf-8'))
    database = validate_config(config, settings['dbname'])
    marker = 'Bar do Zico - treinamento ' + config['instance']
    with psycopg.connect(**{**settings, 'dbname': 'postgres'}, autocommit=True) as admin:
        # Impedir preparações simultâneas da mesma instalação.
        admin.execute('SELECT pg_advisory_lock(hashtextextended(%s,0))', ('training:' + database,))
        try:
            row = admin.execute("SELECT shobj_description(oid,'pg_database') FROM pg_database WHERE datname=%s", (database,)).fetchone()
            if row and row[0] != marker:
                raise RuntimeError('Banco com identificação diferente. Nenhum dado foi alterado.')
            if not row:
                admin.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(database)))
                admin.execute(sql.SQL('COMMENT ON DATABASE {} IS {}').format(sql.Identifier(database), sql.Literal(marker)))
            if not credential.exists():
                # Nunca gerar outra senha para um usuário que já existe.
                with psycopg.connect(**{**settings, 'dbname': database}) as c:
                    exists = c.execute("SELECT to_regclass('bar.users') IS NOT NULL").fetchone()[0]
                    if exists and c.execute('SELECT 1 FROM bar.users WHERE id=%s', (config['user'],)).fetchone():
                        raise RuntimeError('Arquivo de acesso ausente. A senha existente foi preservada; não foi redefinida.')
                write_private_credential(credential,
                    'AMBIENTE DE TREINAMENTO — NÃO USAR EM VENDAS REAIS\n'
                    'E-mail: treinamento@bar.local\nSenha inicial: ' + secrets.token_urlsafe(24) + '\n'
                    'Esta senha é exclusiva do treinamento. Não use a senha do Raphael aqui.\n')
            password = next(line.partition(':')[2].strip() for line in credential.read_text(encoding='utf-8').splitlines() if line.startswith('Senha inicial:'))
            env = os.environ.copy()
            env.update(FLYWAY_URL=f"jdbc:postgresql://127.0.0.1:{settings['port']}/{database}",
                       FLYWAY_USER=settings['user'], FLYWAY_PASSWORD=settings['password'],
                       FLYWAY_SCHEMAS='bar', FLYWAY_DEFAULT_SCHEMA='bar', FLYWAY_CLEAN_DISABLED='true',
                       FLYWAY_LOCATIONS='filesystem:' + (ROOT / 'apps/api/src/main/resources/db/migration').as_posix())
            subprocess.run([str(ROOT / '.tools/flyway-13.10.3/flyway.cmd'), 'migrate'],
                           cwd=ROOT, env=env, check=True)
            unit, user = UUID(config['unit']), UUID(config['user'])
            with psycopg.connect(**{**settings, 'dbname': database}) as c:
                existing = c.execute('SELECT id FROM bar.establishments WHERE id=%s', (unit,)).fetchone()
                if not existing:
                    c.execute("INSERT INTO bar.establishments(id,name) VALUES(%s,'TREINAMENTO — Bar do Zico')", (unit,))
                    c.execute("INSERT INTO bar.users(id,establishment_id,name,email,password_hash) VALUES(%s,%s,'Operador de treinamento','treinamento@bar.local',%s)", (user, unit, PasswordHasher().hash(password)))
                    role = c.execute("INSERT INTO bar.roles(establishment_id,name) VALUES(%s,'Administrador de treinamento') RETURNING id", (unit,)).fetchone()[0]
                    c.execute('INSERT INTO bar.user_roles(establishment_id,user_id,role_id) VALUES(%s,%s,%s)', (unit, user, role))
                    c.execute('INSERT INTO bar.role_permissions(establishment_id,role_id,permission_id) SELECT %s,%s,id FROM bar.permissions', (unit, role))
                    area = c.execute("INSERT INTO bar.areas(establishment_id,name) VALUES(%s,'Salão de treinamento') RETURNING id", (unit,)).fetchone()[0]
                    for number in ('T01', 'T02'):
                        c.execute('INSERT INTO bar.bar_tables(establishment_id,area_id,number) VALUES(%s,%s,%s)', (unit, area, number))
                    category = c.execute("INSERT INTO bar.categories(establishment_id,name) VALUES(%s,'Exemplos — preços fictícios') RETURNING id", (unit,)).fetchone()[0]
                    for name, price, destination in [('Água — exemplo', '5.00', 'BAR'), ('Porção — exemplo', '20.00', 'COZINHA')]:
                        c.execute('INSERT INTO bar.products(establishment_id,category_id,name,price,production_destination) VALUES(%s,%s,%s,%s,%s)', (unit, category, name, price, destination))
                    c.execute("INSERT INTO bar.cash_terminals(establishment_id,name) VALUES(%s,'Caixa de treinamento')", (unit,))
                    c.execute("INSERT INTO bar.audit_events(establishment_id,actor_id,action,resource_type,resource_id,reason,correlation_id) VALUES(%s,%s,'training.initialized','establishment',%s,'Dados fictícios para piloto em banco separado',%s)", (unit, user, unit, str(uuid4())))
                elif not c.execute('SELECT 1 FROM bar.users WHERE establishment_id=%s AND id=%s', (unit, user)).fetchone():
                    raise RuntimeError('Administrador de treinamento ausente. Configuração preservada para diagnóstico.')
                # Mesmo contrato restrito de execução da API real.
                for statement in (
                    'GRANT USAGE ON SCHEMA bar TO bar_app',
                    'GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA bar TO bar_app',
                    'REVOKE ALL ON bar.flyway_schema_history FROM bar_app',
                    'REVOKE INSERT,UPDATE,DELETE ON bar.permissions FROM bar_app',
                    'GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA bar TO bar_app',
                    'REVOKE ALL ON ALL FUNCTIONS IN SCHEMA bar FROM PUBLIC',
                    'GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA bar TO bar_app',
                ): c.execute(statement)
        finally:
            admin.execute('SELECT pg_advisory_unlock(hashtextextended(%s,0))', ('training:' + database,))
    print('Treinamento preparado em banco separado. Dados e senhas anteriores foram preservados.')
    print('Acesso exclusivo do treinamento:', credential)
    return config


if __name__ == '__main__': prepare()
