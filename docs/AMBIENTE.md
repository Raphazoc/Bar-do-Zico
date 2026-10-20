# Ambiente de desenvolvimento no Windows

Preparado em 19/10/2026 e ampliado para API Java, cliente JavaFX e telas React.
Existem `pom.xml` na raiz e nos módulos Java e `apps/web/package.json` com lockfile.
Para abrir o sistema, consulte [COMO-USAR.md](COMO-USAR.md). Os comandos de banco
abaixo continuam disponíveis para desenvolvimento e diagnóstico.

O piloto também dispõe de banco de treinamento persistente `bar_training_<uuid>`,
provisionado por `scripts/preparar_treinamento.py`. Configuração e acesso ficam
privados em `.tools/training/`; o serviço usa `18081` ou `18443` em HTTPS.
`Verificar Rede.cmd` faz apenas leitura das condições locais. Os celulares
informados pelo proprietário são Android; consulte [ANDROID.md](ANDROID.md).
O Wi-Fi residencial permanece com perfil Público no Windows. O utilitário
**Configurar Rede do Bar.cmd** preserva esse perfil e solicita apenas regras
locais para o Java; a confirmação administrativa ainda deve ser conferida.

A tela **Preparação** reúne cadastros, acesso Android e estado das cópias.
O serviço de operação inicia um trabalhador de backup horário, com arquivos
e manifestos em `.tools/backups/`. Cada cópia é restaurada em banco temporário
e conferida antes do sucesso. **Configurar Backups.cmd** escolhe a segunda
pasta; consulte [BACKUP-E-RECUPERACAO.md](BACKUP-E-RECUPERACAO.md).

## Ferramentas disponíveis

| Ferramenta | Versão | Local |
|---|---|---|
| Python | 3.13.15 | Instalação já existente do usuário |
| Bibliotecas Python | psycopg/psycopg-binary 3.3.6 e tzdata 2026.5 | `.venv/` |
| Bootstrap do administrador | argon2-cffi 25.1.0 | `.venv/`, definido em `requirements-dev.txt` |
| Eclipse Temurin JDK | 25.0.4.1+1 | `%LOCALAPPDATA%/Programs/BarDev/jdk-25.0.4.1+1` |
| JDK global preservado | 21.0.12.1+1 | `JAVA_HOME` e PATH do usuário, sem alteração global nesta entrega |
| Apache Maven | 3.9.16 | `%LOCALAPPDATA%/Programs/BarDev/apache-maven-3.9.16` |
| Spring Boot / JavaFX | 3.5.16 / 25.0.1 | Dependências Maven dos módulos API/desktop |
| Node.js | 24.22.0 | `.tools/node-v24.22.0-win-x64/`, instalação local com checksum oficial |
| Interface web | React 19.3.1, TypeScript 7.0.2, Vite 8.3.4 | `apps/web/`, dependências com lockfile |
| Testes de interface | Playwright 1.64.1 | `apps/web/e2e/`, navegadores em `.tools/ms-playwright/` |
| PostgreSQL | 17.11 | `.tools/pgsql/` |
| Flyway Community CLI | 13.10.3 | `.tools/flyway-13.10.3/` |
| Docker Desktop | 4.95.0, instalado e em execução | `%LOCALAPPDATA%/Programs/DockerDesktop` |
| Docker Engine/CLI | 29.8.2 | Backend WSL 2 e ferramentas do Desktop |
| Compose integrado | 5.6.0 | Docker Desktop |
| WSL | 3.0.3.0, versão padrão 2 | Recurso do Windows habilitado pelo proprietário |
| Compose auxiliar | 5.6.0, apenas validação offline | `.tools/compose/` |

O projeto usa **Java 25** e **JavaFX 25.0.1**, conforme os POMs. O Temurin
25.0.4.1+1 já existente nesta máquina foi reutilizado; não foi necessário baixar
ou instalar outro JDK. Seu caminho absoluto está em `.tools/java-home.txt`,
arquivo local ignorado pelo versionamento. Em outra máquina, esse marcador deve
apontar para a instalação de JDK 25 disponível nela; uma pasta `.tools/jdk-25/`
também é reconhecida quando não há marcador.

`scripts/ambiente.ps1` valida o JDK e seleciona Java 25 somente na sessão atual.
Os scripts Python usam o mesmo marcador para `java`, `keytool` e `jpackage`.
O utilitário de configuração do firewall prepara regras para esse mesmo
executável, preservando as restrições de interface, portas e sub-rede local.
Essa seleção não aplica regras nem muda o perfil Público do Windows.

O `JAVA_HOME` e o PATH do usuário continuam apontando para o Java 21 anterior.
Maven permanece disponível pela configuração já existente. Para carregar as
ferramentas do projeto no terminal atual, incluindo Python virtual,
PostgreSQL e Flyway:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
. .\scripts\ambiente.ps1
java -version
mvn -version
python -m pip check
```

A alteração de política acima vale apenas para o processo atual. Alternativamente,
os utilitários abaixo podem ser chamados em processo separado, como nos exemplos.
Os arquivos em `.venv/` e `.tools/` são locais e ignorados pelo versionamento.

Foram conferidos `java -version` e `jpackage --version` como 25.0.4.1 nos
utilitários do projeto. A compilação integrada com Java 25 foi concluída.
Foram aprovados 17 testes de tela em cada modo e 13 testes da API no treinamento;
o executável renderizou o login por HTTPS com JavaFX 25.0.1. As capturas foram
conferidas no computador e no Android emulado. O teste em aparelho físico está pendente.

## Usar o banco local agora

O cluster preparado fica em `.tools/pgdata`, restrito a `127.0.0.1:55432`, com
database `gestao_bar` e schema `bar`. A credencial de migração local é lida de
arquivo ignorado pelo Git, sem aparecer nos comandos ou na saída.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/banco-local.ps1 -Acao Iniciar
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/banco-local.ps1 -Acao Migrar
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/banco-local.ps1 -Acao Status
```

Para executar os testes, sem alterar o database de desenvolvimento:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/banco-local.ps1 -Acao Testar
```

O comando inicia o PostgreSQL se necessário, cria um database descartável para
os testes e o exclui ao terminar. Se iniciou o servidor para esse teste, também
o encerra depois. Para parar um servidor iniciado manualmente:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/banco-local.ps1 -Acao Parar
```

Este ambiente nativo foi validado na entrega inicial com dez migrações. O
container recebeu também V11 para sessões da aplicação e é usado pela API.
Os dois clusters não compartilham dados; migre o nativo antes de usá-lo com a API.

## Docker operacional

Após o proprietário habilitar WSL e reiniciar o Windows, foram confirmados WSL
3.0.3.0 com versão padrão 2 e `VirtualMachinePlatform` ativo. Docker Desktop
4.95.0 foi instalado no perfil do usuário e iniciado com sucesso. As imagens
`postgres:17.11` e `redgate/flyway:13.10.3` já foram baixadas.

O serviço `gestao-bar-db-1` está saudável, com database `gestao_bar`, schema `bar`
e porta publicada somente em `127.0.0.1:5432`. As 14 migrações e a validação de
checksums foram executadas pelo Flyway do container.

Para iniciar novamente e conferir as migrações:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/pos-reinicio.ps1
```

O utilitário preserva o `.env`, inicia Docker Desktop quando necessário, aguarda
o engine, sobe o banco e executa `migrate`/`validate`. O instalador também foi
ajustado para preservar uma instalação existente e conferir o resultado real
quando o bootstrapper retorna código diferente de zero.

No terminal com o ambiente carregado, confira:

```powershell
docker version
docker compose version
```

O `.env` já foi gerado com senha local aleatória, sem exibição no terminal e
ignorado pelo Git. A configuração e a execução dos containers foram conferidas.
O Compose auxiliar continua isolado em `.tools/` apenas para validação offline;
a execução usa o Compose integrado ao Docker Desktop.

Para repetir os 31 testes de integridade e concorrência contra o container:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validar-docker.ps1
```

O teste usa databases descartáveis e preserva o database `gestao_bar`. A conexão
é lida do Compose em memória, sem imprimir credenciais. O usuário de execução
`bar_app` já foi criado, com senha em `.tools/docker-app-password.txt`, ignorado
pelo Git; foram confirmados acesso permitido aos dados e negação de DDL e acesso
ao histórico do Flyway. A primeira unidade e seu administrador já foram
cadastrados por solicitação do proprietário. A API já foi implementada e usa
`bar_app`; o cadastro inicial foi feito por `scripts/cadastrar_admin.py`.

A API executa em `http://127.0.0.1:8080` no modo computador e
`https://IP-LOCAL:8443` no perfil de rede. O programa JavaFX é gerado em
`output/app/Bar do Zico/Bar do Zico.exe`, com runtime incluído. A interface de
celular é web, hospedada pela API. O modo de rede gera certificados privados em
`.tools/tls/`; somente `bar-ca.cer` é público. A confiança nos aparelhos e o firewall
não são alterados automaticamente. Não houve validação em aparelho físico.

Para repetir somente a conferência offline, preservando as credenciais:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/pos-reinicio.ps1 -SomenteValidar
```

Referências oficiais: [instalação do Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/),
[instalação do WSL](https://learn.microsoft.com/en-us/windows/wsl/install),
[comandos do WSL](https://learn.microsoft.com/en-us/windows/wsl/basic-commands),
[instalação do Temurin por arquivo](https://adoptium.net/installation/archives),
[Apache Maven](https://maven.apache.org/download.cgi).


## Novo front e Android — 20/10/2026

V13/V14 aplicadas na operação e no treinamento, com preservação das colunas e
registros anteriores. São 37 tabelas incluindo Flyway e 87 chaves estrangeiras.
A operação também está em HTTPS local na porta 8443; o treinamento usa 18443.
Os atalhos preservam esse modo ao abrir a janela no computador.

SDK Android, Gradle 8.13 e emulador de API 35 preparados em `.tools`. O Android
usa o JDK 21 existente somente no processo de build. Computador/API e seus
empacotadores continuam com Java 25. A distribuição Gradle tem checksum fixado.
APKs assinados em `output/android`; CA pública embutida, chave privada de
assinatura preservada em `.tools/android-signing`.

O aplicativo Windows abre diretamente em Mesas. O APK release foi instalado
no emulador Android, com conexão HTTPS à API local. A instalação em aparelho
físico e o teste no Wi-Fi real do bar permanecem no piloto acompanhado.
