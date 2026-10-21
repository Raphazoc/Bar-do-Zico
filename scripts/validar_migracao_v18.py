"""Confere V17 → V18 com cadastros preenchidos em um banco descartável."""
import subprocess
from uuid import uuid4

import psycopg
from psycopg import sql
from cadastrar_admin import ROOT, connection_settings
from java_tools import java_environment
from validar_migracao_v17 import snapshot


def main():
    settings = connection_settings()
    database = 'bar_app_test_' + uuid4().hex
    with psycopg.connect(**{**settings, 'dbname': 'postgres'}, autocommit=True) as admin:
        admin.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(database)))
        try:
            env = java_environment()
            env.update(FLYWAY_URL=f"jdbc:postgresql://127.0.0.1:{settings['port']}/{database}",
                       FLYWAY_USER=settings['user'], FLYWAY_PASSWORD=settings['password'],
                       FLYWAY_SCHEMAS='bar', FLYWAY_DEFAULT_SCHEMA='bar', FLYWAY_CLEAN_DISABLED='true',
                       FLYWAY_LOCATIONS='filesystem:' + (ROOT/'apps/api/src/main/resources/db/migration').as_posix())
            flyway = str(ROOT/'.tools/flyway-13.10.3/flyway.cmd')
            subprocess.run([flyway, '-target=17', 'migrate'], env=env, cwd=ROOT, check=True)
            unit, other, user, other_user, area, table, account, terminal, cash = [uuid4() for _ in range(9)]
            manager, attendant, observer, other_manager = [uuid4() for _ in range(4)]
            with psycopg.connect(**{**settings, 'dbname': database}) as c:
                c.execute("INSERT INTO bar.establishments(id,name) VALUES(%s,'Teste V18'),(%s,'Outra unidade V18')", (unit, other))
                for user_id, unit_id in ((user, unit), (other_user, other)):
                    c.execute("INSERT INTO bar.users(id,establishment_id,name,password_hash) VALUES(%s,%s,'Responsável de teste','hash-sem-login-usado-apenas-no-teste')", (user_id, unit_id))
                for role, unit_id, code in ((manager, unit, 'table.manage'), (attendant, unit, 'account.open'),
                                            (observer, unit, 'report.view'), (other_manager, other, 'table.manage')):
                    c.execute('INSERT INTO bar.roles(id,establishment_id,name) VALUES(%s,%s,%s)', (role, unit_id, str(role)))
                    c.execute('INSERT INTO bar.role_permissions(establishment_id,role_id,permission_id) SELECT %s,%s,id FROM bar.permissions WHERE code=%s', (unit_id, role, code))
                c.execute("INSERT INTO bar.areas(id,establishment_id,name) VALUES(%s,%s,'Salão anterior')", (area, unit))
                c.execute("INSERT INTO bar.bar_tables(id,establishment_id,area_id,number) VALUES(%s,%s,%s,'7')", (table, unit, area))
                c.execute('INSERT INTO bar.accounts(id,establishment_id,table_id,opened_by) VALUES(%s,%s,%s,%s)', (account, unit, table, user))
                c.execute("UPDATE bar.bar_tables SET status='OCUPADA' WHERE id=%s", (table,))
                c.execute("INSERT INTO bar.cash_terminals(id,establishment_id,name) VALUES(%s,%s,'Caixa anterior')", (terminal, unit))
                c.execute('INSERT INTO bar.cash_sessions(id,establishment_id,terminal_id,operator_id,opening_amount) VALUES(%s,%s,%s,%s,123.45)', (cash, unit, terminal, user))
                tables = [r[0] for r in c.execute("SELECT tablename FROM pg_tables WHERE schemaname='bar' AND tablename NOT IN ('flyway_schema_history','permissions','role_permissions') ORDER BY tablename")]
                columns = {name: [r[0] for r in c.execute("SELECT column_name FROM information_schema.columns WHERE table_schema='bar' AND table_name=%s ORDER BY ordinal_position", (name,))] for name in tables}
                previous = snapshot(c, columns)
                permissions = set(c.execute('SELECT id,code,description FROM bar.permissions').fetchall())
                grants = set(c.execute('SELECT id,establishment_id,role_id,permission_id FROM bar.role_permissions').fetchall())
                checksums = c.execute("SELECT version,checksum FROM bar.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank").fetchall()
            subprocess.run([flyway, '-target=18', 'migrate'], env=env, cwd=ROOT, check=True)
            subprocess.run([flyway, '-target=18', 'validate'], env=env, cwd=ROOT, check=True)
            with psycopg.connect(**{**settings, 'dbname': database}) as c:
                assert snapshot(c, columns) == previous, 'Cadastros ou movimentações foram alterados'
                current_permissions = set(c.execute('SELECT id,code,description FROM bar.permissions').fetchall())
                assert permissions < current_permissions and len(current_permissions-permissions) == 1
                assert next(iter(current_permissions-permissions))[1] == 'table.create'
                current_grants = set(c.execute('SELECT id,establishment_id,role_id,permission_id FROM bar.role_permissions').fetchall())
                assert grants < current_grants and len(current_grants-grants) == 3
                granted = set(c.execute("SELECT rp.establishment_id,rp.role_id FROM bar.role_permissions rp JOIN bar.permissions p ON p.id=rp.permission_id WHERE p.code='table.create'").fetchall())
                assert granted == {(unit, manager), (unit, attendant), (other, other_manager)}
                assert c.execute("SELECT version,checksum FROM bar.flyway_schema_history WHERE version IS NOT NULL AND version<>'18' ORDER BY installed_rank").fetchall() == checksums
            print(f'V18 aprovada: {len(previous)} tabelas preservadas, permissão e vínculos anteriores intactos.')
            print('Cadastro liberado para gestão/atendimento; observador não recebeu acesso. Checksums V1–V17 preservados.')
        finally:
            if not database.startswith('bar_app_test_') or len(database) != len('bar_app_test_') + 32:
                raise RuntimeError('Nome inválido para remoção do banco de teste.')
            admin.execute(sql.SQL('DROP DATABASE {} WITH (FORCE)').format(sql.Identifier(database)))


if __name__ == '__main__':
    main()
