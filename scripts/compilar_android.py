"""Gera dois APKs assinados com chave local persistente, sem publicar em lojas."""
import os
import ipaddress
import hashlib
import re
from pathlib import Path
import secrets
import shutil
import subprocess
from urllib.parse import urlsplit
from preparar_android import ROOT,TOOLS,environment


def validate_address(value):
    try:
        uri=urlsplit(value)
        host=uri.hostname or ''
        try: ipaddress.ip_address(host);valid_host=True
        except ValueError: valid_host=bool(re.fullmatch(r'[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*',host,re.IGNORECASE))
        return (len(value)<=200 and uri.scheme=='https' and valid_host
                and uri.username is None and uri.password is None
                and (uri.port is None or 1<=uri.port<=65535)
                and uri.path in ('','/') and not uri.query and not uri.fragment
                and '?' not in value and '#' not in value
                and uri.hostname!='bar-api.example.azurecontainerapps.io')
    except ValueError:
        return False


def main():
    env=environment();directory=TOOLS/'android-signing';directory.mkdir(parents=True,exist_ok=True)
    for variable in ('BAR_ANDROID_URL','BAR_ANDROID_TRAINING_URL'):
        if variable in env and not validate_address(env[variable]):
            raise RuntimeError(variable+': use uma origem HTTPS válida e real, sem senha, caminho ou parâmetros.')
    password_file=directory/'password.txt';key=directory/'bar-do-zico.p12'
    if key.exists() and not password_file.exists():raise RuntimeError('Preserve a chave existente: arquivo da senha ausente.')
    if not password_file.exists():
        from cadastrar_admin import write_private_credential
        write_private_credential(password_file,secrets.token_urlsafe(32))
    env['BAR_ANDROID_KEY_PASSWORD']=password_file.read_text().strip()
    if not key.exists():
        subprocess.run([str(Path(env['JAVA_HOME'])/'bin/keytool.exe'),'-genkeypair','-keystore',str(key),'-storetype','PKCS12','-alias','bardozico','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Bar do Zico, OU=Aplicativo local, O=Bar do Zico, C=BR','-storepass:env','BAR_ANDROID_KEY_PASSWORD','-keypass:env','BAR_ANDROID_KEY_PASSWORD'],env=env,check=True,capture_output=True)
    env['BAR_ANDROID_KEYSTORE']=str(key)
    certificate=ROOT/'.tools/tls/bar-ca.cer'
    if not certificate.exists(): raise RuntimeError('Prepare o certificado HTTPS local antes de compilar o Android.')
    raw=ROOT/'apps/android/app/src/main/res/raw';raw.mkdir(parents=True,exist_ok=True)
    shutil.copy2(certificate,raw/'bar_ca.cer')
    subprocess.run([str(TOOLS/'gradle-8.13/bin/gradle.bat'),'--no-daemon','--console=plain','assembleRelease'],cwd=ROOT/'apps/android',env=env,check=True)
    out=ROOT/'output/android';out.mkdir(parents=True,exist_ok=True)
    for flavor,name in [('operacao','Bar-do-Zico.apk'),('treinamento','Bar-do-Zico-Treinamento.apk')]:
        source=ROOT/f'apps/android/app/build/outputs/apk/{flavor}/release/app-{flavor}-release.apk'
        subprocess.run([str(TOOLS/'android-sdk/build-tools/35.0.0/apksigner.bat'),'verify','--verbose',str(source)],env=env,check=True)
        shutil.copy2(source,out/name);print('APK pronto:',out/name)
    # O manifesto precisa corresponder aos APKs recém-gerados, nunca à entrega anterior.
    sums=''.join(hashlib.sha256((out/name).read_bytes()).hexdigest()+'  '+name+'\n'
                 for name in ('Bar-do-Zico.apk','Bar-do-Zico-Treinamento.apk'))
    (out/'SHA256SUMS.txt').write_text(sums,encoding='utf-8')
    print('Chave de atualização preservada em .tools/android-signing. Não publicar essa pasta.')

if __name__=='__main__':main()
