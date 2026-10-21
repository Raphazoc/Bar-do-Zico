# Modelo de dados

## Persistência

O esquema PostgreSQL é `bar`, criado e evoluído pelas migrações Flyway em `apps/api/src/main/resources/db/migration`. As tabelas operacionais são segregadas por `establishment_id` e usam UUIDs compostos com chaves estrangeiras compostas em vários relacionamentos.

## Entidades principais

- **Establishment**: unidade do bar, timezone, taxa de serviço e configurações JSON.
- **User/Role/Permission**: identidade, perfis, permissões e tabelas de associação `user_roles`/`role_permissions`.
- **Areas/Bar tables**: locais físicos, mesas, estado e autorização por usuário.
- **Categories/Products/Modifier groups/Modifiers**: catálogo, preço, disponibilidade, estoque e complementos.
- **Accounts/Orders/Order items**: atendimento, pedidos, itens, destinos de produção e snapshots de preço/descrição.
- **Production events**: transições de produção, ator, autorização e disposição de estoque em cancelamentos.
- **Account adjustments**: descontos, serviço, acréscimos e reversões.
- **Payments/Payment reversals**: recebimentos por forma de pagamento e devoluções.
- **Cash terminals/Cash sessions/Cash closures**: terminais, abertura, movimentos, contagem e fechamento.
- **Stock movements**: ledger de estoque com saldo após movimentação.
- **App/device sessions, audit events, outbox events, idempotency records**: sessão, rastreabilidade, invalidação em tempo real e deduplicação.

## Relacionamentos essenciais

- Uma `establishments` possui usuários, perfis, áreas, mesas, catálogo, caixas, atendimentos e registros financeiros.
- `accounts` podem referenciar uma mesa e possuem `orders`; `orders` possuem `order_items`; itens possuem `order_item_modifiers` e eventos de produção.
- `products` pertencem a `categories`, podem ser ligados a grupos de complementos e geram `stock_movements`.
- `payments` pertencem a `accounts` e podem ter `payment_reversals`; sessões de caixa agrupam os pagamentos.
- `users` recebem `roles`, e roles recebem `permissions`; autorizações ligam usuário a mesa.

## Transações e integridade

- Comandos passam por `Operations.command`, que grava idempotência, executa a operação, registra evento/auditoria e salva a resposta no mesmo limite transacional.
- Há locks consultivos para gestão de mesas, abertura de caixa e gestão de equipe.
- A geração da guia usa `REPEATABLE_READ`.
- O schema aplica checks de status, valores não negativos, unicidade por estabelecimento, snapshots e integridade referencial.

## Campos/contratos sensíveis

- Estados de conta: `ABERTA`, `EM_FECHAMENTO`, `FECHADA`, `CANCELADA`.
- Estados de item: `ENVIADO`, `ACEITO`, `EM_PREPARO`, `PRONTO`, `ENTREGUE`, `CANCELADO`.
- Formas de pagamento: `DINHEIRO`, `PIX`, `DEBITO`, `CREDITO`, `VOUCHER`, `OUTRO`.
- Quantidades de estoque usam até três casas decimais; valores monetários usam duas.
