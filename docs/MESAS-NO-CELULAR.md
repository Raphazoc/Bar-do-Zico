# Criar mesas pelo celular e pelo computador

Atendente e administrador podem **cadastrar uma mesa nova**. O celular continua
voltado ao atendimento; edição e exclusão de mesas e locais ficam no PC.

## No celular

1. Abra **Mesas** e toque em **Adicionar mesa**.
2. Informe o **Número ou nome da mesa** e escolha o **Local da mesa**.
3. Toque em **Salvar mesa**. A mesa aparecerá no celular e no computador.
4. Para começar o atendimento, toque em **Abrir mesa** nessa mesa e adicione os produtos.

**Adicionar mesa** cadastra a mesa; **Abrir mesa** inicia o atendimento.
Cancelar o cadastro não salva nada. Se o número já existir, escolha outro.
Pedidos sem confirmação devem ser conferidos em **Conferir envio**, para evitar repetições.

Se o bar ainda não tem nenhum local, o primeiro cadastro cria **Salão** na mesma
transação. Com locais existentes, deixar o local sem seleção usa o primeiro
local ativo. Se todos estiverem desativados, o administrador deve ativar ou
cadastrar um local no computador.

## No computador

O administrador usa o mesmo botão **Adicionar mesa** e também pode editar ou
excluir mesas, respeitando os atendimentos em andamento. Uma mesa criada pelo
celular não precisa de liberação adicional para ser atendida.

## Atualização

A mudança está na API e na interface compartilhada. O APK **0.2.3** e o
executável **0.2.2** continuam compatíveis; não é necessário reinstalá-los.
Depois da atualização do serviço, feche e reabra o aplicativo no celular e a
janela do PC para carregar as telas novas. Operação e treinamento continuam separados.

## Implementação

- V18 acrescenta `table.create`, sem editar migrações anteriores.
- `table.create` aceita somente `{number, areaId?}` e não permite editar uma mesa.
- Atendimento recebe `table.create`, `account.open` e `order.create`.
- `table.save`, exclusão e administração de locais continuam exigindo `table.manage`.
- Cadastro, local padrão, auditoria e idempotência fazem parte da mesma transação.
- Cadastros concorrentes do PC/celular usam os mesmos bloqueios e a mesma regra de número único.

## Verificação

Passaram 82 testes da API e 50 casos de interface. O teste do APK cria a mesa
pelo formulário, confere o cadastro no PC, inicia o atendimento e lança consumo.
Também confere cancelamento e número repetido. A API testa reenvio, concorrência,
campos indevidos e local de outra unidade; a migração V18 foi validada sobre V17 preenchida.

Antes da instalação foram feitos backups de operação e treinamento, restaurados
em bancos temporários para conferência. Depois, os hashes de 34 tabelas de cada
banco confirmaram a preservação dos dados operacionais anteriores. API e
interface atualizadas foram conferidas por HTTPS nos dois ambientes.
