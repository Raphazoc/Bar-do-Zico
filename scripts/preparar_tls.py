"""Gera CA privada e certificado local, sem alterar a confiança do Windows/celulares."""
from pathlib import Path
import ipaddress
import json
import os
import secrets
import shutil
import socket
import subprocess
import ssl
import time
from cadastrar_admin import ROOT,write_private_credential
from java_tools import java_tool


def prepare():
    directory=ROOT/'.tools/tls'
    directory.mkdir(parents=True,exist_ok=True)
    password_file=directory/'password.txt'
    if not password_file.exists(): write_private_credential(password_file,secrets.token_urlsafe(32))
    password=password_file.read_text(encoding='utf-8').strip()
    addresses=sorted({a[4][0] for a in socket.getaddrinfo(socket.gethostname(),None,socket.AF_INET) if ipaddress.ip_address(a[4][0]).is_private and not ipaddress.ip_address(a[4][0]).is_loopback})
    names=['127.0.0.1']+addresses
    metadata=directory/'addresses.json'
    keytool=java_tool('keytool')
    env={**os.environ,'BAR_TLS_PASSWORD':password}
    def run(*args):
        subprocess.run([keytool,*args],env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
    # ACL herdada restringida antes de gerar as chaves privadas.
    if os.name=='nt':
        import csv
        sid=next(csv.reader([subprocess.run(['whoami','/user','/fo','csv','/nh'],capture_output=True,text=True,check=True).stdout.strip()]))[1]
        subprocess.run(['icacls',str(directory),'/inheritance:r','/grant:r',f'*{sid}:(OI)(CI)(F)','*S-1-5-18:(OI)(CI)(F)'],capture_output=True,check=True)
    ca=directory/'ca.p12';server=directory/'bar.p12';certificate=directory/'bar-ca.cer'
    if not ca.exists():
        run('-genkeypair','-alias','ca','-keyalg','RSA','-keysize','3072','-dname','CN=Bar do Zico Local CA','-ext','bc:c=ca:true','-ext','KU=keyCertSign,cRLSign','-validity','3650','-keystore',str(ca),'-storetype','PKCS12','-storepass:env','BAR_TLS_PASSWORD')
        run('-exportcert','-alias','ca','-keystore',str(ca),'-storepass:env','BAR_TLS_PASSWORD','-rfc','-file',str(certificate))
    trust=directory/'trust.p12'
    if not trust.exists():
        # Somente certificado público; senha conhecida não protege chaves privadas.
        run('-importcert','-alias','ca','-keystore',str(trust),'-storetype','PKCS12','-storepass','changeit','-file',str(certificate),'-noprompt')
    signed=directory/'bar-signed.cer'
    renew=True
    if signed.exists():
        try:
            expires=ssl.cert_time_to_seconds(ssl._ssl._test_decode_cert(str(signed))['notAfter'])
            renew=expires<time.time()+30*86400
        except (ValueError,ssl.SSLError): pass
    if not server.exists() or not metadata.exists() or json.loads(metadata.read_text())!=names or renew:
        # Substituir somente o certificado gerado desta instalação; manter a CA confiada.
        if server.exists(): server.unlink()
        run('-genkeypair','-alias','bar','-keyalg','RSA','-keysize','3072','-dname','CN=Bar do Zico','-validity','365','-keystore',str(server),'-storetype','PKCS12','-storepass:env','BAR_TLS_PASSWORD')
        csr=directory/'bar.csr';signed=directory/'bar-signed.cer'
        run('-certreq','-alias','bar','-keystore',str(server),'-storepass:env','BAR_TLS_PASSWORD','-file',str(csr))
        run('-gencert','-alias','ca','-keystore',str(ca),'-storepass:env','BAR_TLS_PASSWORD','-infile',str(csr),'-outfile',str(signed),'-rfc','-validity','365','-ext','KU=digitalSignature,keyEncipherment','-ext','EKU=serverAuth','-ext','SAN='+','.join(['dns:localhost']+['ip:'+ip for ip in names]))
        run('-importcert','-alias','ca','-keystore',str(server),'-storepass:env','BAR_TLS_PASSWORD','-file',str(certificate),'-noprompt')
        run('-importcert','-alias','bar','-keystore',str(server),'-storepass:env','BAR_TLS_PASSWORD','-file',str(signed),'-noprompt')
        metadata.write_text(json.dumps(names),encoding='utf-8')
    print('Certificado público para confiar nos aparelhos:',certificate)
    public_directory=ROOT/'output/android'
    public_directory.mkdir(parents=True,exist_ok=True)
    shutil.copy2(certificate,public_directory/'bar-ca.cer')
    print('Endereços IP locais:',', '.join(addresses))
    # Priorizar a rede com gateway para não oferecer primeiro um adaptador virtual do Docker/WSL.
    try:
        result=subprocess.run(['powershell','-NoProfile','-Command',
            'Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway } | ForEach-Object { $_.IPv4Address.IPAddress } | ConvertTo-Json -Compress'],
            capture_output=True,text=True,timeout=10,check=True)
        preferred=json.loads(result.stdout or '[]')
        if isinstance(preferred,str):preferred=[preferred]
        addresses=[ip for ip in preferred if ip in addresses]+[ip for ip in addresses if ip not in preferred]
    except (ValueError,subprocess.SubprocessError):pass
    return server,password,addresses


if __name__=='__main__': prepare()
