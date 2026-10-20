"""Confere criação idempotente do treinamento e preservação dos cadastros reais."""
from pathlib import Path
import hashlib
import json
import psycopg
from cadastrar_admin import ROOT, connection_settings
from preparar_treinamento import prepare, validate_config


def real_snapshot(settings):
    with psycopg.connect(**settings) as c:
        return (
            c.execute('SELECT id,name FROM bar.establishments ORDER BY id').fetchall(),
            c.execute('SELECT id,email,password_hash,active FROM bar.users ORDER BY id').fetchall(),
            tuple(c.execute('SELECT count(*) FROM bar.'+name).fetchone()[0] for name in
                  ('areas','bar_tables','categories','products','accounts','orders','payments','cash_sessions')),
        )


def main():
    settings=connection_settings();before=real_snapshot(settings)
    config=prepare()
    credentials=ROOT/'.tools/training/acesso.txt'
    original=hashlib.sha256(credentials.read_bytes()).digest()
    with psycopg.connect(**{**settings,'dbname':config['database']}) as c:
        first=c.execute('SELECT id,password_hash FROM bar.users ORDER BY id').fetchall()
        counts=tuple(c.execute('SELECT count(*) FROM bar.'+name).fetchone()[0] for name in ('bar_tables','products'))
    assert prepare()==config
    with psycopg.connect(**{**settings,'dbname':config['database']}) as c:
        assert first==c.execute('SELECT id,password_hash FROM bar.users ORDER BY id').fetchall()
        assert counts==tuple(c.execute('SELECT count(*) FROM bar.'+name).fetchone()[0] for name in ('bar_tables','products'))
    assert original==hashlib.sha256(credentials.read_bytes()).digest()
    for database in (settings['dbname'],'bar_training_gestao_bar','bar_training_'+32*'a'+';DROP DATABASE x'):
        try:validate_config({**config,'database':database},settings['dbname'])
        except RuntimeError:pass
        else:raise AssertionError('Configuração inválida foi aceita.')
    assert before==real_snapshot(settings)
    print('Piloto validado: banco separado, cadastros e senhas reais preservados, preparo repetível sem duplicação.')


if __name__=='__main__':main()
