"""Prepara .env local sem sobrescrever configuração existente nem exibir segredos."""
from pathlib import Path
import secrets

ROOT = Path(__file__).resolve().parents[1]


def prepare():
    target = ROOT / '.env'
    if target.exists():
        print('.env existente preservado.')
        return
    template = (ROOT / '.env.example').read_text(encoding='utf-8')
    if 'POSTGRES_PASSWORD=\n' not in template:
        raise RuntimeError('Formato de .env.example inesperado; nenhuma configuração foi gravada.')
    content = template.replace('POSTGRES_PASSWORD=\n', 'POSTGRES_PASSWORD=' + secrets.token_urlsafe(32) + '\n', 1)
    # Modo exclusivo: se outro processo criar o arquivo, não sobrescrever.
    with target.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(content)
    print('.env criado com credencial local aleatória. O arquivo é ignorado pelo Git.')


if __name__ == '__main__':
    prepare()
