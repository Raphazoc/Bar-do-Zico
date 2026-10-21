"""Confere V16 -> V17 em banco local descartável, incluindo dados históricos preenchidos.

Não migra nem modifica os bancos de operação e treinamento. Credenciais são
obtidas da configuração local do Compose e passadas ao Flyway pelo ambiente.
"""
import hashlib
import json
import subprocess
from decimal import Decimal
from uuid import uuid4

import psycopg
from psycopg import sql

from cadastrar_admin import ROOT, connection_settings
from java_tools import java_environment


def snapshot(connection, columns):
    result = {}
    for table, fields in columns.items():
        rows = connection.execute(sql.SQL('SELECT {} FROM bar.{}').format(
            sql.SQL(',').join(map(sql.Identifier, fields)), sql.Identifier(table))).fetchall()
        serialized = sorted(json.dumps(row, default=str, ensure_ascii=False) for row in rows)
        result[table] = (len(rows), hashlib.sha256('\n'.join(serialized).encode()).hexdigest())
    return result


def rejects(connection, error_type, statement, parameters, description):
    try:
        with connection.transaction():
            connection.execute(statement, parameters)
    except error_type:
        return
    raise AssertionError(description)


def main():
    settings = connection_settings()
    database = 'bar_app_test_' + uuid4().hex
    with psycopg.connect(**{**settings, 'dbname': 'postgres'}, autocommit=True) as admin:
        admin.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(database)))
        try:
            env = java_environment()
            env.update(
                FLYWAY_URL=f"jdbc:postgresql://127.0.0.1:{settings['port']}/{database}",
                FLYWAY_USER=settings['user'], FLYWAY_PASSWORD=settings['password'],
                FLYWAY_SCHEMAS='bar', FLYWAY_DEFAULT_SCHEMA='bar',
                FLYWAY_CLEAN_DISABLED='true',
                FLYWAY_LOCATIONS='filesystem:' + (ROOT / 'apps/api/src/main/resources/db/migration').as_posix())
            flyway = str(ROOT / '.tools/flyway-13.10.3/flyway.cmd')
            subprocess.run([flyway, '-target=16', 'migrate'], env=env, cwd=ROOT, check=True)
            unit, other_unit, owner, deleted, area, table, account, terminal, session = [uuid4() for _ in range(9)]
            with psycopg.connect(**{**settings, 'dbname': database}) as c:
                c.execute("INSERT INTO bar.establishments(id,name) VALUES(%s,'Teste V17 preenchido'),(%s,'Outra unidade V17')", (unit, other_unit))
                c.execute("""INSERT INTO bar.users(id,establishment_id,name,email,username,password_hash)
                    VALUES(%s,%s,'Responsável anterior','responsavel@example.test','responsavel','hash-sem-uso-no-teste-de-migracao'),
                          (%s,%s,'Funcionário histórico','historico@example.test','historico','hash-sem-uso-no-teste-de-migracao')""",
                          (owner, unit, deleted, unit))
                c.execute("INSERT INTO bar.areas(id,establishment_id,name) VALUES(%s,%s,'Salão anterior')", (area, unit))
                c.execute("INSERT INTO bar.bar_tables(id,establishment_id,area_id,number) VALUES(%s,%s,%s,'Mesa 7')", (table, unit, area))
                c.execute("""INSERT INTO bar.accounts(id,establishment_id,table_id,opened_by,status,closed_at)
                    VALUES(%s,%s,%s,%s,'FECHADA',CURRENT_TIMESTAMP)""", (account, unit, table, deleted))
                c.execute('UPDATE bar.users SET active=false,deleted_at=CURRENT_TIMESTAMP WHERE id=%s', (deleted,))
                c.execute("INSERT INTO bar.cash_terminals(id,establishment_id,name) VALUES(%s,%s,'Caixa anterior')", (terminal, unit))
                c.execute("""INSERT INTO bar.cash_sessions(id,establishment_id,terminal_id,operator_id,opening_amount)
                    VALUES(%s,%s,%s,%s,123.45)""", (session, unit, terminal, owner))
                tables = [r[0] for r in c.execute("SELECT tablename FROM pg_tables WHERE schemaname='bar' AND tablename<>'flyway_schema_history' ORDER BY tablename")]
                columns = {name: [r[0] for r in c.execute("""SELECT column_name FROM information_schema.columns
                    WHERE table_schema='bar' AND table_name=%s ORDER BY ordinal_position""", (name,))] for name in tables}
                previous = snapshot(c, columns)
                checksums = c.execute("SELECT version,checksum FROM bar.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank").fetchall()
                assert [v for v, _ in checksums] == [str(i) for i in range(1, 17)]

            subprocess.run([flyway, '-target=17', 'migrate'], env=env, cwd=ROOT, check=True)
            subprocess.run([flyway, '-target=17', 'validate'], env=env, cwd=ROOT, check=True)
            with psycopg.connect(**{**settings, 'dbname': database}) as c:
                assert snapshot(c, columns) == previous, 'Dados anteriores foram alterados pela migração'
                assert c.execute("SELECT version,checksum FROM bar.flyway_schema_history WHERE version IS NOT NULL AND version<>'17' ORDER BY installed_rank").fetchall() == checksums
                assert c.execute('SELECT count(*) FROM bar.users WHERE system_attendant IS DISTINCT FROM false').fetchone()[0] == 0
                assert c.execute('SELECT opening_amount FROM bar.cash_sessions WHERE id=%s', (session,)).fetchone()[0] == Decimal('123.45')
                assert c.execute('SELECT opened_by FROM bar.accounts WHERE id=%s', (account,)).fetchone()[0] == deleted
                assert c.execute('SELECT deleted_at IS NOT NULL AND NOT active FROM bar.users WHERE id=%s', (deleted,)).fetchone()[0]

                # The internal identity has no login identifier; normal users still default to false.
                internal = c.execute("""INSERT INTO bar.users(establishment_id,name,password_hash,system_attendant)
                    VALUES(%s,'Atendimento dos aparelhos','hash-sem-uso-no-teste-de-migracao',true)
                    RETURNING id,email,username,system_attendant""", (unit,)).fetchone()
                assert internal[1:] == (None, None, True)
                assert c.execute("""INSERT INTO bar.users(establishment_id,name,password_hash)
                    VALUES(%s,'Cadastro sem login','hash-sem-uso-no-teste-de-migracao')
                    RETURNING system_attendant""", (unit,)).fetchone()[0] is False
                rejects(c, psycopg.errors.UniqueViolation,
                    """INSERT INTO bar.users(establishment_id,name,password_hash,system_attendant)
                       VALUES(%s,'Atendimento duplicado','hash-sem-uso-no-teste-de-migracao',true)""", (unit,),
                    'Foi permitida mais de uma identidade interna na mesma unidade')
                # A different unit is allowed its own attendance identity.
                c.execute("""INSERT INTO bar.users(establishment_id,name,password_hash,system_attendant)
                    VALUES(%s,'Atendimento de outra unidade','hash-sem-uso-no-teste-de-migracao',true)""", (other_unit,))
                for identifier, value in [('email', 'interno@example.test'), ('username', 'interno')]:
                    rejects(c, psycopg.errors.CheckViolation,
                        sql.SQL('UPDATE bar.users SET {}=%s WHERE id=%s').format(sql.Identifier(identifier)),
                        (value, internal[0]), 'Identidade interna aceitou ' + identifier)
                c.rollback()  # Discard all identity probes, retaining only the original fixture.
                assert snapshot(c, columns) == previous, 'Dados anteriores mudaram após as verificações'
            print(f'V17 aprovada: hashes de {len(previous)} tabelas preservados; checksums V1–V16 intactos.')
            print('Usuários normal e excluído, mesa/conta histórica e abertura anterior de R$ 123,45 preservados.')
            print('Default false, identidade interna sem e-mail/username, rejeição de identificadores e unicidade por unidade aprovados.')
        finally:
            # This generated name is never taken from configuration or user input.
            if not database.startswith('bar_app_test_') or len(database) != len('bar_app_test_') + 32:
                raise RuntimeError('Nome inesperado de banco descartável; remoção interrompida.')
            admin.execute(sql.SQL('DROP DATABASE {} WITH (FORCE)').format(sql.Identifier(database)))
            print('Banco descartável removido; operação e treinamento não foram modificados.')


if __name__ == '__main__':
    main()
