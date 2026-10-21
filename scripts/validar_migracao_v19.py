"""Confere V18 -> V19 em banco preenchido descartável; nunca altera operação."""
import subprocess
from uuid import uuid4
import psycopg
from psycopg import sql
from cadastrar_admin import ROOT, connection_settings
from java_tools import java_environment
from validar_migracao_v17 import snapshot


def main():
    settings=connection_settings();database='bar_app_test_'+uuid4().hex
    with psycopg.connect(**{**settings,'dbname':'postgres'},autocommit=True) as admin:
        admin.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(database)))
        try:
            env=java_environment();env.update(FLYWAY_URL=f"jdbc:postgresql://127.0.0.1:{settings['port']}/{database}",FLYWAY_USER=settings['user'],FLYWAY_PASSWORD=settings['password'],FLYWAY_SCHEMAS='bar',FLYWAY_DEFAULT_SCHEMA='bar',FLYWAY_CLEAN_DISABLED='true',FLYWAY_LOCATIONS='filesystem:'+(ROOT/'apps/api/src/main/resources/db/migration').as_posix())
            flyway=str(ROOT/'.tools/flyway-13.10.3/flyway.cmd')
            subprocess.run([flyway,'-target=18','migrate'],env=env,cwd=ROOT,check=True)
            unit,user,terminal,cash,closed_terminal,closed_cash,closure=[uuid4() for _ in range(7)]
            with psycopg.connect(**{**settings,'dbname':database}) as c:
                c.execute("INSERT INTO bar.establishments(id,name) VALUES(%s,'Migração preenchida V19')",(unit,))
                c.execute("INSERT INTO bar.users(id,establishment_id,name,password_hash) VALUES(%s,%s,'Responsável','hash-inutilizado-para-operacao-local')",(user,unit))
                for tid,name in [(terminal,'Caixa aberto'),(closed_terminal,'Caixa encerrado')]:c.execute('INSERT INTO bar.cash_terminals(id,establishment_id,name) VALUES(%s,%s,%s)',(tid,unit,name))
                for sid,tid,opening in [(cash,terminal,'123.45'),(closed_cash,closed_terminal,'10')]:c.execute('INSERT INTO bar.cash_sessions(id,establishment_id,terminal_id,operator_id,opening_amount) VALUES(%s,%s,%s,%s,%s)',(sid,unit,tid,user,opening))
                c.execute('INSERT INTO bar.cash_closures(id,establishment_id,cash_session_id,closed_by) VALUES(%s,%s,%s,%s)',(closure,unit,closed_cash,user))
                c.execute('INSERT INTO bar.cash_closure_lines(establishment_id,closure_id,method,expected_amount,counted_amount) SELECT establishment_id,%s,method,expected_amount,expected_amount FROM bar.cash_session_totals WHERE cash_session_id=%s',(closure,closed_cash))
                c.execute("UPDATE bar.cash_sessions SET status='FECHADA',closed_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=%s",(closed_cash,))
                tables=[r[0] for r in c.execute("SELECT tablename FROM pg_tables WHERE schemaname='bar' AND tablename<>'flyway_schema_history' ORDER BY tablename")]
                columns={name:[r[0] for r in c.execute("SELECT column_name FROM information_schema.columns WHERE table_schema='bar' AND table_name=%s ORDER BY ordinal_position",(name,))] for name in tables}
                previous=snapshot(c,columns);checksums=c.execute("SELECT version,checksum FROM bar.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank").fetchall()
            subprocess.run([flyway,'-target=19','migrate'],env=env,cwd=ROOT,check=True)
            subprocess.run([flyway,'-target=19','validate'],env=env,cwd=ROOT,check=True)
            with psycopg.connect(**{**settings,'dbname':database}) as c:
                assert snapshot(c,columns)==previous,'Dados anteriores foram alterados pela migração'
                assert c.execute('SELECT count(*) FROM bar.cash_sessions WHERE review_required').fetchone()[0]==0
                assert c.execute("SELECT version,checksum FROM bar.flyway_schema_history WHERE version IS NOT NULL AND version<>'19' ORDER BY installed_rank").fetchall()==checksums
                c.execute('UPDATE bar.cash_sessions SET review_required=true,version=version+1 WHERE id=%s',(cash,))
                for statement,params in [
                    ('UPDATE bar.cash_sessions SET review_required=false WHERE id=%s',(cash,)),
                    ('UPDATE bar.cash_sessions SET opening_amount=999 WHERE id=%s',(cash,)),
                    ('UPDATE bar.cash_sessions SET review_required=true WHERE id=%s',(closed_cash,)),
                    ('INSERT INTO bar.cash_sessions(establishment_id,terminal_id,operator_id,opening_amount) VALUES(%s,%s,%s,0)',(unit,closed_terminal,user)),
                    ("INSERT INTO bar.cash_movements(establishment_id,cash_session_id,operator_id,authorized_by,kind,amount,reason) VALUES(%s,%s,%s,%s,'SUPRIMENTO',1,'Não permitido')",(unit,cash,user,user))]:
                    try:
                        with c.transaction():c.execute(statement,params)
                    except psycopg.errors.CheckViolation:pass
                    else:raise AssertionError('O banco aceitou alteração proibida')
                c.rollback()
            print(f'V19 aprovada: {len(previous)} tabelas preservadas, abertura 123.45 e fechamento anterior intactos. Checksums V1-V18 preservados.')
        finally:
            if not database.startswith('bar_app_test_') or len(database)!=len('bar_app_test_')+32:raise RuntimeError('Destino inválido')
            admin.execute(sql.SQL('DROP DATABASE {} WITH (FORCE)').format(sql.Identifier(database)))


if __name__=='__main__':main()
