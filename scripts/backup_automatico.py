"""Backup horário enquanto a instalação local estiver em uso. Sem tarefa administrativa."""
import argparse
from datetime import datetime, timezone
import os
from pathlib import Path
import subprocess
import sys
import time
from threading import Event,Thread

from backup_service import DIRECTORY, atomic_json, configuration, lock, make_backup, now, prepare_directory, read_json
from cadastrar_admin import ROOT


def due(status,config):
    if not config['enabled']:return False
    if not status.get('lastSuccessAt'):return True
    try:last=datetime.fromisoformat(status['lastSuccessAt'])
    except (ValueError,TypeError):return True
    if last.tzinfo is None:return True
    elapsed=(datetime.now(timezone.utc)-last).total_seconds()
    return elapsed< -300 or elapsed>=config['intervalMinutes']*60


def start_worker():
    config=configuration()
    if not config['enabled']:return
    (DIRECTORY/'stop').unlink(missing_ok=True)
    env=os.environ.copy();env['PYTHONIOENCODING']='utf-8'
    with (DIRECTORY/'automatic.log').open('ab') as log:
        subprocess.Popen([sys.executable,str(ROOT/'scripts/backup_automatico.py'),'--worker'],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)


def worker():
    prepare_directory()
    try:
        with lock('automatic.lock',wait_seconds=5):
            (DIRECTORY/'stop').unlink(missing_ok=True)
            heartbeat_stop=Event()
            def heartbeat():
                while not heartbeat_stop.is_set():
                    try:atomic_json(DIRECTORY/'worker.json',{'pid':os.getpid(),'heartbeatAt':now(),'enabled':configuration()['enabled']})
                    except Exception:pass
                    heartbeat_stop.wait(15)
            heartbeat_thread=Thread(target=heartbeat,daemon=True);heartbeat_thread.start()
            try:
                last_attempt=0
                while not (DIRECTORY/'stop').exists():
                    config=configuration();request=DIRECTORY/'request.json'
                    atomic_json(DIRECTORY/'worker.json',{'pid':os.getpid(),'heartbeatAt':now(),'enabled':config['enabled']})
                    if config['enabled'] and (request.exists() or due(read_json(DIRECTORY/'status.json',{}),config)) and time.monotonic()-last_attempt>=60:
                        request.unlink(missing_ok=True);last_attempt=time.monotonic()
                        try:make_backup(verify=True)
                        except Exception:print(now()+': backup não concluído; haverá nova tentativa. Confira o status.',flush=True)
                    for _ in range(6):
                        if (DIRECTORY/'stop').exists():break
                        time.sleep(5)
            finally:
                heartbeat_stop.set();heartbeat_thread.join(timeout=2)
                atomic_json(DIRECTORY/'worker.json',{'pid':os.getpid(),'heartbeatAt':now(),'enabled':False})
    except RuntimeError:
        # A trava permite reabrir o sistema sem multiplicar trabalhadores.
        return


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--worker',action='store_true')
    parser.add_argument('--parar',action='store_true')
    parser.add_argument('--destino')
    parser.add_argument('--intervalo',type=int)
    parser.add_argument('--desativar',action='store_true')
    parser.add_argument('--ativar',action='store_true')
    args=parser.parse_args()
    if args.worker:return worker()
    prepare_directory()
    if args.parar:(DIRECTORY/'stop').touch();print('O trabalhador encerrará após concluir a cópia em andamento.');return
    value=configuration()
    if args.intervalo is not None:
        if not 15<=args.intervalo<=1440:parser.error('Use de 15 a 1440 minutos.')
        value['intervalMinutes']=args.intervalo
    if args.destino:
        destination=Path(args.destino).resolve()
        if not destination.is_dir() or destination==DIRECTORY.resolve():parser.error('Escolha outra pasta existente para a segunda cópia.')
        value['secondaryDirectory']=str(destination)
    if args.desativar:value['enabled']=False
    if args.ativar:value['enabled']=True
    atomic_json(DIRECTORY/'config.json',value)
    if value['enabled']:start_worker()
    print('Backup automático '+('ativado' if value['enabled'] else 'desativado')+'. Intervalo: '+str(value['intervalMinutes'])+' minutos.')


if __name__=='__main__':main()
