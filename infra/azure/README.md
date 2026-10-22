# Azure pilot

**Revisão de 20/10/2026:** consulte [REVISÃO DO PROJETO](../../docs/REVISAO-REDE-E-AZURE.md).
O proprietário confirmou Azure para acesso por 4G/outras redes. A imagem foi
compilada localmente, mas isso não significa que o servidor esteja publicado.
A Azure CLI foi autenticada nesta revisão: assinatura Enabled, zero grupos de
recursos e zero recursos. Nenhum servidor foi criado. O login da CLI é separado
da sessão do VS Code.

O aplicativo atual abre sem login somente no ambiente local. O contêiner público
mantém `BAR_OPEN_ACCESS=false`: ainda é necessário implementar autorização dos
aparelhos para usar a interface na nuvem sem uma tela de login diária. Não liberar
acesso anônimo na internet. Também não existe sincronização entre bancos local
e remoto; publicar o piloto vazio não sincroniza as vendas do computador.

This directory is reserved for the non-production Azure deployment. The pilot
must publish only the API container over HTTPS and keep PostgreSQL private.

## Container contract

Build from the repository root:

```powershell
docker build -t bar-api:pilot .
```

The image listens on port `8080` and requires these runtime settings:

| Setting | Purpose |
|---|---|
| `DB_URL` | Private PostgreSQL JDBC URL, including `currentSchema=bar` |
| `DB_USERNAME` | Restricted application database user |
| `DB_PASSWORD` | Application database password, stored as a secret |
| `BAR_ALLOWED_HOSTS` | Public hostname of the API, without the scheme |
| `BAR_ANDROID_URLS` | Public HTTPS URL used to generate the Android QR code |
| `BAR_UNIT_ID` | Unit identifier for the pilot data set |

The image defaults to `BAR_BIND=0.0.0.0`, `BAR_PORT=8080`,
`BAR_OPEN_ACCESS=false`, and `BAR_SECURE_COOKIE=true`. Do not enable open
access for a public deployment.

The final image runs as UID/GID `10001`, and uses
`SERVER_FORWARD_HEADERS_STRATEGY=framework` behind the trusted Container Apps
ingress so HTTPS origins and secure cookies work with TLS termination. Do not
expose the container's HTTP port directly outside that ingress.

## Pilot boundaries

- Use a separate Azure resource group and a separate PostgreSQL database.
- Do not import the production database until backups and restore have been
  tested.
- Keep PostgreSQL off the public Internet; allow only the API's private
  network path.
- Store all passwords and signing material in a managed secret store.
- Use the real HTTPS hostname provided by Container Apps, or a custom domain
  with a trusted certificate. A custom domain is optional.

The infrastructure definition and deployment commands will be added after the
Azure subscription, region, domain, and chosen hosting service are confirmed.

## Empty-database bootstrap

After applying all Flyway migrations, run `scripts/bootstrap_azure.py` once
against the pilot database with a privileged database URL. It requires:

```text
AZURE_ADMIN_DATABASE_URL
BAR_UNIT_ID (required; choose one UUID and reuse it)
BAR_UNIT_NAME
BAR_ADMIN_NAME
BAR_ADMIN_USERNAME
BAR_ADMIN_PASSWORD
```

The script creates the pilot establishment, administrator, administrator role,
and all currently defined permissions idempotently. It does not print or store
the administrator password. Create the restricted `bar_app` role separately
using `infra/postgres/runtime-role.sql`, then configure `DB_USERNAME` and
`DB_PASSWORD` as Azure secrets.

The chosen unit UUID must be used as `BAR_UNIT_ID` in the Container App.

Use the same UUID in bootstrap and API configuration. Repeating bootstrap keeps
the existing password and does not reactivate an inactive/deleted administrator.
Concurrent runs for the same unit are serialized in PostgreSQL. Flyway migrations
must run before bootstrap; the API image does not execute migrations itself.

Local checks (all databases created and dropped by these tools are disposable):

```powershell
.venv/Scripts/python.exe scripts/validar_nuvem.py
docker build -t bar-do-zico:revisao-20261020 .
```

To inspect the real subscription without creating resources, open
**ferramentas/Verificar Azure.cmd**. The tool uses the official ZIP Azure CLI in `.tools`,
requests Microsoft sign-in if necessary, and saves the inventory locally in
`.tools/azure-inventory.json`. It never prints access tokens, changes the selected
subscription, creates resources or publishes the image.
