"""Exporta o schema validado para DBML. Conexão por PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE."""
from pathlib import Path
import os

import psycopg
from psycopg.rows import dict_row

ROOT=Path(__file__).resolve().parents[1]


def quote(value):
    return "'"+value.replace('\\','\\\\').replace("'","\\'").replace('\n',' ') + "'"


def main():
    output=[
        '// Gerado do PostgreSQL por scripts/export_dbml.py. Não substitui as migrações.',
        '// Índices parciais têm o predicado em note; UNIQUE só vale quando o predicado é verdadeiro.',
        'Project gestao_bar {', "  database_type: 'PostgreSQL'",
        "  Note: 'MVP: 33 tabelas de negócio/referência e 2 de infraestrutura da API. Checks, triggers, views e condições de índices estão nas migrações SQL.'", '}', '',
    ]
    with psycopg.connect(dbname=os.environ.get('PGDATABASE','gestao_bar'),row_factory=dict_row) as c:
        tables=c.execute("SELECT oid,relname FROM pg_class WHERE relnamespace='bar'::regnamespace AND relkind='r' AND relname<>'flyway_schema_history' ORDER BY relname").fetchall()
        for t in tables:
            output.append(f"Table bar.{t['relname']} {{")
            columns=c.execute('''SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS type,
                a.attnotnull,a.attidentity,pg_get_expr(d.adbin,d.adrelid) AS default_value,
                EXISTS(SELECT 1 FROM pg_constraint k WHERE k.conrelid=a.attrelid AND k.contype='p' AND a.attnum=ANY(k.conkey)) AS pk
                FROM pg_attribute a LEFT JOIN pg_attrdef d ON (d.adrelid,d.adnum)=(a.attrelid,a.attnum)
                WHERE a.attrelid=%s AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum''',(t['oid'],)).fetchall()
            for col in columns:
                typ=col['type'].replace('character varying','varchar').replace('timestamp with time zone','timestamptz')
                attrs=[]
                if col['pk']:attrs.append('pk')
                if col['attnotnull']:attrs.append('not null')
                if col['attidentity']:attrs.append('increment')
                elif col['default_value']:attrs.append('default: `'+col['default_value']+'`')
                output.append(f"  {col['attname']} {typ}"+(' ['+', '.join(attrs)+']' if attrs else ''))
            indexes=c.execute('''SELECT ic.relname AS name,i.indisunique,pg_get_expr(i.indpred,i.indrelid) AS predicate,
                ARRAY(SELECT pg_get_indexdef(i.indexrelid,n,true) FROM generate_series(1,i.indnkeyatts) n) AS columns
                FROM pg_index i JOIN pg_class ic ON ic.oid=i.indexrelid
                WHERE i.indrelid=%s AND NOT i.indisprimary ORDER BY ic.relname''',(t['oid'],)).fetchall()
            if indexes:output.append('  indexes {')
            for idx in indexes:
                attrs=['name: '+quote(idx['name'])]
                if idx['indisunique'] and not idx['predicate']:attrs.append('unique')
                if idx['predicate']:attrs.append('note: '+quote(('UNIQUE parcial: ' if idx['indisunique'] else 'Parcial: ')+idx['predicate']))
                output.append('    ('+', '.join(idx['columns'])+') ['+', '.join(attrs)+']')
            if indexes:output.append('  }')
            output.extend(['}',''])
        refs=c.execute('''SELECT child.relname AS child,parent.relname AS parent,
            array_agg(ca.attname ORDER BY s.ord) AS child_cols,array_agg(pa.attname ORDER BY s.ord) AS parent_cols
            FROM pg_constraint fk JOIN pg_class child ON child.oid=fk.conrelid
            JOIN pg_class parent ON parent.oid=fk.confrelid
            CROSS JOIN LATERAL unnest(fk.conkey,fk.confkey) WITH ORDINALITY s(cnum,pnum,ord)
            JOIN pg_attribute ca ON ca.attrelid=child.oid AND ca.attnum=s.cnum
            JOIN pg_attribute pa ON pa.attrelid=parent.oid AND pa.attnum=s.pnum
            WHERE fk.contype='f' AND child.relnamespace='bar'::regnamespace
            GROUP BY fk.oid,child.relname,parent.relname ORDER BY child.relname,fk.oid''').fetchall()
        for ref in refs:
            child_cols=ref['child_cols'];parent_cols=ref['parent_cols']
            left=f"bar.{ref['child']}."+(child_cols[0] if len(child_cols)==1 else '('+', '.join(child_cols)+')')
            right=f"bar.{ref['parent']}."+(parent_cols[0] if len(parent_cols)==1 else '('+', '.join(parent_cols)+')')
            output.append(f'Ref: {left} > {right}')
    target=ROOT/'docs/banco/gestao_bar.dbml'
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text('\n'.join(output)+'\n',encoding='utf-8')
    print(f'DBML: {len(tables)} tabelas, {len(refs)} relacionamentos, {target}')


if __name__=='__main__':main()
