# API local — Bar do Zico

Java 25 LTS, Spring Boot 3.5.16, Spring Security e Spring JDBC. O SQL existente é a
fonte de verdade do modelo. JDBC foi usado para coordenar explicitamente os
bloqueios, views e transações já definidos; JPA não foi introduzido nesta entrega.
A API não altera o schema. Flyway aplica as migrações separadamente.

## Atualização V17 — PC administrador e celular de atendimento

O modo local padrão combina `BAR_OPEN_ACCESS=true` e `BAR_DEVICE_ACCESS=true`.
`GET /api/me` informa `deviceAccess` e `accessMode`: `ADMINISTRADOR` no programa
do PC conectado por loopback real, `ATENDIMENTO` no APK e demais conexões de rede.
O responsável administrativo é fixo; cookies anteriores de login/seleção não
mudam o perfil determinado pela conexão. `/operator`, login e senha não são
caminhos de elevação nesse modo. Não há tela de login.

A confiança local usa `request.getRemoteAddr()` (`127.0.0.0/8` ou `::1`), sem
interpretar Host ou headers de encaminhamento como prova de administração.
O marcador `BarDoZicoAndroid/0.2.2` no User-Agent somente reduz acesso para
atendimento, inclusive se a conexão chegar por loopback em testes/ADB.
`server.forward-headers-strategy=NONE` é obrigatório com perfis locais por
aparelho; acesso remoto por proxy exige a política autenticada do futuro piloto
Azure, ainda não implantado.

Atendimento pode consultar mesas e cardápio, abrir contas e adicionar produtos.
Leituras omitem gestão, caixa, equipe, relatórios, histórico de alterações e
outros dados administrativos; comandos de gestão/remoção/pagamento são negados
pela API, mesmo que alguém tente chamá-los sem os botões da interface.
Usa identidade interna com `users.system_attendant=true`, sem username/e-mail,
oculta nos cadastros e seletores. O default nos registros antigos é `false`;
check impede identificadores de login e índice único limita a uma por unidade.
Permissões de atendimento são restritas no servidor, sem editar os perfis antigos.

`cash.open` continua aceitando `openingAmount` não negativo. O formulário inicia
em `0`, editável. Caixas já abertos são apresentados para conferência/uso, sem
nova abertura ou reescrita do valor anterior. Históricos e saldos são preservados.
Guia: [PERFIS-PC-CELULAR.md](PERFIS-PC-CELULAR.md).

## Atualização V16 — locais, funcionários, resumo do caixa e vendas por mesa

- `area.delete`: `{areaId,destinationAreaId?}`, permissão `table.manage`.
  Com mesas, exige outro local ativo da mesma unidade; transfere e exclui na
  mesma transação. Mesas ocupadas mantêm atendimento e consumo. Cadastro de
  locais/mesas é serializado com a exclusão por lock da unidade.
- `staff.delete`: `{userId}`, `user.manage`. Marca `deleted_at`, desativa e revoga
  sessões/autorização de mesas. Histórico continua referenciando o autor original.
  O responsável atual é protegido; alterações de equipe/perfis são serializadas
  e exigem administrador ativo restante. Cadastro excluído não pode ser restaurado.
- `/state.cashSummaries`: `cash_session_id`, `cash_amount`, `net_received_amount`,
  calculados no SQL sobre todos os registros de cada uma das sessões devolvidas.
  Dinheiro inclui abertura e movimentos; vendas líquidas somam todas as formas
  menos devoluções e excluem abertura/suprimentos/sangrias. Valores monetários
  continuam strings decimais, como no restante da API.
- `GET /api/reports/sales?from=AAAA-MM-DD&to=AAAA-MM-DD&page=0`, `report.view`:
  `{rows,totalElements,page,pageSize:50,timezone}`. Linhas com `account_id`,
  `table_name`, `closed_at`, `total`. Datas inclusivas no fuso da unidade, período
  máximo de um ano, ordenação encerramento/ID decrescentes. Nome histórico da mesa
  tem precedência sobre o cadastro atual. Conta reaberta não aparece até encerrar.
  Total e página são lidos em snapshot consistente, sem limite operacional de 500.
- `/reports` e `/audit` permanecem compatíveis; auditoria só é exibida em
  Configurações com `audit.view`. Textos simples são apresentação, sem mudar códigos
  de permissões ou reescrever eventos antigos.

## Atualização V15 — exclusões e atendimento direto

- `table.delete` (`{tableId}`, `table.manage`) remove fisicamente uma mesa livre.
  Conta aberta impede a exclusão, inclusive com status de mesa incorreto. Número
  reutilizável; não existe restauração do identificador excluído. A conta antiga
  e a autorização revogada conservam `table_number_snapshot`; suas FKs ficam nulas.
- `product.delete` (`{productId}`, `catalog.manage`) marca `deleted_at` e desativa
  o cadastro de forma irreversível. `/state` não o inclui em `products`, mas
  mantém suas vendas e movimentações. Quantidade histórica não é zerada.
  Entradas, disponibilidade e novos consumos do cadastro excluído são rejeitados.
  Cancelamentos de vendas anteriores continuam podendo devolver estoque.
- A interface chama `order.create` ao clicar **Adicionar à mesa**, sem rascunho
  novo, envio posterior ou etapas de preparo/entrega. O estado interno `ENVIADO`
  permanece por compatibilidade com o banco e não exige avanço para pagar.
  `item.cancel` corrige o consumo com motivo e preserva as regras de estoque.
- Comandos mantêm unidade, permissão, auditoria, transação e idempotência.
  Os endpoints antigos de produção permanecem por compatibilidade, fora do novo front.

## Histórico V13/V14 — novo front e operação sem login (seleção superada pela V17)

O comportamento abaixo registra a implementação anterior; a separação fixa por
aparelho da V17 prevalece no modo local atual. Com `BAR_DEVICE_ACCESS=false`,
o contrato legado de seleção continua disponível para compatibilidade.

O modo anterior usa `bar.open-access=true` (variável `BAR_OPEN_ACCESS`), com abertura
direta. Sem cookie, a API associa a operação ao primeiro responsável ativo da
unidade configurada com `user.manage`. Não cria usuários, redefine senhas ou
mistura unidades. `GET /api/me` informa `openAccess`. `POST /api/operator` aceita
`{userId}` para identificar voluntariamente um funcionário sem senha e emitir
cookie; `{userId:null}` retorna à gestão local. É organização do aparelho, não
verificação de identidade. Host permitido, origem, header `X-Bar-Request`,
permissões da identidade selecionada, unidade e idempotência continuam aplicados.

Os endpoints antigos de login/senha são mantidos para compatibilidade e testes;
`BAR_OPEN_ACCESS=false` restaura a exigência de sessão na API. O novo front não
possui tela de login. O modo aberto é destinado à instalação e rede local do bar.

Novos comandos usam o mesmo endpoint `/api/commands/{operação}`, header de
idempotência, transação e auditoria dos comandos existentes:

| Operação | Corpo principal | Permissão |
|---|---|---|
| `staff.save` | `id?`, `name`, `roleId` ou `role` (`ATENDIMENTO`, `PRODUCAO`, `CAIXA`, `ADMINISTRADOR`), `allTables?` | `user.manage` |
| `role.save` | `id?`, `name`, `permissions`: lista de códigos | `user.manage` |
| `stock.move` | `productId`, `kind`: `ENTRADA`, `SAIDA` ou `AJUSTE`, `quantity`, `reason` | `stock.adjust` |
| `product.availability` | `productId`, `available`: booleano | `catalog.manage` |

`staff.save` não exige nem altera credenciais existentes. Ao editar um funcionário,
substitui seu perfil de forma transacional e preserva o histórico. `user.active`
continua ativando/desativando, com revogação de sessões. O próprio responsável
não pode se desativar nem remover sua permissão de gestão pelo novo editor.

`product.save` aceita `stockControlled`, `stockMinimum` e, somente no cadastro
novo, `stockQuantity` inicial. Quantidades usam strings decimais com até três
casas; preços mantêm duas casas. Quantidade inicial gera entrada no histórico.
Em `AJUSTE`, `quantity` é o saldo contado; o servidor calcula a diferença.
Produtos antigos continuam `stock_controlled=false` até ativação explícita.
O controle ativado é preservado; correções são novas movimentações.

`GET /api/state` acrescenta `stockMovements`, `roles`, `permissions` e
`rolePermissions` conforme as permissões. Produtos incluem `stock_controlled`,
`stock_quantity`, `stock_minimum` e `sellable` (disponibilidade efetiva, incluindo
saldo positivo quando controlado e categoria ativa). O histórico é limitado aos
mil movimentos mais recentes na tela; o banco mantém todos os movimentos.

A confirmação de pedido debita estoque na mesma transação. Estoque insuficiente
rejeita o pedido inteiro. Cancelamento anterior ao preparo devolve a quantidade;
após preparo, exige `RETORNO_FISICO`. `PERDA` mantém a baixa. Os triggers e
bloqueios do produto impedem saldo negativo; os itens de cada pedido são
bloqueados em ordem de UUID para reduzir conflitos entre pedidos concorrentes.
Repetir uma chave de idempotência não duplica movimento, venda ou devolução.

## Endpoints

| Método e caminho | Uso |
|---|---|
| `GET /api/health` | Saúde do processo e conexão com banco, sem credenciais |
| `POST /api/login` | Usuário simples ou e-mail antigo e senha da unidade configurada |
| `GET /api/me` | Usuário, unidade e permissões atuais |
| `POST /api/logout` | Revogar a sessão atual |
| `POST /api/password` | Senha atual e nova senha; revoga todas as sessões do usuário |
| `GET /api/state` | Dados para as telas, filtrados por unidade e perfil |
| `GET /api/accounts/{id}` | Consumo, complementos, ajustes e recebimentos da conta acessível |
| `GET /api/reports?from=AAAA-MM-DD&to=AAAA-MM-DD` | Vendas encerradas, recebimentos, devoluções e conferências no período |
| `GET /api/audit` | Últimos 200 eventos de auditoria da unidade |
| `GET /api/events` | SSE autenticado de invalidação das telas, sem payload de negócio |
| `GET /api/setup` | Contagens da unidade, endereços locais e estado dos backups; exige `user.manage` |
| `GET /api/setup/qr?address=...` | QR Code SVG para um endereço previamente configurado no servidor |
| `GET /api/setup/certificate` | Somente o certificado público configurado; sem acesso a caminhos enviados pelo cliente |
| `POST /api/setup/backup` | Solicita uma cópia ao trabalhador local; operação administrativa, desabilitada no treinamento |
| `POST /api/commands/{operation}` | Comando transacional com idempotência |

Dinheiro e quantidades são strings decimais JSON. Datas de relatório usam o fuso
da unidade e intervalo de até um ano. Vendas encerradas são agrupadas pela data
de fechamento; recebimentos e devoluções usam as próprias datas. Não são um
livro fiscal. As telas de histórico carregam lotes limitados; relatórios por período
consultam os registros completos correspondentes.

## Autenticação legada e modo com sessão

Esta seção descreve o contrato preservado para `BAR_OPEN_ACCESS=false` e os
testes de compatibilidade. No modo local padrão, prevalece a V17 acima:
sem login, PC administrador fixo e rede/APK apenas atendimento, independentemente
de cookies antigos.

A unidade é configurada no serviço por `BAR_UNIT_ID`, sem aceitar uma unidade
arbitrária enviada pelo cliente. Cada requisição revalida usuário ativo, sessão
e permissões. Sessão opaca aleatória de oito horas, sem renovação automática,
com apenas SHA-256 do token no banco. Login usa Argon2id no formato PHC existente.

O login recebe `{"username":"joao","password":"..."}`. O campo `username`
também aceita o e-mail de uma conta antiga; o corpo legado com `email` no lugar
de `username` continua válido. Se ambos forem enviados, prevalece `username`
quando preenchido. O identificador é normalizado para minúsculas, sem espaços
nas pontas. Senhas existentes e acessos por e-mail não são redefinidos.
`/api/me` e os usuários de `/api/state` incluem `username` e `email`: um deles
pode ser nulo. Contas antigas não recebem um nome de usuário inventado.

No cadastro, funcionários usam senha de 4 a 128 caracteres; o perfil inicial
`ADMINISTRADOR` exige de 6 a 128. Na troca de senha, quem possui `user.manage`
precisa de pelo menos 6 caracteres; os demais, 4. A senha nova precisa diferir
da atual. A troca revoga todas as sessões, mantendo os hashes Argon2id.

O cookie `BAR_SESSION` é HttpOnly, SameSite Strict e tem caminho `/api`.
No treinamento, o nome é `BAR_TRAINING_SESSION`, evitando colisão com a operação
no mesmo navegador. `/api/health` informa `mode` e `/api/me` informa `training`.
O modo vem da configuração do servidor e não pode ser alterado pelo cliente.
No perfil HTTPS `lan`, também é Secure. Nenhum token é guardado em localStorage.
Escritas exigem `X-Bar-Request: 1`, origem compatível quando informada e não
possuem CORS habilitado. Há lista explícita de hosts locais. Essa proteção de
requisição substitui o filtro CSRF padrão para a API JSON; nunca liberar CORS
amplo mantendo essa configuração.

Tentativas de login são limitadas por identidade/unidade e origem: oito por
identidade e 40 por IP em uma janela de 15 minutos. Limites persistem no banco.
Usuário simples e e-mail da mesma pessoa compartilham o limite da identidade.
Um login bem-sucedido limpa o contador da identidade. Não há envio de e-mail
ou recuperação automática de senha nesta entrega.

## Comandos

Todos exigem `Idempotency-Key` com 16 a 150 caracteres alfanuméricos, `_` ou `-`.
Use uma chave nova para uma intenção nova e preserve a chave para conferir/repetir
um envio incerto. A API compara o hash canônico do corpo, valida novamente a
permissão e devolve o resultado original quando o comando já foi confirmado.
Alterar o corpo com a mesma chave retorna conflito. A resposta confirmada é
`{"id":"uuid"}`. Erros usam `{"error":"mensagem"}` com status HTTP adequado.

| Operação | Permissão | Dados principais |
|---|---|---|
| `area.save`, `table.save`, `table.authorize` | `table.manage` | Área, identificação de mesa ou autorização de funcionário |
| `table.delete` | `table.manage` | `tableId`; retira mesa livre/bloqueada do salão, preservando histórico |
| `category.save`, `product.save` | `catalog.manage` | Nome, categoria, preço, destino, atividade e disponibilidade |
| `modifier-group.save`, `modifier.save`, `product.groups` | `catalog.manage` | Limites, opções e vínculos dos complementos |
| `account.open` | `account.open` | `tableId` e autorização de mesa |
| `account.close` | `account.close` | `accountId`, saldo zero |
| `account.reopen` | `account.reopen` | `accountId`, `reason`; desvincula mesa antiga |
| `account.adjust` | `account.adjust` | Conta, tipo, valor ou taxa, motivo; `reversesId` para compensar |
| `order.create` | `order.create` | Conta, itens com produto/quantidade/complementos e observações |
| `item.status`, `item.cancel` | `production.update` / `order.cancel` | Item, próximo estado ou motivo/destino físico |
| `terminal.save`, `cash.open` | `cash.open` | Terminal ou `openingAmount`; nova abertura na interface inicia em zero, editável |
| `cash.move` | `cash.move` | Turno, tipo, valor e motivo |
| `cash.close`, `cash.correct` | `cash.close` | Turno e contagem das seis formas; correção referencia fechamento anterior |
| `payment.receive` | `payment.receive` | Conta, turno, forma, valor aplicado e entregue; exige também `account.close` ao quitar |
| `payment.reverse` | `payment.reverse` | Pagamento, turno aberto, valor, forma e motivo; conta precisa estar aberta |
| `user.create` | `user.manage` | Nome, usuário, senha, perfil inicial e `allTables` opcional; e-mail opcional |
| `user.active` | `user.manage` | `userId` e `active`; revoga sessões ao alterar atividade |

`*.save` aceita `id` para editar um cadastro existente da unidade. Sem `id`,
cria um novo registro. Valores são parametrizados; tabelas e colunas vêm de
listas fixas no servidor. Consulte os tipos das telas e `Operations.java` para
o contrato completo de cada corpo.

### Cadastro legado com credenciais

O novo front usa `staff.save`, sem exigir credenciais. O contrato `user.create`
abaixo é preservado para compatibilidade; desde V14 o banco permite também
cadastros sem ambos os identificadores.

`user.create` recebe `name`, `username`, `password` e `role`. Perfis iniciais:
`ATENDIMENTO`, `PRODUCAO`, `CAIXA` e `ADMINISTRADOR`. `username` é único na
unidade, tem de 2 a 40 caracteres e aceita letras sem acento, números, ponto,
traço e sublinhado, começando por letra ou número. O servidor remove espaços
nas pontas e converte para minúsculas. Duplicidade de usuário ou e-mail retorna
HTTP 409 com uma mensagem explicativa.

`email` é opcional. Para compatibilidade, cadastro antigo contendo apenas um
e-mail válido como identificador continua aceito. Esse comando legado exige um
identificador; a regra não se aplica a `staff.save` ou à identidade interna V17.
Não são gravados e-mails fictícios para funcionários.
Respostas e auditoria não incluem senha nem hash de senha.

`allTables` é um booleano opcional, com padrão `false`. Em `ATENDIMENTO`, quando
`true`, cria autorizações para as mesas ativas já cadastradas na mesma transação
do usuário e perfil. Não libera mesas criadas depois nem altera outros usuários.
Para outros perfis, a opção não concede autorizações. A tela de funcionários
oferece a opção de liberação inicial e permite ajustar os acessos depois.

### Histórico V12 — exclusão lógica e restauração de mesas (superado pela V15)

O contrato abaixo é histórico. Atualmente `table.delete` remove a mesa livre
permanentemente, libera seu número e preserva os nomes nas contas antigas,
conforme a atualização V15 no início deste documento. Não orientar restauração
de mesa excluída no programa atual.

`table.delete` recebe `{"tableId":"uuid"}` no endpoint de comandos, com a
mesma chave de idempotência dos demais comandos. A exclusão marca `active=false`;
não apaga mesa, pedidos, contas, pagamentos nem autorizações existentes.
O estado da API continua incluindo a mesa para consulta histórica; as telas
de atendimento devem mostrar somente mesas ativas.

A mesa precisa estar `LIVRE` ou `BLOQUEADA`, sem conta `ABERTA` ou
`EM_FECHAMENTO`. Se estiver em atendimento, retorna HTTP 400 e não altera
nenhum dado. A abertura de conta também rejeita mesas excluídas. Há proteção
correspondente no banco para impedir que escritas diretas contornem essas regras.

Para restaurar, envie `table.save` com `id`, `areaId`, `number` e `active:true`.
Editar uma mesa sem enviar `active` preserva sua atividade atual; não a restaura
por acidente. `active:false` em `table.save` é recusado: use `table.delete`.
O número de uma mesa excluída permanece reservado; restaure-a ou use outro número.

As escritas usam READ COMMITTED. Pagamento/estorno bloqueiam caixa antes de conta
e pagamento original. Alteração, auditoria, evento de outbox e resultado de
idempotência são confirmados juntos. Correções financeiras preservam histórico.
Exclusão e abertura se serializam pela linha da mesa. A exclusão não bloqueia
contas e não inverte a ordem financeira de bloqueios.

O publicador local envia notificações SSE após commit, com identificação do evento,
retentativa e deduplicação no cliente. As telas também sincronizam a cada cinco
segundos. A notificação invalida dados; o cliente relê o estado autorizado.
Não há mensageria ou integração externa de eventos nesta entrega.

## Ambiente de treinamento

O inicializador provisiona um database `bar_training_<uuid>` persistente, com
marcador de propriedade, unidade e administrador exclusivos. Os dados iniciais
fictícios são criados uma única vez. Reexecutar não limpa lançamentos, não duplica
cadastros e não redefine senhas. Não existe comando de reset nesta entrega.

O servidor confere a compatibilidade entre modo e nome do banco antes de aceitar
conexões HTTP. Treinamento local usa `18081`; treinamento HTTPS usa `18443`.
Os modos de operação mantêm `8080`/`8443`. Os processos e arquivos de controle
são separados; parar o treinamento não encerra a operação.

## Backup e preparação

O servidor usa `BAR_ANDROID_URLS`, `BAR_PUBLIC_CERTIFICATE` e `BAR_BACKUP_DIRECTORY`
definidos pelo inicializador. A API não recebe caminhos arbitrários do cliente.
O QR aceita somente endereços da lista configurada. O estado de backup expõe
campos permitidos, sem credenciais ou configuração completa de arquivos locais.

O trabalhador Python no usuário local usa a credencial de manutenção para o
backup. A API Java continua com `bar_app` e não recebe senha de migração para
essa função. Uma solicitação administrativa cria apenas um pedido em arquivo
fixo; não executa comandos fornecidos pelo cliente. Backup manual e automático
compartilham trava e validação por snapshot/SHA-256.
