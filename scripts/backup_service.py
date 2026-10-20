"""Backups verificados por snapshot, sem substituir dados de operação."""
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
from uuid import uuid4

import psycopg
from psycopg import sql, IsolationLevel
from cadastrar_admin import ROOT, connection_settings, write_private_credential

DIRECTORY = ROOT / '.tools/backups'
DEFAULT_CONFIG = {'enabled': True, 'intervalMinutes': 60, 'secondaryDirectory': None}


def now(): return datetime.now(timezone.utc).isoformat()


def atomic_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary=path.with_name(path.name+'.'+uuid4().hex+'.tmp')
    try:
        temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')
        os.replace(temporary,path)
    finally:
        if temporary.exists(): temporary.unlink()


def read_json(path, fallback=None):
    try: return json.loads(path.read_text(encoding='utf-8-sig'))
    except (FileNotFoundError,ValueError): return fallback


def prepare_directory():
    DIRECTORY.mkdir(parents=True,exist_ok=True)
    if os.name=='nt':
        import csv
        sid=next(csv.reader([subprocess.run(['whoami','/user','/fo','csv','/nh'],capture_output=True,text=True,check=True).stdout.strip()]))[1]
        subprocess.run(['icacls',str(DIRECTORY),'/inheritance:r','/grant:r',f'*{sid}:(OI)(CI)(F)','*S-1-5-18:(OI)(CI)(F)'],capture_output=True,check=True)
    else: DIRECTORY.chmod(0o700)


def configuration():
    path=DIRECTORY/'config.json'
    if not path.exists():
        prepare_directory()
        try: write_private_credential(path,json.dumps(DEFAULT_CONFIG,indent=2))
        except FileExistsError: pass
    value=read_json(path)
    if not isinstance(value,dict) or type(value.get('enabled')) is not bool:
        raise RuntimeError('Configuração de backup inválida.')
    interval=value.get('intervalMinutes')
    if type(interval) is not int or not 15<=interval<=1440:
        raise RuntimeError('Intervalo de backup deve estar entre 15 e 1440 minutos.')
    secondary=value.get('secondaryDirectory')
    if secondary is not None and (not isinstance(secondary,str) or not Path(secondary).is_absolute()):
        raise RuntimeError('O segundo destino precisa ser um caminho absoluto escolhido pelo proprietário.')
    return value


@contextmanager
def lock(name='backup.lock',wait_seconds=0):
    DIRECTORY.mkdir(parents=True,exist_ok=True)
    with (DIRECTORY/name).open('a+b') as stream:
        # Ler o byte já travado por outro processo falha no Windows; consultar tamanho não bloqueia.
        if os.fstat(stream.fileno()).st_size==0:stream.write(b'0');stream.flush()
        stream.seek(0)
        if os.name=='nt':
            import msvcrt
            until=time.monotonic()+wait_seconds
            while True:
                try:msvcrt.locking(stream.fileno(),msvcrt.LK_NBLCK,1);break
                except OSError:
                    if time.monotonic()>=until:raise RuntimeError('Outro processo de backup já está em execução.')
                    time.sleep(0.25)
            try:yield
            finally:stream.seek(0);msvcrt.locking(stream.fileno(),msvcrt.LK_UNLCK,1)
        else:
            import fcntl
            until=time.monotonic()+wait_seconds
            while True:
                try:fcntl.flock(stream,fcntl.LOCK_EX|fcntl.LOCK_NB);break
                except OSError:
                    if time.monotonic()>=until:raise RuntimeError('Outro processo de backup já está em execução.')
                    time.sleep(0.25)
            try:yield
            finally:fcntl.flock(stream,fcntl.LOCK_UN)


def docker_command():
    executable=shutil.which('docker')
    if not executable: executable=str(Path(os.environ['LOCALAPPDATA'])/'Programs/DockerDesktop/resources/bin/docker.exe')
    return [executable,'compose','exec','-T','db']


def checksum(path):
    digest=hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b''):digest.update(chunk)
    return digest.hexdigest()


def table_counts(connection):
    names=connection.execute("SELECT tablename FROM pg_tables WHERE schemaname='bar' ORDER BY tablename").fetchall()
    return {name:connection.execute(sql.SQL('SELECT count(*) FROM bar.{}').format(sql.Identifier(name))).fetchone()[0] for (name,) in names}


def restore_check(target,settings,cmd=None,expected=None):
    """Restaura somente em nome aleatório criado aqui; remove apenas esse banco."""
    database='bar_restore_'+uuid4().hex
    admin_settings={**settings,'dbname':'postgres'};created=False
    cmd=cmd or docker_command()
    try:
        with psycopg.connect(**admin_settings,autocommit=True) as connection:
            connection.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(database)))
        created=True
        with Path(target).open('rb') as stream:
            subprocess.run(cmd+['pg_restore','-U',settings['user'],'-d',database,'--no-owner','--no-privileges','--exit-on-error'],cwd=ROOT,stdin=stream,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
        with psycopg.connect(**{**settings,'dbname':database}) as connection:
            counts=table_counts(connection)
            if expected is not None and counts!=expected: raise RuntimeError('Contagens restauradas diferentes do snapshot do backup.')
            if len(counts)<34 or counts.get('establishments',0)<1 or counts.get('users',0)<1: raise RuntimeError('Estrutura/cadastros esperados ausentes na restauração.')
            connection.execute('SELECT * FROM bar.account_totals LIMIT 1').fetchall()
            connection.execute('SELECT * FROM bar.cash_session_totals LIMIT 1').fetchall()
        return {'tables':len(counts),'units':counts['establishments'],'users':counts['users'],'matchedSnapshot':expected is not None}
    finally:
        if created:
            if not database.startswith('bar_restore_') or len(database)!=44:raise RuntimeError('Nome de restauração inesperado.')
            with psycopg.connect(**admin_settings,autocommit=True) as connection:
                connection.execute(sql.SQL('DROP DATABASE {} WITH (FORCE)').format(sql.Identifier(database)))


def make_backup(verify=True):
    prepare_directory()
    with lock():
        status_path=DIRECTORY/'status.json';old=read_json(status_path,{})
        status={**old,'state':'RUNNING','lastAttemptAt':now(),'error':None}
        atomic_json(status_path,status)
        stamp=datetime.now().strftime('%Y%m%d-%H%M%S')
        target=DIRECTORY/f'gestao-bar-{stamp}-{uuid4().hex[:8]}.dump'
        partial=target.with_suffix('.dump.part')
        try:
            settings=connection_settings();cmd=docker_command()
            # Exportar e contar no mesmo snapshot usado por pg_dump, mesmo com atendimento concorrente.
            with psycopg.connect(**settings) as snapshot:
                snapshot.isolation_level=IsolationLevel.REPEATABLE_READ;snapshot.read_only=True
                snapshot_id=snapshot.execute('SELECT pg_export_snapshot()').fetchone()[0]
                counts=table_counts(snapshot)
                with partial.open('xb') as stream:
                    subprocess.run(cmd+['pg_dump','-U',settings['user'],'-d',settings['dbname'],'--format=custom','--no-owner','--no-privileges','--snapshot',snapshot_id],cwd=ROOT,stdout=stream,stderr=subprocess.PIPE,check=True)
            if partial.stat().st_size<100:raise RuntimeError('Backup vazio.')
            validation=restore_check(partial,settings,cmd,counts) if verify else None
            os.replace(partial,target)
            manifest={'version':1,'file':target.name,'createdAt':now(),'database':settings['dbname'],'sha256':checksum(target),'bytes':target.stat().st_size,'rowCounts':counts,'verified':verify,'validation':validation}
            manifest_path=target.with_suffix('.manifest.json');atomic_json(manifest_path,manifest)
            secondary_state='NOT_CONFIGURED'
            secondary=configuration().get('secondaryDirectory')
            if secondary:
                destination=Path(secondary)
                # Um disco removível desconectado não deve virar uma pasta local com nome semelhante.
                if not destination.is_dir(): secondary_state='UNAVAILABLE'
                elif destination.resolve()==DIRECTORY.resolve(): secondary_state='SAME_DIRECTORY'
                else:
                    try:
                        copied=destination/target.name
                        with target.open('rb') as source,copied.open('xb') as output:shutil.copyfileobj(source,output)
                        if checksum(copied)!=manifest['sha256']:raise RuntimeError('Checksum da segunda cópia divergente.')
                        shutil.copy2(manifest_path,destination/manifest_path.name)
                        secondary_state='COPIED'
                    except (OSError,RuntimeError):secondary_state='FAILED'
            status.update(state='OK',lastSuccessAt=manifest['createdAt'],file=target.name,bytes=manifest['bytes'],verified=verify,sha256=manifest['sha256'],secondaryState=secondary_state,error=None)
            atomic_json(status_path,status)
            print('Backup criado e '+('restaurado/conferido' if verify else 'salvo')+': '+str(target),flush=True)
            return target
        except Exception:
            status.update(state='ERROR',error='Não foi possível concluir o backup. Confira Docker, banco e espaço disponível.')
            atomic_json(status_path,status)
            raise


def verify_saved(target):
    target=Path(target).resolve()
    manifest=read_json(target.with_suffix('.manifest.json'))
    if not manifest or manifest.get('version')!=1 or manifest.get('file')!=target.name:
        raise RuntimeError('Escolha uma cópia criada pelo sistema, acompanhada de seu manifesto.')
    if checksum(target)!=manifest.get('sha256'):raise RuntimeError('O arquivo foi alterado ou está incompleto; restauração recusada.')
    with lock():return restore_check(target,connection_settings(),expected=manifest['rowCounts'])
