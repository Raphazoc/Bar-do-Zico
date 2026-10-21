# Imprimir a guia da mesa — papel de 80 mm

O computador administrador imprime uma guia para conferir o consumo: mesa,
produtos, quantidades, preço unitário, valor de cada item, subtotal, descontos,
acréscimos e total. Se houve pagamento parcial ou devolução, aparecem o valor
líquido já recebido e o que falta pagar. Adicionais estão incluídos no preço
unitário e também são identificados abaixo do produto. Itens cancelados não
entram na guia. A data e a hora usam o fuso cadastrado para o bar.

## Testar a prévia agora, sem impressora

1. Abra **Bar do Zico - Treinamento** pelo atalho da Área de Trabalho.
2. Em **Mesas**, abra uma mesa com consumo e clique em **Ver consumo**.
3. Clique em **Imprimir guia da mesa**.
4. Confira a prévia: produtos, quantidades, valores e total.
5. Use **Fechar** para voltar ao atendimento. Quando tiver a impressora, use
   **Imprimir** nessa mesma janela.

No **Caixa**, selecione a mesa. **Visualizar / imprimir guia** e os controles de
pagamento aparecem juntos. Você pode imprimir e receber, ou receber diretamente,
sem imprimir primeiro. A prévia abre numa janela separada no PC, permitindo
continuar o pagamento; no navegador, aparece dentro da tela, sem bloquear os
controles. O valor a receber acompanha pagamentos parciais e novos produtos.

A guia não aparece mais no comprovante exibido depois do pagamento. Atendimentos
encerrados continuam com reimpressão em **Caixa → Vendas**.
No navegador, **Imprimir** abre o documento com os controles de impressão do
próprio navegador. No executável, abre a seleção da impressora do Windows.
A rotina tem apenas prévia, **Imprimir** e **Fechar**; a guia fica na memória
e não precisa ser salva em arquivo.

## Usar quando tiver a impressora

1. Conecte a impressora ao PC e instale seu driver para Windows, conforme as
   instruções do fabricante. Ela precisa aparecer nas impressoras do Windows.
2. Nas preferências do driver, configure o papel de **80 mm**, orientação retrato
   e tamanho real/100%, sem ampliar para preencher a folha.
3. Abra a guia no programa, clique em **Imprimir**, escolha a impressora e confirme.
4. Confira a primeira impressão. Margens, altura do papel e corte precisam ser
   acertados com o driver e o modelo que você comprar.

Não é preciso configurar portas ou comandos no Docker. A impressão usa a
impressora do Windows. Impressão direta por Bluetooth/ESC/POS, acionamento de
gaveta e comandos próprios de corte não estão implementados.

## O que a guia significa

Imprimir, cancelar a impressão ou fechar a prévia não registra
pagamento e não encerra a mesa. É uma conferência de consumo, identificada como
**Não fiscal**. A conta aberta avisa que a guia não confirma pagamento. O total
corresponde ao momento de emissão; se alterar o consumo, gere outra guia.

O histórico usa os nomes registrados na venda, mesmo após exclusão da mesa ou
produto. Contas longas são divididas em páginas de 80 mm; o nome do bar, a mesa,
o número da página e, no treinamento, a marca de exemplo permitem identificá-las.
Prévia nativa limitada às primeiras 10 páginas; imprimir inclui todas.

A função fica no **PC administrador**. O APK continua para atendimento, sem
acesso a pagamentos ou impressão. Não é necessário reinstalar o APK. O programa
Windows foi atualizado para **0.2.4**, no mesmo caminho usado pelos atalhos.

## Conferência técnica

`GET /api/accounts/{id}/guide.pdf` consulta o atendimento completo da unidade,
com transação de leitura `REPEATABLE READ`, sem depender das listas limitadas de
`/api/state`. Exige `payment.receive` ou `report.view` e rejeita atendimento móvel.
O PDF usa `Cache-Control: no-store`, fontes Noto Sans incorporadas (SIL OFL) e
[Apache PDFBox 3.0.8](https://pdfbox.apache.org/3.0/getting-started.html).

O cliente Windows busca apenas esse endpoint na origem configurada, mantendo a
sessão, e não aceita URLs ou comandos de impressão arbitrários da página.
Prévia e impressão usam o mesmo documento, preparado na memória. A impressão pelo
driver permanece pendente de teste físico com a futura impressora.

Verificação: 87 testes Java/API e 58 casos de interface aprovados, incluindo a
prévia no executável. Contas longas conferidas em 20 páginas, com títulos e
totais legíveis. Backups de operação e treinamento restaurados em bancos
temporários para conferência antes da atualização.
