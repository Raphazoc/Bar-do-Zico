# Bar do Zico — Tutorial de operação

## 1. Para que serve o sistema

O Bar do Zico organiza o atendimento de mesas, o cardápio, os pedidos, o caixa,
os pagamentos, o estoque e os relatórios do bar.

O sistema funciona com três frentes:

- **Computador:** administração, caixa, funcionários, estoque, cardápio,
  relatórios, configurações e impressão da guia da mesa.
- **Celular Android:** atendimento. O atendente consulta mesas e cardápio,
  cadastra mesa, abre atendimento e adiciona produtos.
- **Treinamento:** ambiente separado para praticar sem alterar a operação real.

O sistema local não precisa de internet externa depois de instalado, mas precisa
que o computador e a rede local estejam funcionando. O celular deve estar no
mesmo Wi-Fi do computador. O acesso por 4G ou por outra rede depende de uma
implantação remota que ainda não faz parte desta versão.

## 2. Regra mais importante: PC e celular têm funções diferentes

O **PC é o administrador**. O **Android é o atendimento**.

No PC ficam:

- Mesas e locais: cadastrar, editar, excluir e transferir mesas.
- Caixa: abrir, receber, estornar, movimentar e fechar.
- Funcionários e permissões.
- Estoque e cardápio.
- Relatórios de vendas e histórico de alterações.
- Configuração de Android, certificado e backups.
- Visualização e impressão da guia da mesa.

No celular ficam:

- Consultar mesas e cardápio.
- Cadastrar uma mesa nova.
- Abrir atendimento.
- Ver o consumo.
- Adicionar produtos e complementos.
- Configurar o endereço do servidor no próprio aplicativo.

O celular não possui uma opção para virar administrador. Esconder um botão não
é a única proteção: a API também bloqueia as operações administrativas.

## 3. Abrir o sistema no computador

### 3.1 Treinar

1. Na Área de Trabalho, abra **Bar do Zico - Treinamento**.
2. Aguarde o banco e a API iniciarem.
3. A tela inicial será **Mesas**.
4. Confirme a faixa amarela de **TREINAMENTO**.

O treinamento usa banco separado e não deve ser usado para registrar vendas
reais.

### 3.2 Operar o bar

1. Abra o atalho **Bar do Zico**.
2. Aguarde o serviço iniciar.
3. Use a tela **Mesas** para começar.

Se os atalhos não estiverem disponíveis, use `Iniciar Bar.cmd` para uso local
ou `Iniciar Bar na Rede.cmd` quando houver celulares no Wi-Fi.

Fechar a janela do programa não necessariamente encerra o serviço. Isso permite
que os celulares continuem conectados. Para parar o serviço, use
**Parar Bar.cmd** ou **Parar Treinamento.cmd**.

## 4. Configurar o Android

### 4.1 Instalação

Existem dois APKs:

- `Bar-do-Zico-Treinamento.apk`: dados fictícios.
- `Bar-do-Zico.apk`: operação real.

O Android precisa ser versão 8 ou mais recente.

1. Inicie o ambiente correspondente no PC.
2. Copie o APK para o celular.
3. Abra o arquivo pelo aplicativo **Arquivos**.
4. Autorize a instalação quando o Android solicitar.
5. Instale por cima da versão anterior, se houver. Não desinstale sem necessidade.
6. Conecte o celular ao mesmo roteador do computador.

### 4.2 Endereço do servidor

1. No PC, abra **Configurações → Acesso pelo Android**.
2. Copie o endereço HTTPS apresentado.
3. No celular, abra **Menu → Configurações → Conexão com o servidor**.
4. Cole o endereço.
5. Toque em **Salvar e conectar**.

Se a tela principal não abrir, use o botão **Configurações** no aviso de erro.
Não desative a validação de certificado e não use endereço HTTP.

### 4.3 Se o celular não conectar

Confira, nesta ordem:

1. O PC está ligado?
2. O serviço correto está aberto?
3. O celular está no mesmo Wi-Fi?
4. O endereço começa com `https://`?
5. O Windows permitiu a rede do Java?
6. O treinamento está usando o endereço de treinamento e a operação está
   usando o endereço de operação?

Se houver um envio pendente, não limpe os dados nem desinstale o aplicativo.
Reconecte e use **Conferir envio**.

## 5. Preparar mesas e locais

### 5.1 Cadastrar uma mesa

No PC ou no celular:

1. Abra **Mesas**.
2. Toque em **Adicionar mesa**.
3. Informe o número ou nome.
4. Escolha o local, se aparecer essa opção.
5. Toque em **Salvar mesa**.

Se o bar ainda não tiver local cadastrado, o primeiro cadastro cria
automaticamente o local **Salão**. Se já houver locais, o sistema usa o primeiro
local ativo quando nenhum for escolhido.

O número precisa ser único. Se já existir, escolha outro.

### 5.2 Editar ou excluir uma mesa

Essas funções são administrativas e ficam no PC.

- **Editar:** altera o cadastro da mesa.
- **Excluir:** remove a mesa permanentemente e libera o número.

Não exclua uma mesa com atendimento aberto. O histórico dos atendimentos antigos
continua preservado mesmo quando o cadastro da mesa é removido.

### 5.3 Organizar locais

Locais podem representar salão, varanda, deck ou outra área.

1. Abra a seção de locais abaixo das mesas.
2. Para excluir um local com mesas, escolha outro local ativo.
3. Confirme a transferência.

Mesas ocupadas continuam com o mesmo atendimento durante a transferência.

## 6. Abrir atendimento e lançar consumo

### 6.1 Abrir uma mesa

1. Em **Mesas**, localize uma mesa livre.
2. No PC, clique em **Abrir atendimento**.
3. No celular, toque em **Abrir mesa**.
4. A mesa passará a mostrar o atendimento aberto.

Em uma mesa que já possui atendimento, use **Ver consumo**.

### 6.2 Adicionar produto

1. Abra o consumo da mesa.
2. Escolha uma **Categoria**.
3. Escolha o **Produto**.
4. Informe a quantidade.
5. Se necessário, selecione os complementos.
6. Inclua uma observação, quando necessário.
7. Toque em **Adicionar à mesa**.

O lançamento é salvo imediatamente e o estoque é baixado quando o produto tem
controle de estoque. Trocar a categoria limpa a seleção anterior para evitar
que um produto ou complemento errado seja enviado.

Se o produto não estiver disponível, o envio fica bloqueado.

### 6.3 Remover um item

1. Abra o consumo.
2. Localize o item.
3. Clique em **Remover**.
4. Informe o motivo.
5. Confirme.

O item deixa de compor o total, mas o histórico da alteração continua guardado.
Se o item tiver vindo de estoque, a devolução depende do estado em que ele se
encontrava e do motivo informado.

### 6.4 Envio pendente

Se a conexão cair durante um lançamento:

1. Aguarde a reconexão.
2. Use **Conferir envio**.
3. Verifique se o produto foi registrado.
4. Não toque várias vezes em enviar.

O sistema usa uma chave de idempotência para impedir duplicação. O aviso de
falha não significa automaticamente que o lançamento não foi gravado.

## 7. Trabalhar com estoque e cardápio

### 7.1 Cadastrar produto

No PC:

1. Abra **Estoque** ou **Cardápio**.
2. Clique em cadastrar produto.
3. Informe nome, categoria e preço.
4. Defina se o produto está ativo e disponível.
5. Se usar estoque, informe o controle, a quantidade inicial e o estoque mínimo.
6. Salve.

O cadastro do produto alimenta o cardápio. Produto inativo, indisponível ou sem
estoque controlado disponível não pode ser adicionado à mesa.

### 7.2 Alterar quantidade

Para um produto já existente, não edite a quantidade diretamente. Use:

- **Registrar entrada:** mercadoria recebida.
- **Registrar saída:** perda, descarte ou retirada.
- **Ajustar quantidade:** correção após conferência física.

No ajuste, informe o saldo contado, e não apenas a diferença. Sempre informe o
motivo. O histórico mostra o saldo depois da operação.

### 7.3 Complementos

Grupos de complementos podem limitar quantidade mínima e máxima. O produto pode
ser associado a um ou mais grupos. O preço dos complementos aparece no consumo
e na guia da mesa.

## 8. Abrir e usar o caixa

### 8.1 Conferir caixa anterior

Ao abrir uma nova janela, o sistema não abre um caixa automaticamente.

Se houver um caixa anterior pendente:

1. Abra **Caixa**.
2. Escolha **Conferir caixa anterior**.
3. Clique em **Conferir e fechar**.
4. Confira os totais calculados pelo sistema.
5. Clique em **Fechar caixa**.

Só depois será possível abrir um novo caixa. Fechar a janela do programa não
fecha o caixa.

### 8.2 Abrir um caixa

1. Em **Caixa**, clique em **Abrir caixa**.
2. Escolha ou cadastre o terminal.
3. Confira o dinheiro inicial.
4. O padrão é **R$ 0,00**, mas o valor pode ser editado.
5. Confirme a abertura.

O valor zero vale apenas para novas aberturas. Ele não altera caixas antigos,
recebimentos ou saldos já registrados.

### 8.3 Receber uma conta

1. Em **Contas abertas**, selecione a mesa.
2. Confira produtos, adicionais, descontos e acréscimos.
3. Se desejar, clique em **Visualizar / imprimir guia**.
4. Escolha a forma de pagamento:
   - Dinheiro;
   - Pix;
   - Débito;
   - Crédito;
   - Voucher;
   - Outro.
5. Para pagamento parcial, marque a opção e informe o valor.
6. No dinheiro, informe o valor entregue para calcular o troco.
7. Clique em **Receber e finalizar mesa**.

A mesa só é liberada quando a conta estiver quitada. Pagamentos parciais não
criam uma segunda venda.

Pix e cartões são registrados como pagamentos já confirmados. O sistema não
cobra automaticamente na maquininha ou no banco.

### 8.4 Desconto e acréscimo

1. Selecione a conta.
2. Clique em **Aplicar desconto** ou **Adicionar acréscimo**.
3. Informe valor ou taxa, conforme o formulário.
4. Informe o motivo.
5. Confirme.

Correções e reversões preservam o histórico anterior.

### 8.5 Entradas e retiradas

Em **Movimentações**, registre:

- suprimento;
- sangria;
- perda;
- retorno físico;
- correção autorizada.

Informe sempre o motivo. Entradas e retiradas alteram o dinheiro previsto em
caixa, mas não são vendas.

### 8.6 Fechar o caixa

1. Selecione o caixa.
2. Clique em **Fechar caixa**.
3. Confira os valores separados por forma de pagamento.
4. Confira o total do fechamento.
5. Confirme.

O fechamento usa os totais calculados pelo sistema. Não é necessário preencher
uma contagem manual nessa rotina. Uma correção física posterior deve ser feita
pela função própria de correção, com motivo.

## 9. Guia da mesa e impressão

A guia é uma conferência não fiscal. Ela não recebe pagamento e não encerra a
mesa.

1. Abra o consumo ou selecione a conta no caixa.
2. Clique em **Imprimir guia da mesa** ou **Visualizar / imprimir guia**.
3. Confira produtos, complementos, subtotal, descontos, acréscimos, total,
   recebido e saldo.
4. No PC, clique em **Imprimir**.
5. Escolha uma impressora configurada para papel de 80 mm.

No navegador, a impressão usa o diálogo do próprio navegador. No executável
Windows, usa a impressora do sistema. Itens cancelados não aparecem na guia.
Contas antigas podem ser reimpressas em **Caixa → Vendas**.

## 10. Consultar vendas e histórico

### Vendas

1. Abra **Caixa → Vendas**.
2. Escolha a data inicial e final.
3. Clique em **Consultar período**.
4. Use **Anterior** e **Próxima** quando houver mais de uma página.

Cada atendimento encerrado aparece como uma venda separada, mesmo que a mesma
mesa tenha sido usada várias vezes no dia.

### Histórico de alterações

1. Abra **Configurações**.
2. Expanda **Histórico de alterações**.
3. Clique em **Consultar histórico**.

A consulta depende da permissão `audit.view`. O histórico mantém quem realizou
as operações e os motivos informados.

## 11. Funcionários e permissões

No PC:

1. Abra **Funcionários**.
2. Clique em **Cadastrar funcionário**.
3. Informe o nome.
4. Escolha a função ou perfil.
5. Salve.

É possível editar, ativar, desativar e excluir cadastros. A exclusão retira o
funcionário das seleções futuras, mas preserva seus lançamentos históricos.

Os grupos de permissão são:

- Mesas e atendimento.
- Caixa.
- Estoque e cardápio.
- Administração.

Não é possível excluir o responsável em uso nem deixar o bar sem administrador.
O responsável administrativo do PC é separado da identidade interna usada pelo
atendimento no celular.

## 12. Rotina recomendada

### Abertura do dia

1. Inicie o ambiente de operação.
2. Confirme que o PC abre em **Mesas**.
3. Confira se não há fechamento pendente.
4. Abra ou reutilize o caixa.
5. Confira mesas, cardápio e produtos disponíveis.
6. Conecte os celulares e teste a abertura de uma mesa no treinamento quando
   houver atualização.

### Durante o atendimento

1. Abra a mesa antes de lançar produtos.
2. Lance cada produto e complemento uma vez.
3. Observe os avisos de envio pendente.
4. Use **Conferir envio** depois de qualquer queda de conexão.
5. Registre retiradas, perdas e correções de estoque com motivo.

### Encerramento

1. Confira as contas abertas.
2. Receba as contas quitadas.
3. Verifique pagamentos parciais restantes.
4. Feche o caixa pelo total do sistema.
5. Faça o backup conforme a rotina do bar.

## 13. Limitações conhecidas

- Não há sincronização entre banco local e nuvem.
- O celular não funciona para novos lançamentos sem conexão com o PC/servidor.
- Não há emissão fiscal.
- Não há cobrança automática em banco ou maquininha.
- Não há delivery.
- O estoque controla produtos e movimentações, não receitas ou ingredientes.
- A impressão depende de uma impressora instalada no Windows.
- O acesso remoto por 4G ainda depende de implantação e autorização próprias.

## 14. Ajuda rápida

### “O celular não conecta”

Verifique serviço, Wi-Fi, endereço HTTPS, firewall e ambiente correto.

### “O botão de enviar está bloqueado”

Confira se o produto está ativo, disponível, dentro da categoria correta e, se
for controlado, se possui estoque.

### “Não consigo abrir o caixa”

Existe provavelmente um caixa anterior pendente. Feche-o antes de abrir outro.

### “O lançamento pode ter duplicado”

Não lance novamente. Reconecte e use **Conferir envio**.

### “Não consigo administrar pelo celular”

Isso é esperado. O Android é o perfil de atendimento. Use o programa do PC.

### “A guia não imprime”

Confira se a prévia abre, se a impressora aparece no Windows, se o driver está
instalado e se o papel está configurado para 80 mm.

## 15. Atalhos principais

| Arquivo | Função |
|---|---|
| `Iniciar Bar.cmd` | Inicia a operação local no PC |
| `Iniciar Bar na Rede.cmd` | Inicia a operação para PC e celulares |
| `Iniciar Treinamento.cmd` | Inicia o ambiente de treinamento |
| `Parar Bar.cmd` | Para o serviço da operação |
| `Parar Treinamento.cmd` | Para o serviço de treinamento |
| `ferramentas/Configurar Rede do Bar.cmd` | Configura as regras de rede locais |
| `ferramentas/Verificar Rede.cmd` | Diagnostica a rede |
| `ferramentas/Fazer Backup.cmd` | Executa e confere uma cópia |
| `ferramentas/Configurar Backups.cmd` | Configura a pasta secundária de cópias |
| `Criar Atalhos.cmd` | Recria os atalhos da Área de Trabalho |
