"""Consulta assinatura e recursos existentes, sem criar ou alterar recursos Azure."""
import argparse
import json
import re
import subprocess
from cadastrar_admin import ROOT


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--login',action='store_true',help='Permitir login interativo se ainda não houver sessão.')
    args=parser.parse_args()
    marker=ROOT/'.tools/azure-cli-path.txt'
    if not marker.exists():
        from preparar_azure_cli import main as prepare
        prepare()
    command=marker.read_text(encoding='utf-8').strip()

    def query(*arguments):
        result=subprocess.run([command,*arguments,'--output','json','--only-show-errors'],cwd=ROOT,capture_output=True,text=True,encoding='utf-8')
        if result.returncode: raise RuntimeError('A consulta Azure falhou. Confira autenticação e permissão de leitura da assinatura.')
        return json.loads(result.stdout)

    accounts=query('account','list','--all','--query','[].{id:id,name:name,state:state,tenantId:tenantId}')
    if not accounts and args.login:
        print('Entre com sua conta Microsoft/Azure no endereço e código mostrados abaixo. Nenhum recurso será criado.',flush=True)
        subprocess.run([command,'login','--use-device-code','--output','none'],cwd=ROOT,check=True)
        accounts=query('account','list','--all','--query','[].{id:id,name:name,state:state,tenantId:tenantId}')
    if not accounts:
        print('Azure CLI preparada, mas sem sessão autenticada. Abra ferramentas/Verificar Azure.cmd para entrar e consultar os recursos.')
        return
    plan=(ROOT/'.azure/deployment-plan.md').read_text(encoding='utf-8')
    match=re.search(r'Subscription ID:\s*`?([0-9a-f-]{36})`?',plan.replace('**',''),re.IGNORECASE)
    if not match: raise RuntimeError('Subscription ID não encontrado no plano Azure. Confira .azure/deployment-plan.md.')
    subscription=match.group(1)
    account=next((a for a in accounts if a['id'].lower()==subscription.lower()),None)
    if not account:
        print('A assinatura registrada no plano não está acessível nesta sessão. Não foi consultada outra assinatura automaticamente.')
        print('Assinaturas acessíveis:',', '.join(a['name']+' ('+a['id']+')' for a in accounts))
        return
    resources=query('resource','list','--subscription',subscription,'--query','[].{id:id,name:name,type:type,location:location,resourceGroup:resourceGroup}')
    groups=query('group','list','--subscription',subscription,'--query','[].{name:name,location:location}')
    report={'subscription':account,'resourceGroups':groups,'resources':resources}
    (ROOT/'.tools/azure-inventory.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Assinatura consultada:',account['name'],'—',account['state'])
    print('Grupos de recursos:',len(groups),'| Recursos:',len(resources))
    for item in resources[:30]: print(item['name'],'—',item['type'],'—',item['location'])
    print('Inventário salvo em .tools/azure-inventory.json. Nenhuma configuração Azure foi alterada.')


if __name__=='__main__': main()
