"""Instala Node local fixado, conferindo SHA-256 oficial, sem alterar o Windows."""
from pathlib import Path
import hashlib
import urllib.request
import zipfile
from cadastrar_admin import ROOT

VERSION='v24.22.0'


def main():
    directory=ROOT/'.tools'/f'node-{VERSION}-win-x64'
    if not (directory/'node.exe').exists():
        name=f'node-{VERSION}-win-x64.zip';archive=ROOT/'.tools'/name
        archive.parent.mkdir(exist_ok=True)
        urllib.request.urlretrieve(f'https://nodejs.org/dist/{VERSION}/{name}',archive)
        sums=urllib.request.urlopen(f'https://nodejs.org/dist/{VERSION}/SHASUMS256.txt').read().decode()
        expected=next(line.split()[0] for line in sums.splitlines() if line.split()[-1]==name)
        if hashlib.sha256(archive.read_bytes()).hexdigest()!=expected: raise RuntimeError('Checksum do Node inválido.')
        with zipfile.ZipFile(archive) as package:
            for entry in package.namelist():
                destination=(archive.parent/entry).resolve()
                if not destination.is_relative_to(archive.parent.resolve()): raise RuntimeError('Caminho de pacote inválido.')
            package.extractall(archive.parent)
    (ROOT/'.tools/node-path.txt').write_text(str(directory),encoding='utf-8')
    print('Node local preparado:',VERSION)


if __name__=='__main__':main()
