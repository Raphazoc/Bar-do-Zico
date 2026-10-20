"""Prepara SDK e Gradle locais sem alterar o Java ou as variáveis do Windows."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[1]
TOOLS=ROOT/'.tools'
SDK=TOOLS/'android-sdk'

def download(url,path,checksum=None,algorithm='sha256'):
    path.parent.mkdir(parents=True,exist_ok=True)
    if not path.exists():
        print('Baixando',path.name,flush=True)
        partial=path.with_suffix(path.suffix+'.partial')
        with urllib.request.urlopen(url,timeout=90) as source,partial.open('wb') as target:
            shutil.copyfileobj(source,target)
        partial.replace(path)
    if checksum and hashlib.new(algorithm,path.read_bytes()).hexdigest()!=checksum.lower():
        raise RuntimeError('Checksum inválido: '+path.name)

def extract(archive,target):
    target.mkdir(parents=True,exist_ok=True)
    resolved=target.resolve()
    with zipfile.ZipFile(archive) as z:
        for member in z.infolist():
            if not (resolved/member.filename).resolve().is_relative_to(resolved):
                raise RuntimeError('Caminho inválido no pacote.')
        z.extractall(target)

def environment():
    marker=TOOLS/'android-java-home.txt'
    home=Path(marker.read_text().strip()) if marker.exists() else Path(shutil.which('java')).resolve().parent.parent
    release=(home/'release').read_text(encoding='utf-8')
    if 'JAVA_VERSION="21' not in release and 'JAVA_VERSION="17' not in release:
        raise RuntimeError('A compilação Android precisa de JDK 17 ou 21. Informe .tools/android-java-home.txt.')
    marker.write_text(str(home),encoding='utf-8')
    env=os.environ.copy()
    env.update(JAVA_HOME=str(home),ANDROID_HOME=str(SDK),ANDROID_SDK_ROOT=str(SDK),GRADLE_USER_HOME=str(TOOLS/'gradle-cache'))
    env['PATH']=str(home/'bin')+os.pathsep+env.get('PATH','')
    return env

def main():
    env=environment()
    manager=SDK/'cmdline-tools/19.0/bin/sdkmanager.bat'
    if not manager.exists():
        print('Consultando catálogo oficial do Android SDK...',flush=True)
        with urllib.request.urlopen('https://dl.google.com/android/repository/repository2-1.xml',timeout=60) as response:
            tree=ET.fromstring(response.read())
        package=next(p for p in tree if p.tag.endswith('remotePackage') and p.attrib.get('path')=='cmdline-tools;19.0')
        archives=package.find('archives')
        archive=next(a for a in archives if a.findtext('host-os')=='windows')
        complete=archive.find('complete');filename=complete.findtext('url')
        checksum=complete.find('checksum')
        path=TOOLS/'downloads'/filename
        download('https://dl.google.com/android/repository/'+filename,path,checksum.text,checksum.attrib.get('type','sha1'))
        stage=TOOLS/'android-command-tools'
        extract(path,stage)
        (SDK/'cmdline-tools').mkdir(parents=True,exist_ok=True)
        shutil.copytree(stage/'cmdline-tools',SDK/'cmdline-tools/19.0',dirs_exist_ok=True)
    # Aceite das licenças padrão necessário para preparar o SDK autorizado.
    subprocess.run([str(manager),'--sdk_root='+str(SDK),'--licenses'],input='y\n'*100,text=True,env=env,check=True)
    subprocess.run([str(manager),'--sdk_root='+str(SDK),'platform-tools','platforms;android-35','build-tools;35.0.0'],env=env,check=True)
    if not (TOOLS/'gradle-8.13/bin/gradle.bat').exists():
        url='https://services.gradle.org/distributions/gradle-8.13-bin.zip'
        with urllib.request.urlopen(url+'.sha256',timeout=60) as response: checksum=response.read().decode().strip()
        archive=TOOLS/'downloads/gradle-8.13-bin.zip'
        download(url,archive,checksum)
        extract(archive,TOOLS)
    project=ROOT/'apps/android';project.mkdir(parents=True,exist_ok=True)
    (project/'local.properties').write_text('sdk.dir='+SDK.as_posix().replace(':','\\:')+'\n',encoding='utf-8')
    print('Ferramentas Android prontas no projeto.',flush=True)

if __name__=='__main__':main()
