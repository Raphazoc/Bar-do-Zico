"""Inicia o serviço Java em segundo plano e abre o cliente do computador."""
import argparse
import json
import os
from pathlib import Path
import shutil
import ssl
import subprocess
import time
import urllib.request
import hashlib
from cadastrar_admin import ROOT,connection_settings
from java_tools import java_tool,java_environment


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sem-janela',action='store_true')
    parser.add_argument('--rede-local',action='store_true')
    parser.add_argument('--parar',action='store_true')
    parser.add_argument('--treinamento',action='store_true')
    args=parser.parse_args()
    tools=ROOT/'.tools';tools.mkdir(exist_ok=True)
    runtime=tools/('training-runtime.json' if args.treinamento else 'api-runtime.json')
    saved=json.loads(runtime.read_text()) if runtime.exists() else None
    if args.parar:
        if not args.treinamento:
            from backup_service import DIRECTORY
            if DIRECTORY.exists():(DIRECTORY/'stop').touch()
        if not saved: print('Nenhum serviço iniciado por este utilitário.');return
        pid=int(saved['pid'])
        # Conferir identidade antes de parar; não encerrar outro processo que reutilizou o PID.
        command=f"$p=Get-CimInstance Win32_Process -Filter 'ProcessId = {pid}'; if ($p -and $p.CommandLine.Contains('bar-api-0.1.0.jar')) {{ Stop-Process -Id {pid} -ErrorAction Stop }}"
        subprocess.run(['powershell','-NoProfile','-Command',command],check=True,capture_output=True)
        runtime.unlink();print('Serviço Java encerrado. Banco e dados preservados.');return
    settings=connection_settings()
    training=None
    if args.treinamento:
        from preparar_treinamento import prepare as prepare_training
        training=prepare_training()
        settings={**settings,'dbname':training['database']}
    java=java_tool('java')
    env=java_environment()
    env.pop('DEBUG',None)
    env.pop('SPRING_PROFILES_ACTIVE',None)
    env['BAR_TRAINING']='true' if args.treinamento else 'false'
    if training: env['BAR_UNIT_ID']=training['unit']
    env.update(DB_URL=f"jdbc:postgresql://127.0.0.1:{settings['port']}/{settings['dbname']}?currentSchema=bar",DB_USERNAME='bar_app',DB_PASSWORD=(tools/'docker-app-password.txt').read_text().strip())
    env['BAR_BACKUP_DIRECTORY']='' if args.treinamento else str(tools/'backups')
    env['BAR_PUBLIC_CERTIFICATE']=str(tools/'tls/bar-ca.cer')
    env['BAR_ANDROID_URLS']=''
    port=18081 if args.treinamento else 8080
    env['SERVER_PORT']=str(port)
    url=f'http://127.0.0.1:{port}';context=None
    if args.rede_local:
        from preparar_tls import prepare
        store,password,addresses=prepare()
        env.update(SPRING_PROFILES_ACTIVE='lan',BAR_TLS_KEYSTORE=store.as_uri(),BAR_TLS_PASSWORD=password)
        env['BAR_ALLOWED_HOSTS']=','.join(['127.0.0.1','localhost','::1']+addresses)
        port=18443 if args.treinamento else 8443
        env['SERVER_PORT']=str(port)
        env['BAR_ANDROID_URLS']=','.join(f'https://{ip}:{port}' for ip in addresses)
        url=f'https://127.0.0.1:{port}'
        context=ssl.create_default_context(cafile=str(tools/'tls/bar-ca.cer'))
    def healthy(target,ctx):
        try:
            with urllib.request.urlopen(target+'/api/health',context=ctx,timeout=3) as response:
                info=json.load(response)
                expected='TREINAMENTO' if args.treinamento else 'OPERACAO'
                return info.get('application')=='bar-do-zico' and info.get('mode', 'OPERACAO')==expected
        except Exception: return False
    if saved and saved.get('url')!=url:
        old_context=ssl.create_default_context(cafile=str(tools/'tls/bar-ca.cer')) if saved['url'].startswith('https') else None
        if healthy(saved['url'],old_context):
            stop_command='Parar Treinamento.cmd' if args.treinamento else 'Parar Bar.cmd'
            raise RuntimeError('Serviço está em outro modo. Abra '+stop_command+' antes de alternar entre computador e rede local.')
    if not healthy(url,context):
        jar=ROOT/'apps/api/target/bar-api-0.1.0.jar'
        if not jar.exists(): raise RuntimeError('Compile primeiro com scripts/compilar.ps1.')
        # Executar uma cópia evita bloquear o artefato de compilação no Windows.
        build_hash=hashlib.sha256(jar.read_bytes()).hexdigest()[:16]
        deployed=tools/'runtime-api'/build_hash/'bar-api-0.1.0.jar'
        deployed.parent.mkdir(parents=True,exist_ok=True)
        if not deployed.exists(): shutil.copy2(jar,deployed)
        log=tools/'api.log'
        with log.open('ab') as stream:
            process=subprocess.Popen([java,'-jar',str(deployed)],cwd=ROOT,env=env,stdout=stream,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
        runtime.write_text(json.dumps({'pid':process.pid,'url':url,'mode':'TREINAMENTO' if args.treinamento else 'OPERACAO'}),encoding='utf-8')
        for _ in range(45):
            if healthy(url,context): break
            if process.poll() is not None: raise RuntimeError(f'Serviço não iniciou. Consulte {log}.')
            time.sleep(1)
        else: raise RuntimeError(f'Serviço ainda não respondeu. Consulte {log}.')
    print('Serviço Java pronto:',url)
    if args.rede_local:print('Endereços para Android:',env['BAR_ANDROID_URLS'])
    if not args.treinamento:
        from backup_automatico import start_worker
        start_worker()
    if args.treinamento: print('TREINAMENTO: preços e dados fictícios; não registrar vendas reais.')
    if not args.sem_janela:
        executable=ROOT/'output/app/Bar do Zico/Bar do Zico.exe'
        if not executable.exists(): raise RuntimeError('Pacote do computador ausente. Execute scripts/compilar.ps1 -Empacotar.')
        desktop_env={k:v for k,v in os.environ.items() if k not in ('DB_PASSWORD','PGPASSWORD','BAR_APP_PASSWORD','BAR_TLS_PASSWORD')}
        desktop_env['BAR_DESKTOP_URL']=url
        desktop_env['BAR_PROJECT_DIR']=str(ROOT)
        # WebView confia apenas na CA local quando o modo de rede estiver ativo.
        if args.rede_local:
            # A truststore contém somente o certificado público da CA.
            desktop_env['JAVA_TOOL_OPTIONS']='-Djavax.net.ssl.trustStore='+str(tools/'tls/trust.p12')+' -Djavax.net.ssl.trustStorePassword=changeit'
        subprocess.Popen([str(executable)],cwd=ROOT,env=desktop_env,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
        print('Programa do computador aberto. Fechar a janela mantém o serviço ativo.')


if __name__=='__main__': main()
