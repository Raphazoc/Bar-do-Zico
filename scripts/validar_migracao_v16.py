"""Confere V15 -> V16 em banco preenchido descartável e preserva todos os dados anteriores."""
import hashlib
import json
import subprocess
from uuid import uuid4
import psycopg
from psycopg import sql
from cadastrar_admin import ROOT,connection_settings
from java_tools import java_environment


def snapshot(connection,columns):
    result={}
    for table,fields in columns.items():
        rows=connection.execute(sql.SQL('SELECT {} FROM bar.{}').format(sql.SQL(',').join(map(sql.Identifier,fields)),sql.Identifier(table))).fetchall()
        serialized=sorted(json.dumps(row,default=str,ensure_ascii=False) for row in rows)
        result[table]=(len(rows),hashlib.sha256('\n'.join(serialized).encode()).hexdigest())
    return result


def main():
    settings=connection_settings();database='bar_app_test_'+uuid4().hex
    with psycopg.connect(**{**settings,'dbname':'postgres'},autocommit=True) as admin:
        admin.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(database)))
        try:
            env=java_environment();env.update(FLYWAY_URL=f"jdbc:postgresql://127.0.0.1:{settings['port']}/{database}",FLYWAY_USER=settings['user'],FLYWAY_PASSWORD=settings['password'],FLYWAY_SCHEMAS='bar',FLYWAY_DEFAULT_SCHEMA='bar',FLYWAY_CLEAN_DISABLED='true',FLYWAY_LOCATIONS='filesystem:'+(ROOT/'apps/api/src/main/resources/db/migration').as_posix())
            flyway=str(ROOT/'.tools/flyway-13.10.3/flyway.cmd')
            subprocess.run([flyway,'-target=15','migrate'],env=env,cwd=ROOT,check=True)
            unit,owner,inactive,area,table,account=[uuid4() for _ in range(6)]
            with psycopg.connect(**{**settings,'dbname':database}) as c:
                c.execute("INSERT INTO bar.establishments(id,name) VALUES(%s,'Teste V16')",(unit,))
                for identifier,active in [(owner,True),(inactive,False)]:
                    c.execute("INSERT INTO bar.users(id,establishment_id,name,password_hash,active) VALUES(%s,%s,'Funcionário anterior','hash-sem-uso-no-teste-de-migracao',%s)",(identifier,unit,active))
                c.execute("INSERT INTO bar.areas(id,establishment_id,name) VALUES(%s,%s,'Salão')",(area,unit))
                c.execute("INSERT INTO bar.bar_tables(id,establishment_id,area_id,number) VALUES(%s,%s,%s,'Mesa 1')",(table,unit,area))
                c.execute("INSERT INTO bar.accounts(id,establishment_id,table_id,opened_by,status,closed_at) VALUES(%s,%s,%s,%s,'FECHADA',CURRENT_TIMESTAMP)",(account,unit,table,owner))
                tables=[r[0] for r in c.execute("SELECT tablename FROM pg_tables WHERE schemaname='bar' AND tablename<>'flyway_schema_history'")]
                columns={table:[r[0] for r in c.execute("SELECT column_name FROM information_schema.columns WHERE table_schema='bar' AND table_name=%s ORDER BY ordinal_position",(table,))] for table in tables}
                previous=snapshot(c,columns)
                checksums=c.execute("SELECT version,checksum FROM bar.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank").fetchall()
            subprocess.run([flyway,'-target=16','migrate'],env=env,cwd=ROOT,check=True)
            subprocess.run([flyway,'validate'],env=env,cwd=ROOT,check=True)
            with psycopg.connect(**{**settings,'dbname':database}) as c:
                assert snapshot(c,columns)==previous
                assert c.execute("SELECT version,checksum FROM bar.flyway_schema_history WHERE version IS NOT NULL AND version<>'16' ORDER BY installed_rank").fetchall()==checksums
                assert c.execute('SELECT count(*) FROM bar.users WHERE deleted_at IS NOT NULL').fetchone()[0]==0
                c.execute('UPDATE bar.users SET deleted_at=CURRENT_TIMESTAMP,active=false WHERE id=%s',(inactive,))
            try:
                with psycopg.connect(**{**settings,'dbname':database}) as c:
                    c.execute('UPDATE bar.users SET deleted_at=NULL,active=true WHERE id=%s',(inactive,))
            except psycopg.errors.CheckViolation:
                pass
            else:
                raise AssertionError('A restauração indevida não foi impedida')
            print('V16 aprovada: dados e checksums V1–V15 preservados; exclusão não restaura funcionário.')
        finally:
            admin.execute(sql.SQL('DROP DATABASE {} WITH (FORCE)').format(sql.Identifier(database)))


if __name__=='__main__':main()
