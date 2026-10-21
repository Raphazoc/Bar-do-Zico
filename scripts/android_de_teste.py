"""Abre um Android virtual visível com o APK de treinamento, sem apagar dados."""
import hashlib
import json
import os
from pathlib import Path
import ssl
import subprocess
import sys
import time
import urllib.request

from cadastrar_admin import ROOT

TOOLS = ROOT / '.tools'
ADB = TOOLS / 'android-sdk/platform-tools/adb.exe'
EMULATOR = TOOLS / 'android-sdk/emulator/emulator.exe'
AVD = 'BarZicoTest'
SERIAL = 'emulator-5556'
PACKAGE = 'br.com.bardozico.android.treinamento'
HIDDEN = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
PERFORMANCE_PROFILE = {'gpu': 'swiftshader', 'memoryMB': 4096, 'cores': 4, 'screen': '720x1560@320', 'quickBoot': False}


def adb(*arguments, timeout=15, check=True):
    return subprocess.run([str(ADB), '-s', SERIAL, *arguments], cwd=ROOT,
                          text=True, capture_output=True, timeout=timeout, check=check,
                          creationflags=HIDDEN)


def training_ready():
    try:
        runtime = json.loads((TOOLS/'training-runtime.json').read_text(encoding='utf-8'))
        jar = ROOT/'apps/api/target/bar-api-0.1.0.jar'
        if runtime.get('url') != 'https://127.0.0.1:18443':
            return False
        if runtime.get('buildHash') != hashlib.sha256(jar.read_bytes()).hexdigest()[:16]:
            return False
        context = ssl.create_default_context(cafile=str(TOOLS/'tls/bar-ca.cer'))
        with urllib.request.urlopen(runtime['url']+'/api/health', context=context, timeout=3) as response:
            state = json.load(response)
        return state.get('application') == 'bar-do-zico' and state.get('mode') == 'TREINAMENTO'
    except (OSError, ValueError):
        return False


def main():
    for file in (ADB, EMULATOR, TOOLS/'android-avd/BarZicoTest.ini', ROOT/'output/android/Bar-do-Zico-Treinamento.apk'):
        if not file.is_file():
            raise RuntimeError('Arquivo necessário não encontrado: '+str(file))
    print('ANDROID DE TESTE — dados separados da operação do bar.', flush=True)
    if not training_ready():
        print('Iniciando o serviço de treinamento. Aguarde...', flush=True)
        subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                        str(ROOT/'scripts/iniciar-bar.ps1'), '-Treinamento', '-SemJanela'],
                       cwd=ROOT, check=True)
        if not training_ready():
            raise RuntimeError('O serviço de treinamento não respondeu. Consulte .tools/api.log.')

    subprocess.run([str(ADB), 'start-server'], cwd=ROOT, check=True, creationflags=HIDDEN,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    devices = subprocess.run([str(ADB), 'devices'], capture_output=True, text=True,
                             check=True, creationflags=HIDDEN).stdout
    process = None
    if SERIAL in devices:
        current = adb('emu', 'avd', 'name').stdout.splitlines()
        if AVD not in current:
            raise RuntimeError('Outro Android usa a porta de teste. Feche esse emulador e tente novamente.')
        print('Usando o Android de teste já aberto.', flush=True)
    else:
        env = {**os.environ, 'ANDROID_AVD_HOME': str(TOOLS/'android-avd'),
               'ANDROID_HOME': str(TOOLS/'android-sdk'), 'ANDROID_SDK_ROOT': str(TOOLS/'android-sdk')}
        print('Abrindo o Android no modo gráfico compatível, com 4 GB de RAM e 4 núcleos.', flush=True)
        print('A inicialização pode demorar até três minutos. Os dados anteriores são mantidos.', flush=True)
        # A janela do emulador fica visível para o usuário; apenas o console auxiliar é ocultado.
        with (TOOLS/'android-teste.log').open('ab') as log:
            process = subprocess.Popen([str(EMULATOR), '-avd', AVD, '-port', '5556',
                                        '-no-audio', '-no-metrics', '-crash-report-mode', 'never',
                                        '-no-snapshot', '-gpu', 'swiftshader', '-memory', '4096', '-cores', '4'], cwd=ROOT, env=env,
                                       stdout=log, stderr=subprocess.STDOUT, creationflags=HIDDEN)
        (TOOLS/'android-teste-runtime.json').write_text(json.dumps({'pid': process.pid, 'serial': SERIAL, 'avd': AVD}), encoding='utf-8')

    deadline = time.monotonic()+180
    while time.monotonic() < deadline:
        if process is not None and process.poll() is not None:
            raise RuntimeError('O Android não iniciou. Consulte .tools/android-teste.log.')
        try:
            if adb('shell', 'getprop', 'sys.boot_completed', timeout=5, check=False).stdout.strip() == '1':
                break
        except subprocess.TimeoutExpired:
            pass
        time.sleep(2)
    else:
        raise RuntimeError('O Android ainda não terminou de iniciar. Consulte .tools/android-teste.log.')

    if process is not None:
        (TOOLS/'android-teste-profile.json').write_text(json.dumps(PERFORMANCE_PROFILE), encoding='utf-8')
    for animation in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
        adb('shell', 'settings', 'put', 'global', animation, '0.5')
    adb('shell', 'settings', 'put', 'system', 'screen_off_timeout', '1800000')
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'input', 'keyevent', '82')
    print('Instalando/atualizando o APK de treinamento, preservando os dados...', flush=True)
    installed = adb('install', '-r', str(ROOT/'output/android/Bar-do-Zico-Treinamento.apk'), timeout=90)
    if 'Success' not in installed.stdout:
        raise RuntimeError('O Android não confirmou a instalação do APK de treinamento.')
    adb('shell', 'am', 'start', '-n', PACKAGE+'/br.com.bardozico.android.MainActivity')
    print('Pronto! Use o mouse para tocar na tela e o teclado para preencher os campos.', flush=True)
    print('Mesas → Adicionar mesa cadastra; Abrir mesa começa o atendimento.', flush=True)
    print('Para abrir novamente, use o atalho Bar do Zico - Android de Teste.', flush=True)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.SubprocessError, OSError) as failure:
        print('Não foi possível abrir o Android de teste: '+str(failure), file=sys.stderr)
        sys.exit(1)
