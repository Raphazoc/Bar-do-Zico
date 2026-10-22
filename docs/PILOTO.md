# Roteiro do primeiro uso — treinamento

**Guia atual:** o programa abre sem login e há APK instalável. Consulte
[Como usar o novo front](COMO-USAR-NOVO-FRONT.md) e
[Instalar o Android](../output/android/COMO-INSTALAR.md). O roteiro abaixo
registra a configuração anterior pelo navegador e não exige mais a entrada
por usuário/senha na versão atual.

O treinamento tem banco, administrador e senhas separados da operação real.
Ele já contém duas mesas e dois produtos com preços fictícios, identificados
como exemplos. Os dados reais do Bar do Zico continuam disponíveis apenas
no ambiente de operação, para cadastro manual.

## Abrir no computador

1. Abra a pasta `E:\Bar` no Explorador de Arquivos.
2. Dê dois cliques em **Iniciar Treinamento.cmd** e aguarde a janela Java abrir.
3. Abra **ferramentas/Acesso do Treinamento.cmd** e consulte a senha. No campo **Usuário
   ou e-mail**, digite `treinamento@bar.local`, preencha **Senha** e clique em
   **Entrar**.
4. Confira a faixa **TREINAMENTO** antes de começar. O endereço local deste
   modo é `http://127.0.0.1:18081`.

Ao abrir novamente, o treinamento mantém suas contas e lançamentos anteriores.
O inicializador não apaga os exercícios nem redefine a senha. Se você trocar
a senha durante o treino, o arquivo de acesso inicial não será atualizado.

## Exercício de atendimento e caixa

1. Em **Caixa**, abra **Abrir caixa — começar o dia**. Escolha o caixa de
   treinamento, informe R$ 50,00 em **Dinheiro inicial (R$)** e clique em
   **Abrir caixa**.
2. Em **Mesas**, abra uma mesa livre. Escolha um dos produtos de exemplo,
   quantidade e observação; adicione e envie o pedido.
3. Em **Cozinha e bar**, encontre o pedido e avance até **Entregue**.
4. Em **Caixa**, selecione o caixa em **Caixa que você quer conferir**. Em
   **Receber pagamento**, selecione a conta e registre o recebimento em dinheiro.
   Use o saldo mostrado na tela. Para testar o troco, informe um valor entregue
   maior que o valor aplicado.
5. Confira que o saldo ficou zerado e que a mesa voltou a ficar livre.
6. Conte o caixa: abertura mais pagamentos aplicados em dinheiro, considerando
   também eventuais sangrias, suprimentos e devoluções que você tenha feito.
7. Em **Fechar caixa — terminar o dia**, informe as seis formas de pagamento
   e clique em **Fechar caixa**.
8. Abra **Vendas**, informe **Data inicial** e **Data final** e clique em
   **Consultar período** para conferir os recebimentos.

Esses exercícios não movimentam banco, Pix ou maquininha. São registros fictícios
no ambiente separado. O histórico real do bar não recebe os exercícios.

## Repetir pelo Android

Feche apenas a janela do treino e execute **Parar Treinamento.cmd** para mudar
de modo. Depois siga [ANDROID.md](ANDROID.md). O modo de rede do treinamento
usa HTTPS na porta `18443`, preservando os mesmos exercícios.

Faça um pedido no Android e acompanhe-o no computador. Depois teste o retorno
ao Wi-Fi quando uma solicitação ficar sem confirmação, usando **Conferir envio**.

## O que falta conferir com o proprietário

- [ ] Entrar no treinamento e completar o exercício no computador.
- [ ] Instalar o certificado e abrir pelo Android físico.
- [ ] Enviar do Android e acompanhar a produção no computador.
- [ ] Simular queda da internet externa mantendo computador e Wi-Fi ligados.
- [ ] Confirmar as regras de desconto, serviço, cancelamento e estorno do bar.
- [ ] Definir responsável e segundo destino dos backups reais.
- [ ] Preencher mesas e cardápio reais manualmente no ambiente de operação.

Fechar a janela mantém o treinamento ativo para os aparelhos. **Parar
Treinamento.cmd** encerra somente o serviço de treinamento; não encerra a API
real e não apaga dados. O banco de treinamento fica persistido no Docker.
