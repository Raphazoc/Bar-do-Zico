"""Diagnóstico somente de leitura para preparar o acesso Android."""
import hashlib
import json
import socket
import ssl
import subprocess
import urllib.request
from cadastrar_admin import ROOT


def read_network():
    command="Get-NetConnectionProfile -ErrorAction Stop | Select-Object InterfaceAlias,InterfaceIndex,NetworkCategory | ConvertTo-Json -Compress"
    result=subprocess.run(['powershell','-NoProfile','-Command',command],capture_output=True,text=True)
    if result.returncode: return []
    profiles=json.loads(result.stdout or '[]')
    return profiles if isinstance(profiles,list) else [profiles]


def main():
    print('VERIFICAÇÃO DA REDE — nenhum ajuste será feito no Windows.')
    profiles=read_network()
    for profile in profiles:
        category={0:'Pública',1:'Privada',2:'Domínio'}.get(profile['NetworkCategory'],str(profile['NetworkCategory']))
        print('Rede:',profile['InterfaceAlias'],'— interface',profile['InterfaceIndex'],'— perfil',category)
    if any(p['NetworkCategory']==0 for p in profiles):
        print('O perfil Público pode ser mantido no Wi-Fi residencial confirmado pelo proprietário.')
        print('ferramentas/Configurar Rede do Bar.cmd libera somente o Java na sub-rede local e na interface conferida.')
    rules=subprocess.run(['powershell','-NoProfile','-Command',
        "@(Get-NetFirewallRule -Name 'BarDoZico-Local-8443','BarDoZico-Local-18443' -ErrorAction SilentlyContinue | Where-Object Enabled -eq True).Count"],
        capture_output=True,text=True)
    if rules.returncode==0 and rules.stdout.strip()=='2':
        print('As duas regras do Bar do Zico estão habilitadas; o teste pelo celular ainda é necessário.')
    else:
        print('Liberação do firewall pendente ou incompleta. Execute ferramentas/Configurar Rede do Bar.cmd e aceite a solicitação do Windows.')
    for name,label,port in [('api-runtime.json','Operação',8443),('training-runtime.json','Treinamento',18443)]:
        path=ROOT/'.tools'/name
        if not path.exists(): print(label+': serviço ainda não preparado.');continue
        try:
            info=json.loads(path.read_text(encoding='utf-8'))
            url=info['url'];context=None
            if url.startswith('https:'):
                context=ssl.create_default_context(cafile=str(ROOT/'.tools/tls/bar-ca.cer'))
            with urllib.request.urlopen(url+'/api/health',context=context,timeout=3) as response:
                health=json.load(response)
                expected='TREINAMENTO' if name=='training-runtime.json' else 'OPERACAO'
                if health.get('application')!='bar-do-zico' or health.get('mode')!=expected: raise RuntimeError('Serviço diferente do esperado.')
            print(label+': respondeu localmente em '+url)
            if context:
                # Os endereços do serviço ativo evitam anunciar um certificado novo antes de reiniciar a API.
                with urllib.request.urlopen(url+'/api/setup',context=context,timeout=3) as response:
                    addresses=json.load(response).get('addresses',[])
                for address in addresses: print('  Endereço para Android: '+address)
            else: print('  Para Android, inicie este ambiente com a opção de rede (HTTPS).')
        except Exception: print(label+': não respondeu. Inicie o ambiente antes de testar no celular.')
    certificate=ROOT/'.tools/tls/bar-ca.cer'
    if certificate.exists():
        fingerprint=hashlib.sha256(ssl.PEM_cert_to_DER_cert(certificate.read_text())).hexdigest().upper()
        print('Certificado público para copiar ao Android:',ROOT/'output/android/bar-ca.cer')
        print('Impressão SHA-256:',':'.join(fingerprint[i:i+2] for i in range(0,len(fingerprint),2)))
    print('Abra docs/ANDROID.md para a configuração. Este teste local não confirma o acesso de outro aparelho.')
    print('4G/outra rede exige o servidor Azure publicado; o IP residencial não funciona fora do roteador.')


if __name__=='__main__':main()
