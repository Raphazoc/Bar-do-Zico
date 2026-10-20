"""Seleciona o JDK 25 do projeto sem alterar as configurações do Windows."""
import os
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]


def java_home():
    marker = ROOT / '.tools/java-home.txt'
    local = ROOT / '.tools/jdk-25'
    if marker.exists():
        value = marker.read_text(encoding='utf-8-sig').strip()
        if not value:
            raise RuntimeError('O marcador .tools/java-home.txt está vazio.')
        home = Path(value)
        if not home.is_absolute():
            home = ROOT / home
    elif (local / 'bin/java.exe').is_file():
        home = local
    elif os.environ.get('JAVA_HOME'):
        home = Path(os.environ['JAVA_HOME'])
    else:
        executable = shutil.which('java')
        if not executable:
            raise RuntimeError('JDK 25 não encontrado. Informe sua pasta em .tools/java-home.txt.')
        home = Path(executable).resolve().parent.parent
    home = home.resolve()
    release = home / 'release'
    if not release.is_file() or not re.search(r'^JAVA_VERSION="25[."]', release.read_text(encoding='utf-8'), re.MULTILINE):
        raise RuntimeError('Este projeto usa JDK 25. Informe sua pasta em .tools/java-home.txt; o Java do Windows será preservado.')
    for tool in ('java', 'javac', 'jpackage'):
        if not (home / 'bin' / (tool + ('.exe' if os.name == 'nt' else ''))).is_file():
            raise RuntimeError(f'JDK incompleto: {tool} não encontrado em {home}.')
    return home


def java_tool(name):
    if name not in ('java', 'javac', 'jpackage', 'keytool'):
        raise ValueError('Ferramenta Java não reconhecida.')
    executable = java_home() / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))
    if not executable.is_file():
        raise RuntimeError(f'Ferramenta Java ausente: {name}.')
    return str(executable)


def java_environment():
    home = java_home()
    environment = os.environ.copy()
    environment['JAVA_HOME'] = str(home)
    environment['PATH'] = str(home / 'bin') + os.pathsep + environment.get('PATH', '')
    return environment
