"""Backup do banco real. A verificação restaura em banco temporário, nunca sobre a operação."""
import argparse
from backup_service import make_backup, restore_check, docker_command, verify_saved


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verificar-restauracao',action='store_true')
    parser.add_argument('--conferir-arquivo')
    args=parser.parse_args()
    if args.conferir_arquivo:
        result=verify_saved(args.conferir_arquivo)
        print('Restauração de conferência aprovada:',result['tables'],'tabelas. Banco original preservado.')
    else:
        # Todas as novas cópias são verificadas; a opção antiga continua compatível.
        make_backup(verify=True)
