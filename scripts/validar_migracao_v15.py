"""Testa V14 -> V15 com histórico existente em banco descartável, sem tocar a operação."""
import subprocess
from uuid import uuid4
import psycopg
from psycopg import sql
from cadastrar_admin import ROOT, connection_settings
from java_tools import java_environment


def main():
    settings=connection_settings();database='bar_app_test_'+uuid4().hex
    with psycopg.connect(**{**settings,'dbname':'postgres'},autocommit=True) as admin:
        admin.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(database)))
        try:
            env=java_environment()
            env.update(FLYWAY_URL=f"jdbc:postgresql://127.0.0.1:{settings['port']}/{database}",
                       FLYWAY_USER=settings['user'],FLYWAY_PASSWORD=settings['password'],
                       FLYWAY_SCHEMAS='bar',FLYWAY_DEFAULT_SCHEMA='bar',FLYWAY_CLEAN_DISABLED='true',
                       FLYWAY_LOCATIONS='filesystem:'+(ROOT/'apps/api/src/main/resources/db/migration').as_posix())
            flyway=str(ROOT/'.tools/flyway-13.10.3/flyway.cmd')
            subprocess.run([flyway,'-target=14','migrate'],env=env,cwd=ROOT,check=True)
            unit,user,area,old,current,account,authorization,category,product=[uuid4() for _ in range(9)]
            with psycopg.connect(**{**settings,'dbname':database}) as c:
                c.execute("INSERT INTO bar.establishments(id,name) VALUES(%s,'Teste de atualização')",(unit,))
                c.execute("INSERT INTO bar.users(id,establishment_id,name,password_hash) VALUES(%s,%s,'Responsável','hash-inutilizado-para-operacao-local')",(user,unit))
                c.execute("INSERT INTO bar.areas(id,establishment_id,name) VALUES(%s,%s,'Salão')",(area,unit))
                for identifier,number in ((old,'Mesa 5'),(current,'Mesa 6')):
                    c.execute('INSERT INTO bar.bar_tables(id,establishment_id,area_id,number) VALUES(%s,%s,%s,%s)',(identifier,unit,area,number))
                c.execute("INSERT INTO bar.accounts(id,establishment_id,table_id,opened_by,status,closed_at) VALUES(%s,%s,%s,%s,'FECHADA',CURRENT_TIMESTAMP)",(account,unit,old,user))
                c.execute('INSERT INTO bar.table_authorizations(id,establishment_id,user_id,table_id,granted_by) VALUES(%s,%s,%s,%s,%s)',(authorization,unit,user,old,user))
                c.execute('UPDATE bar.bar_tables SET active=false WHERE id=%s',(old,))
                c.execute("INSERT INTO bar.categories(id,establishment_id,name) VALUES(%s,%s,'Bebidas')",(category,unit))
                c.execute("INSERT INTO bar.products(id,establishment_id,category_id,name,price,production_destination,stock_controlled) VALUES(%s,%s,%s,'Produto anterior',10,'BAR',true)",(product,unit,category))
                c.execute("INSERT INTO bar.stock_movements(establishment_id,product_id,actor_id,kind,quantity,balance_after,reason) VALUES(%s,%s,%s,'ENTRADA',10,0,'Saldo anterior')",(unit,product,user))
                previous=c.execute("SELECT version,checksum FROM bar.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank").fetchall()
            subprocess.run([flyway,'-target=15','migrate'],env=env,cwd=ROOT,check=True)
            subprocess.run([flyway,'validate'],env=env,cwd=ROOT,check=True)
            with psycopg.connect(**{**settings,'dbname':database}) as c:
                assert c.execute('SELECT count(*) FROM bar.bar_tables WHERE id=%s',(old,)).fetchone()[0]==0
                assert c.execute('SELECT number FROM bar.bar_tables WHERE id=%s',(current,)).fetchone()[0]=='Mesa 6'
                assert c.execute('SELECT table_id,table_number_snapshot,status FROM bar.accounts WHERE id=%s',(account,)).fetchone()==(None,'Mesa 5','FECHADA')
                row=c.execute('SELECT table_id,table_number_snapshot,revoked_at FROM bar.table_authorizations WHERE id=%s',(authorization,)).fetchone()
                assert row[:2]==(None,'Mesa 5') and row[2] is not None
                assert c.execute('SELECT stock_quantity,deleted_at FROM bar.products WHERE id=%s',(product,)).fetchone()==(10,None)
                assert c.execute('SELECT count(*) FROM bar.stock_movements WHERE product_id=%s',(product,)).fetchone()[0]==1
                assert c.execute("SELECT version,checksum FROM bar.flyway_schema_history WHERE version IS NOT NULL AND version<>'15' ORDER BY installed_rank").fetchall()==previous
                c.execute("INSERT INTO bar.bar_tables(establishment_id,area_id,number) VALUES(%s,%s,'Mesa 5')",(unit,area))
            print('Migração V14 → V15 aprovada com conta fechada, autorização, mesa arquivada e estoque existentes. V1–V14 preservadas.')
        finally:
            admin.execute(sql.SQL('DROP DATABASE {} WITH (FORCE)').format(sql.Identifier(database)))


if __name__=='__main__':main()
