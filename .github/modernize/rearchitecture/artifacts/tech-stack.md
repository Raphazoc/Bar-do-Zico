# Stack tecnológico existente

## Backend

- Java 21 (configurado no `apps/api/pom.xml`), Maven e Spring Boot `3.5.16`.
- Spring Web MVC, Spring Security, JDBC e validação/transações Spring.
- PostgreSQL 17, Flyway e `JdbcTemplate`; o código não usa entidades JPA como camada principal.
- Jackson para JSON, Argon2 para senhas, ZXing para QR Code e geração própria de PDF.
- Servidor HTTP configurável por `BAR_BIND`/`BAR_PORT`; padrão observado: `127.0.0.1:8080`.

## Web

- TypeScript `7.0.2`, React/React DOM `19.3.1`, Vite `8.3.4`.
- Playwright `1.64.1` para E2E.
- SPA sem roteador declarado: a navegação é controlada por estado no componente `Front`.

## Android

- Gradle Android plugin `8.13.2`, compile/target SDK 35, min SDK 26.
- Java 17 para o módulo Android.
- Aplicativo nativo Java com `WebView`, flavors `operacao` e `treinamento`.

## Desktop e operação

- Módulo Maven `apps/desktop` para o shell Windows e ponte de impressão.
- `Dockerfile`, `compose.yaml`, scripts PowerShell/CMD e configuração Azure apoiam execução/empacotamento.

## Configuração e restrições observadas

- Variáveis de banco: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; isolamento `READ_COMMITTED` e timezone UTC no pool.
- API distingue banco de operação e treinamento no startup.
- Escritas exigem `X-Bar-Request: 1`, origem same-origin e `Idempotency-Key` para comandos.
- Android aceita somente origens HTTPS sem credenciais, caminho, query ou fragmento, com rejeição de conteúdo misto e certificados inválidos.
- Diretórios `node_modules`, `dist`, `.gradle`, `build`, `.venv` e artefatos compilados foram tratados como gerados, não como fonte.
