"""Cria banco descartável, executa Flyway real e testa integridade/concorrência.

Requer PGHOST, PGPORT, PGUSER, PGPASSWORD e Flyway CLI.
python scripts/validate_database.py --flyway caminho/para/flyway.cmd
O usuário de validação precisa de CREATEDB. Nunca limpa um banco existente.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
from uuid import uuid4

import psycopg
from psycopg import sql
from psycopg.conninfo import make_conninfo

ROOT=Path(__file__).resolve().parents[1]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--flyway',default=shutil.which('flyway') or shutil.which('flyway.cmd'))
    args=parser.parse_args()
    if not args.flyway:parser.error('Informe --flyway ou coloque o CLI no PATH.')
    host=os.environ.get('PGHOST','127.0.0.1')
    port=os.environ.get('PGPORT','5432')
    user=os.environ.get('PGUSER','bar_migrator')
    password=os.environ.get('PGPASSWORD')
    if not password:parser.error('Defina PGPASSWORD no ambiente, sem colocar a senha na linha de comando.')
    # Nome gerado aqui: nunca aceitar nome de banco a excluir como argumento.
    database='bar_test_'+uuid4().hex
    admin=psycopg.connect(host=host,port=port,user=user,password=password,dbname='postgres',autocommit=True)
    created=False
    try:
        admin.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(database)))
        created=True
        env=os.environ.copy()
        env.update(FLYWAY_URL=f'jdbc:postgresql://{host}:{port}/{database}',FLYWAY_USER=user,
            FLYWAY_PASSWORD=password,FLYWAY_SCHEMAS='bar',FLYWAY_DEFAULT_SCHEMA='bar',
            FLYWAY_CLEAN_DISABLED='true',FLYWAY_VALIDATE_MIGRATION_NAMING='true',
            FLYWAY_LOCATIONS='filesystem:'+str(ROOT/'apps/api/src/main/resources/db/migration').replace('\\','/'))
        for command in ('migrate','validate','migrate'):
            subprocess.run([args.flyway,command],env=env,check=True,cwd=ROOT)
        env['BAR_TEST_DSN']=make_conninfo(host=host,port=port,user=user,password=password,dbname=database)
        subprocess.run([sys.executable,str(ROOT/'tests/database/test_schema.py')],env=env,check=True,cwd=ROOT)
        print('Validação concluída: migração limpa, checksums, reexecução e testes PostgreSQL reais.')
    finally:
        if created:
            # Somente o banco criado por esta execução; conexões encerradas antes do DROP.
            admin.execute(sql.SQL('DROP DATABASE {} WITH (FORCE)').format(sql.Identifier(database)))
        admin.close()


if __name__=='__main__':main()
