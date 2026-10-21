# Mapa arquitetural — índice de implementação

Este índice aponta os contratos extraídos do projeto. **This index is not the full contract. Do not implement from this file alone; follow the artifact paths below.**

## Implementation Guide

### Artefatos globais

- `unit_graph.yaml`: leia a unidade atribuída, seus gatilhos, `source_anchors`, assinaturas, dependências, entradas dinâmicas e `shared_refs`.
- `migration_boundary.yaml`: leitura obrigatória para escopo; este pedido não autoriza reescrita (`must_rewrite` está vazio). `source_anchors` não são alvos de reescrita.
- `wire_contracts.yaml`: filtre por `unit`; preserve os contratos REST/SSE/SKD e leia `semantic_divergence`.
- `shared_modules.yaml`: filtre `used_by_units`; use somente os campos listados em `shared_refs.used_fields`. O módulo `web-ui-primitives` é candidato a divisão, mas a decisão não está tomada.
- `cross_unit_state.yaml`: filtre fluxos cujo `writer.unit` ou `reader.unit` seja a unidade atribuída; fluxos não pareados exigem runtime, se existirem.
- `seams.yaml`: filtre `cut_between`; a ponte web/desktop é inferida e o lado desktop deve permanecer congelado.
- `project-structure.md`, `tech-stack.md`, `data-model.md`: visões globais para contexto estrutural, stack e entidades; não substituem os contratos YAML.

### Unidade: `api-identity`

- gatilho: endpoints de login, sessão, operador, senha e health.
- leia `units/api-identity/behavior.yaml` para efeitos e contratos de erro.
- leia `units/api-identity/bindings.yaml` para rotas e configuração `bar.*`.
- leia `units/api-identity/unit_decomposition.yaml` somente como candidatos (`commit: false`).
- filtre wire contracts por `unit: api-identity` e shared/state por `api-identity`.
- antes de DONE: relate artefatos lidos, efeitos preservados, contratos pendentes e evidência de validação.

### Unidade: `api-bar`

- gatilho: estado, conta, relatórios, auditoria e comandos de negócio.
- leia `units/api-bar/behavior.yaml`, `bindings.yaml` e `unit_decomposition.yaml`.
- filtre `wire_contracts.yaml` por `api-bar`, `shared_modules.yaml` por `used_by_units`, e `cross_unit_state.yaml` por escritor/leitor.
- preserve transação, idempotência, locks, permissões e modelo reduzido de atendimento.

### Unidade: `api-setup`

- gatilho: configuração, QR, certificado e solicitação de backup.
- leia os três arquivos em `units/api-setup/`; filtre contratos e shared refs por `api-setup`.
- preserve allowlist HTTPS, permissões, escrita atômica e distinção operação/treinamento.

### Unidade: `api-events`

- gatilho: conexão SSE `/api/events` e publicador agendado de outbox.
- leia `units/api-events/`; filtre `wire_contracts.yaml` e `cross_unit_state.yaml` por `api-events`.
- preserve lifecycle do emitter, escopo por unidade, retry e evento de invalidação sem dados de negócio.

### Unidade: `api-table-guide`

- gatilho: `GET /api/accounts/{id}/guide.pdf`.
- leia `units/api-table-guide/`; filtre wire/shared/state por `api-table-guide`.
- preserve autorização, snapshot `REPEATABLE_READ`, conteúdo do PDF e timezone.

### Unidade: `web-front`

- gatilho: raiz React carregada pelo navegador, Android e desktop.
- leia `units/web-front/`; filtre contratos por `web-front` e shared/state por `web-front`.
- preserve bootstrap de sessão, polling/SSE, lock, armazenamento de pending e restrição de atendimento.

### Unidade: `web-adjustments`

- gatilho: telas administrativas e operacionais importadas pela raiz.
- leia `units/web-adjustments/`; filtre shared/state por `web-adjustments`.
- preserve validações, mensagens, comandos e carregamento explícito de auditoria.

### Unidade: `web-table-guide`

- gatilho: ação de visualizar/imprimir guia.
- leia `units/web-table-guide/`; filtre contrato por `web-table-guide` e seam `web-table-guide`↔`desktop-shell`.
- preserve fallback browser/native, abort da requisição e revogação do object URL.

### Unidade: `android-attendance-shell`

- gatilho: ciclo de vida da `MainActivity` e carregamento da origem configurada.
- leia `units/android-attendance-shell/`; filtre contratos por `android-attendance-shell`.
- preserve HTTPS-only, same-origin, rejeição TLS/mixed-content, preferências e flavors.

### Unidade: `desktop-shell`

- gatilho: shell Windows e ponte de impressão.
- leia `units/desktop-shell/`; filtre shared/state e seam onde o lado desktop é congelado.
- preserve falhas explícitas da ponte e não altere o contrato nativo em mudanças web-only.

## Evidência obrigatória de conclusão

Cada implementação deve informar: `artifacts_read`, itens `must_preserve`/`must_appear_in_target` implementados, contratos não resolvidos/deferidos e evidência de testes, build ou runtime. As decomposições são apenas candidatos e não autorizam uma nova estrutura por si só.
