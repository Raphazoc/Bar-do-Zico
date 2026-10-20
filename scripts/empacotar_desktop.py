"""Gera imagem Windows com Java próprio. Preserva builds anteriores e dados locais."""
from pathlib import Path
import datetime
import shutil
import subprocess
from cadastrar_admin import ROOT
from java_tools import java_tool,java_environment


def main():
    jpackage=java_tool('jpackage')
    target=ROOT/'apps/desktop/target';stage=ROOT/'.tools/desktop-package-input'
    stage.mkdir(parents=True,exist_ok=True)
    # Diretório de preparação exclusivo, sem dados de usuário.
    for entry in stage.glob('*.jar'): entry.unlink()
    shutil.copy2(target/'bar-desktop-0.1.0.jar',stage)
    for jar in (target/'lib').glob('*.jar'): shutil.copy2(jar,stage)
    output=ROOT/'output/app';previous=output/'Bar do Zico'
    if previous.exists():
        resolved=previous.resolve();expected=(ROOT/'output/app/Bar do Zico').resolve()
        if resolved!=expected or resolved.parent!=(ROOT/'output/app').resolve(): raise RuntimeError('Destino de pacote inválido.')
        archive=ROOT/'.tools/previous-builds';archive.mkdir(exist_ok=True)
        previous.rename(archive/('Bar do Zico-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')))
    subprocess.run([jpackage,'--type','app-image','--name','Bar do Zico','--icon',str(ROOT/'assets/icons/bar-do-zico.ico'),'--input',str(stage),'--main-jar','bar-desktop-0.1.0.jar','--main-class','br.com.bardozico.desktop.Launcher','--dest',str(output),'--app-version','0.1.0','--vendor','Bar do Zico','--add-modules','java.desktop,java.logging,java.net.http,java.scripting,java.xml,jdk.jsobject,jdk.unsupported,jdk.crypto.ec'],check=True,cwd=ROOT,env=java_environment())
    print('Programa Windows:',output/'Bar do Zico/Bar do Zico.exe')


if __name__=='__main__': main()
