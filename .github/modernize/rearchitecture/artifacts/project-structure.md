# Estrutura do projeto

## Classificação

Projeto **full-stack multiaplicativo**, com API backend, front-end web, cliente Android e shell desktop Windows.

## Módulos

- `apps/api`: aplicação Spring Boot que expõe a API HTTP/SSE, autentica usuários, aplica permissões, executa comandos transacionais e lê/escreve PostgreSQL via `JdbcTemplate`.
- `apps/web`: SPA React/Vite usada pelo administrador e pelo atendimento móvel; o mesmo front-end é carregado no navegador, no Android WebView e no shell desktop.
- `apps/android`: aplicativo Android nativo que hospeda o front-end em `WebView`, valida a origem HTTPS e fornece configurações de conexão.
- `apps/desktop`: aplicativo Windows que hospeda a operação administrativa e integra impressão nativa de guias.
- `apps/api/src/main/resources/db/migration`: 19 migrações Flyway que definem o esquema `bar`.
- `tests`, `apps/*/src/test`, `apps/web/e2e`: testes unitários, de integração e E2E.
- `infra`, `.azure`, `compose.yaml`, `Dockerfile`: execução local e recursos de infraestrutura/empacotamento.

## Camadas observadas

1. **Entrypoint/host**: `BarApplication`, `MainActivity`, `BarDesktop` e `main.tsx`.
2. **Apresentação/transportes**: controllers Spring, SSE `LocalEvents`, componentes React e WebView.
3. **Aplicação/domínio**: `Operations`, `Management`, `Identity` e regras de permissão/estado.
4. **Persistência**: `JdbcTemplate`, SQL embutido e migrações Flyway/PostgreSQL.
5. **Integrações locais**: backup por arquivos, certificado/QR, impressão nativa e outbox local.

## Áreas funcionais

- Identidade, sessões, perfis e permissões.
- Mesas, locais, autorizações e atendimentos.
- Cardápio, categorias, complementos e disponibilidade.
- Pedidos, produção e cancelamentos.
- Caixa, pagamentos, estornos, fechamentos e relatórios.
- Estoque e movimentações.
- Auditoria e eventos em tempo real.
- Configuração de acesso Android, certificado e backups.
- Impressão de guia da mesa.
