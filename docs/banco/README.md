# Banco — Bar do Zico

Referência: `C:/Users/User/Desktop/bar/Mapa_Banco_Gestao_Bar.pdf`, 8 páginas,
modelo relacional baseado no plano 2.0-Java, de 19/10/2026. O documento foi tratado
como referência de modelagem. O arquivo DBML citado no PDF não foi fornecido;
este projeto gera sua própria representação a partir do banco implementado.

## Escopo implementado

| Área | Tabelas |
|---|---|
| Identidade (6) | establishments, users, roles, permissions, user_roles, role_permissions |
| Salão e catálogo (8) | areas, bar_tables, table_authorizations, categories, products, modifier_groups, modifiers, product_modifier_groups |
| Contas e pedidos (6) | accounts, orders, order_items, order_item_modifiers, production_events, account_adjustments |
| Caixa e recebimentos (7) | cash_terminals, cash_sessions, payments, payment_reversals, cash_movements, cash_closures, cash_closure_lines |
| Estoque (1) | stock_movements |
| Sessões e entrega (6) | devices, device_sessions, refresh_tokens, audit_events, idempotency_records, outbox_events |

Total do modelo inicial: 33 tabelas operacionais/de referência. V11 acrescentou
`app_sessions` e `login_attempts` para autenticação local. V13 acrescentou `stock_movements`: são 36 tabelas mais
`flyway_schema_history`, gerenciada pelo Flyway. O banco atual tem 87 chaves
estrangeiras, quatro views e 19 migrações versionadas. Compras, receitas,
ingredientes e integrações permanecem fora desta entrega.

V19 acrescenta `cash_sessions.review_required`. A abertura do programa marca
turnos anteriores para conferência, preservando valores e histórico. O banco
impede recebimentos/movimentos nesse estado, uma nova abertura enquanto houver
pendência e a remoção da marca sem o fechamento contado. Caixa fechado continua
imutável; V1–V18 não foram alteradas. Verificação em banco preenchido:
`scripts/validar_migracao_v19.py`.

A migração **V12 — `simple_staff_login_and_archived_tables`** acrescenta
`users.username` e `bar_tables.active`, sem criar tabelas ou alterar V1–V11.
Ela permitia retirar mesas do salão sem apagar o histórico; a exclusão lógica
foi substituída pela exclusão permanente V15. V14 permite cadastros sem os dois
identificadores. As migrações devem ser aplicadas antes de iniciar a versão
da API que consulta essas colunas.

## V17 — identidade interna do atendimento

`users.system_attendant` é `NOT NULL DEFAULT false`: pessoas e cadastros antigos
permanecem sem alterações nos seus campos anteriores. A identidade interna do
APK usa `true`, não tem e-mail nem username (check no banco) e é única por
unidade (índice parcial). A API não a mostra como funcionário editável, não a
aceita em login/seleção de responsável e limita suas permissões a atendimento.

No modo local padrão, o PC conectado por loopback real é administrador; APK e
conexões da rede recebem atendimento. Cookies anteriores não substituem essa
decisão. Não há login nas telas; seleção voluntária de responsável da V13/V14
foi superada por esse perfil fixo. Ver [perfis PC/celular](../PERFIS-PC-CELULAR.md).
O padrão de nova abertura do caixa na interface é zero, editável; V17 não muda
`cash_sessions.opening_amount` nem reescreve recebimentos ou saldos existentes.

`scripts/validar_migracao_v17.py` foi executado em banco V16 preenchido e
descartável: hashes das 36 tabelas e checksums V1–V16 preservados, com usuário
normal/excluído, conta histórica e caixa com abertura positiva. Também valida
default false, ausência de identificadores e unicidade por unidade. O banco
temporário é removido; operação e treinamento não são usados como teste.

## V16 — exclusão de funcionários e índices dos relatórios

`users.deleted_at` diferencia exclusão definitiva do cadastro e desativação
reversível. A migração não exclui nem desativa funcionários existentes.
Check exige inatividade após exclusão; trigger impede restaurar/editar cadastros
excluídos. Identificadores históricos continuam válidos. A API protege o usuário
em uso, verifica administrador restante e revoga sessões/autorizações.

Índices suportam vendas encerradas por unidade/data/ID e somas por sessão de caixa.
V1–V15 permanecem sem alterações. Conferência com banco V15 preenchido:
`scripts/validar_migracao_v16.py` valida hashes, checksums e proteção contra restauração.

## V15 — mesas permanentes e exclusão de produtos

Mesas são removidas fisicamente, liberando seu número. `accounts` e
`table_authorizations` preservam `table_number_snapshot` e recebem `table_id`
nulo por FK `ON DELETE SET NULL (table_id)`, mantendo a unidade. Autorizações
são revogadas; pedidos, pagamentos, auditoria e saldos não são apagados.
Um trigger impede apagar mesa com atendimento, mesmo por SQL direto. Mesas
já arquivadas pela versão anterior são removidas por esta migração.

Produtos recebem `deleted_at`: somem do cadastro operacional e não podem ser
restaurados por edição. O registro interno permanece para referenciar vendas
e o livro imutável do estoque. Cancelamentos anteriores podem devolver saldo.
A nova interface registra consumo diretamente; não exige produção ou entrega.

## V13/V14 — estoque e funcionários sem credenciais

V13 inclui saldo, mínimo e controle de quantidade no produto e um livro de
movimentações imutável. Entradas, saídas, ajustes, vendas e devoluções registram
responsável, motivo e saldo após a operação. O trigger bloqueia o produto,
exige READ COMMITTED, calcula o saldo e rejeita quantidade negativa. Alteração
direta do saldo, desativação do controle e alteração/exclusão/truncamento do
histórico são rejeitados. Os novos produtos têm saldo zero até uma entrada.

Pedidos dão baixa atomicamente. Itens cancelados antes do preparo devolvem o
saldo; depois do preparo somente o retorno físico devolve. Itens antigos sem
movimento de venda não criam crédito artificial no estoque ao serem cancelados.
O controle é por produto vendido, sem ficha técnica de ingredientes/complementos.
Produtos existentes permanecem sem controle e com novos campos zerados até a
conferência manual do proprietário. Não inferir saldo a partir de vendas antigas.

V14 permite cadastro de funcionário sem e-mail/usuário/senha de entrada. Os
campos e hashes dos usuários antigos são preservados. A API continua guardando
as permissões e a identificação operacional; o novo front abre diretamente,
conforme solicitado pelo proprietário. Ver [API.md](../API.md).

## Convenções

- Schema `bar`, UUIDs, `timestamptz`, operação em UTC e fuso de exibição por unidade.
- BRL em `numeric(14,2)`, quantidades em `numeric(14,3)`, taxas percentuais em `numeric(7,4)`.
- Valores de taxa são percentuais: `10` significa 10%, não `0.10`.
- Estados e formas iniciais em português/maiúsculas, conforme o PDF. Entidades e campos em inglês.
- Toda entidade operacional tem `establishment_id`, chave única `(establishment_id,id)` e FKs compostas para pais da mesma unidade.
- `permissions` é catálogo global. `resource_id` e `aggregate_id` são referências polimórficas, validadas pelo serviço.
- Sem `account_items` duplicado; consumo vem dos pedidos e itens vinculados.
- Campos `version` são controlados explicitamente pela aplicação JDBC nas transições que os utilizam; não há `@Version`/JPA nesta implementação.
- Novas correções entram em novas migrações. Não editar migração já distribuída/aplicada.

## Garantias já presentes no banco

- Uma conta ativa por mesa; uma sessão aberta por terminal; uma autorização ativa por par usuário/mesa.
- `users.username` é opcional para contas antigas e único por unidade quando preenchido. Tem de 2 a 40 caracteres em minúsculas, com letras sem acento, números, ponto, traço ou sublinhado, começando por letra ou número.
- `users.email` e `users.username` podem ser nulos desde V14. Quando `system_attendant=true` (V17), ambos devem ser nulos e só pode existir uma identidade interna por unidade. Credenciais antigas são preservadas.
- Mesas têm `active=true` por padrão. Desde V15, `table.delete` remove a mesa livre fisicamente, libera o número e conserva nomes históricos nas contas; retirada lógica e restauração V12 são históricas.
- Mesa retirada não pode ter estado ocupado/reservado/aguardando pagamento ou conta aberta/em fechamento; uma conta ativa não pode apontar para uma mesa retirada. Triggers serializam abertura e retirada pela linha da mesa.
- Sem FK entre unidades diferentes. A proteção não substitui filtros de leitura, autorização HTTP ou RLS; RLS não foi introduzido nesta etapa.
- Preço, descrição, quantidade, destino, impostos e complementos vendidos preservados após alteração do catálogo.
- Produtos marcados indisponíveis não entram em novos itens.
- Pagamentos, estornos, pedidos, complementos vendidos, ajustes, produção, movimentos, auditoria e fechamentos imutáveis, com triggers contra alteração/exclusão.
- Totais não negativos, recebimento limitado ao saldo e estorno acumulado limitado ao original, com locks reais.
- Caixa fechado rejeita pagamentos, estornos e movimentos; fechamento exige seis linhas de conferência com esperado calculado pelo banco.
- Correções de fechamento referenciam o mesmo turno, sem ramificar a cadeia de correções.
- Compensação de ajuste anula integralmente o original da mesma conta; para alterar valor, compensar e criar outro ajuste na mesma transação.
- Conta fechada precisa estar quitada; conta cancelada não mantém consumo nem recebimento líquido.
- Produção pode avançar depois da quitação; não pode retroceder ou reativar cancelados.
- Resultado concluído de idempotência é imutável; chaves financeiras não expiram nem são excluídas pelo fluxo comum.
- Payload de outbox imutável; tentativas, horário de nova tentativa e publicação podem ser atualizados.

As regras entre linhas são verificadas por triggers e constraints adiadas até o
commit. Isso permite compensações e ajustes atômicos. Um serviço não pode anunciar
sucesso antes de confirmar o commit. A aplicação usará `READ COMMITTED`; outros
níveis são rejeitados nas escritas financeiras e na retirada de mesas para evitar
leitura de snapshot antigo após espera por locks.

## Consultas e cálculos

| View | Uso |
|---|---|
| order_item_totals | Total congelado por item, incluindo complementos |
| account_totals | Subtotal, ajustes, recebido, estornado, líquido e saldo |
| cash_session_totals | Esperado por turno e pelas seis formas iniciais |
| device_presence | Online, instável, offline ou bloqueado, por sessão |

Quantidade do complemento é **por unidade do item pai**:

```text
total da linha = round(quantidade do item * (preço do item
                 + soma(preço do complemento * quantidade do complemento)), 2)
total da conta = linhas não canceladas - descontos + serviço + acréscimos
pago líquido = pagamentos aplicados - estornos
saldo = total da conta - pago líquido
```

Cada ajuste é um efeito monetário. Rateios devem somar o valor oficial; não gravar
um cabeçalho com valor e linhas de rateio também contabilizadas, pois duplicaria
o ajuste. A taxa de serviço guarda base e valor calculado; o Java precisa
recalculá-la por compensação quando a base mudar.

Esperado em dinheiro: abertura + pagamentos aplicados em dinheiro + suprimentos
- sangrias - devoluções em dinheiro. `cash_movements` não replica pagamentos ou
devoluções. Estorno em turno posterior afeta o novo turno; não reescreve o anterior.
A diferença de fechamento é coluna gerada: contado menos esperado.

## Contrato das transações da API Java

Os fluxos principais abaixo já têm implementação em `Operations.java`. As
tabelas de dispositivos/refresh do modelo original permanecem reservadas;
a primeira autenticação usa `app_sessions`, com expiração fixa e revogação,
sem renovação por refresh token. Consulte [API.md](../API.md).

O contrato legado com sessão aceita usuário simples ou e-mail antigo para entrar;
ele não é usado para trocar o perfil fixo PC/APK no modo local padrão V17.
No cadastro legado de
funcionários, a senha tem mínimo de 4 caracteres; no perfil `ADMINISTRADOR`, 6.
A troca exige mínimo de 6 para quem possui `user.manage` e 4 para os demais,
sempre com máximo de 128. Essas regras pertencem ao serviço, que grava somente
o hash Argon2id e revoga sessões na troca; o banco não recebe a senha em texto.

`user.create` pode receber `allTables:true` para `ATENDIMENTO`: usuário, perfil
e autorizações das mesas ativas existentes são confirmados juntos. O padrão é
`false`; não libera mesas futuras nem muda a autorização de outras pessoas.

`table.delete` exige `table.manage` e, desde V15, exclui permanentemente uma mesa
livre, com idempotência, auditoria e outbox na mesma transação. A rotina bloqueia
a mesa, verifica estado e contas ativas sem bloquear contas; não inverte a ordem
financeira. Não há restauração do identificador excluído; o número pode ser usado
em novo cadastro. Contas e autorizações antigas preservam o nome por snapshot.

1. Abrir conta e ocupar mesa na mesma transação; controlar estados do salão.
2. Validar usuário, perfil, autorização de mesa e disponibilidade; congelar o preço vigente e os impostos; criar pedido, itens, complementos, auditoria e outbox juntos.
3. Garantir mínimo/máximo e associação dos complementos ao produto. As FKs desta etapa não substituem essas regras de seleção.
4. Acréscimos posteriores geram outro pedido. A API não deve oferecer inclusão silenciosa em pedido antigo.
5. Bloquear caixa antes da conta e, para estorno, depois o pagamento original; evitar inverter a ordem em outras rotinas.
6. Registrar recebimento e, se saldo zero, fechar conta e liberar mesa atomicamente. A trigger não libera a mesa sozinha.
7. Cancelar com permissão, motivo, evento de produção, auditoria e outbox; decidir perda/retorno físico quando houver preparo.
8. Estornar conta fechada exige reabertura autorizada (com `closed_at` nulo) e resolução do vínculo da mesa, antes do lançamento; não reocupar uma mesa reutilizada.
9. Gravar o fechamento e suas seis linhas, marcar o caixa fechado e confirmar tudo no mesmo commit. Correções têm outro fechamento com `corrects_id`.
10. Reservar idempotência antes de processar, comparar hash canônico, revalidar autorização e devolver a resposta original em reenvio. A chave única sozinha não implementa o replay HTTP.
11. Persistir somente hashes de senha/refresh, fazer rotação/revogação e encerrar conexões; o banco não autentica a sessão HTTP.
12. Publicar a outbox após commit com deduplicação e retentativa; o publicador local invalida telas por SSE, complementado pela sincronização periódica do cliente.

Para recuperar fila da outbox com vários workers, usar lock com `SKIP LOCKED`
na futura implementação e estratégia de reserva/entrega. Um evento publicado
pode precisar ser repetido; os consumidores devem ser idempotentes.

## Escolhas físicas e diferenças em relação ao mapa

- Número do pedido é `IDENTITY` global, único por unidade e com possíveis lacunas; não representa série fiscal nem sequência independente por bar.
- Todas as seis formas iniciais são conferidas, inclusive com valor zero. Tornar formas configuráveis exige uma futura migração de catálogo e views.
- `difference_amount` é gerada pelo PostgreSQL, evitando divergência manual.
- `created_at` adicional nos tokens ajuda a validar validade e cronologia.
- Presença não é persistida como status fixo: decorre de timestamps e bloqueio. Limites padrão de 180/600 segundos são configuráveis em `establishments.settings`.
- Os checks de serviço, estados e transições representam a política inicial; ajustes de negócio exigirão revisão antes da operação real.
- A API registra auditoria e outbox nas transações implementadas. Escritas administrativas feitas diretamente no banco não passam por essa auditoria da aplicação.

## Integração Java e próximas etapas

A API Java está implementada com Spring JDBC para usar explicitamente as views,
triggers e bloqueios do modelo. O arquivo `application-database.example.yml` é
uma referência histórica de configuração JPA; a configuração executada é
`application.yml`, com JDBC, UTC e `READ COMMITTED`, sem geração de DDL.
Flyway continua separado da credencial da aplicação. Dinheiro usa BigDecimal
no Java e string decimal no JSON.

O fluxo principal de atendimento está disponível nas telas e é validado em
bancos descartáveis. A próxima entrega é o piloto acompanhado, preenchimento
dos cadastros reais e validação de celulares físicos/rede local.

Referências técnicas: [PostgreSQL 17](https://www.postgresql.org/docs/17/),
[Flyway CLI](https://documentation.red-gate.com/flyway/reference/usage/command-line).
