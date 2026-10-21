"""Instala a distribuição ZIP oficial da Azure CLI somente em .tools."""
from pathlib import Path
import subprocess
from preparar_android import download, extract
from cadastrar_admin import ROOT

VERSION='2.91.0'


def main():
    target=ROOT/'.tools'/('azure-cli-'+VERSION)
    command=target/'bin/az.cmd'
    if not command.is_file():
        archive=ROOT/'.tools/downloads'/('azure-cli-'+VERSION+'-x64.zip')
        download('https://azcliprod.blob.core.windows.net/zip/'+archive.name,archive)
        extract(archive,target)
    if not command.is_file(): raise RuntimeError('A distribuição não contém bin/az.cmd.')
    subprocess.run([str(command),'version','--output','json'],check=True)
    (ROOT/'.tools/azure-cli-path.txt').write_text(str(command),encoding='utf-8')
    print('Azure CLI disponível no projeto, sem alterar o PATH ou configurações globais do Windows.')


if __name__=='__main__': main()
