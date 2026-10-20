"""Diagnóstico somente de leitura para preparar o acesso Android."""
import hashlib
import json
import socket
import ssl
import subprocess
import urllib.request
from cadastrar_admin import ROOT


def read_network():
    command="Get-NetConnectionProfile -ErrorAction Stop | Select-Object InterfaceAlias,NetworkCategory | ConvertTo-Json -Compress"
    result=subprocess.run(['powershell','-NoProfile','-Command',command],capture_output=True,text=True)
    if result.returncode: return []
    profiles=json.loads(result.stdout or '[]')
    return profiles if isinstance(profiles,list) else [profiles]


def main():
    print('VERIFICAÇÃO DA REDE — nenhum ajuste será feito no Windows.')
    profiles=read_network()
    for profile in profiles:
        category={0:'Pública',1:'Privada',2:'Domínio'}.get(profile['NetworkCategory'],str(profile['NetworkCategory']))
        print('Rede:',profile['InterfaceAlias'],'— perfil',category)
    if any(p['NetworkCategory']==0 for p in profiles):
        print('O perfil Público pode ser mantido no Wi-Fi residencial confirmado pelo proprietário.')
        print('Configurar Rede do Bar.cmd libera somente o Java na sub-rede local e na interface conferida.')
    rules=subprocess.run(['powershell','-NoProfile','-Command',
        "@(Get-NetFirewallRule -Name 'BarDoZico-Local-8443','BarDoZico-Local-18443' -ErrorAction SilentlyContinue | Where-Object Enabled -eq True).Count"],
        capture_output=True,text=True)
    if rules.returncode==0 and rules.stdout.strip()=='2':
        print('As duas regras do Bar do Zico estão habilitadas; o teste pelo celular ainda é necessário.')
    else:
        print('Liberação do firewall pendente ou incompleta. Execute Configurar Rede do Bar.cmd e aceite a solicitação do Windows.')
    for name,label,port in [('api-runtime.json','Operação',8443),('training-runtime.json','Treinamento',18443)]:
        path=ROOT/'.tools'/name
        if not path.exists(): print(label+': serviço ainda não preparado.');continue
        info=json.loads(path.read_text(encoding='utf-8'))
        url=info['url'];context=None
        if url.startswith('https:'):
            context=ssl.create_default_context(cafile=str(ROOT/'.tools/tls/bar-ca.cer'))
        try:
            with urllib.request.urlopen(url+'/api/health',context=context,timeout=3) as response:
                health=json.load(response)
                if health.get('application')!='bar-do-zico': raise RuntimeError('Serviço diferente do esperado.')
            print(label+': respondeu localmente em '+url)
            if context:
                addresses=json.loads((ROOT/'.tools/tls/addresses.json').read_text())
                for ip in addresses:
                    if ip!='127.0.0.1': print('  Endereço candidato para Android: https://'+ip+':'+str(port))
            else: print('  Para Android, inicie este ambiente com a opção de rede (HTTPS).')
        except Exception: print(label+': não respondeu. Inicie o ambiente antes de testar no celular.')
    certificate=ROOT/'.tools/tls/bar-ca.cer'
    if certificate.exists():
        fingerprint=hashlib.sha256(ssl.PEM_cert_to_DER_cert(certificate.read_text())).hexdigest().upper()
        print('Certificado público para copiar ao Android:',ROOT/'output/android/bar-ca.cer')
        print('Impressão SHA-256:',':'.join(fingerprint[i:i+2] for i in range(0,len(fingerprint),2)))
    print('Abra docs/ANDROID.md para a configuração. Este teste local não confirma o acesso de outro aparelho.')


if __name__=='__main__':main()
