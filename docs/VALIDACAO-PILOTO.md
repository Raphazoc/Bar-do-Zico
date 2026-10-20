# Preparação do piloto

## Entrega atual — novo front e APK Android

Consulte [ENTREGA-NOVO-FRONT.md](ENTREGA-NOVO-FRONT.md): cinco áreas, abertura sem
login, estoque e dois APKs assinados. 29 testes Java e 22 testes de tela por
rodada, além da execução dos pacotes Windows e Android. V13/V14 preservaram
os registros anteriores. Regras locais do firewall aplicadas, perfil Public
preservado. O teste em aparelho físico e o piloto continuam pendentes.

As seções seguintes registram as entregas anteriores.


## Interface simplificada — 20/10/2026

A interface ganhou letras/campos/botões maiores, ações visíveis Editar/Excluir
nas mesas, confirmação de exclusão e recuperação do cadastro. Os menus usam
nomes diretos e o caixa/atendimento mostram passos curtos. Funcionários são
cadastrados sem e-mail e entram com usuário e senha simples; contas antigas
continuam usando o mesmo e-mail e senha.

- Compilação TypeScript/Vite e Java 25 aprovada; pacote Windows regenerado.
- 22 testes Java aprovados em cada modo: operação e treinamento.
- 21 testes Playwright aprovados em cada modo, nos formatos computador e
  Android emulado. Um caso exclusivo do menu móvel é pulado no computador.
- Cobertura nova: funcionário sem e-mail, senha de 4 caracteres, acesso às
  mesas atuais, compatibilidade com login por e-mail e limites compartilhados
  entre os identificadores da mesma conta.
- Exclusão/recuperação, repetição idempotente, permissões, isolamento por unidade,
  preservação de histórico e concorrência entre abrir/excluir mesa conferidos.
- Confirmação de exclusão começa em Cancelar; cancelar/Esc não envia exclusão
  e devolve o foco ao botão de origem. Mesa em atendimento fica protegida.
- Fluxo de pedido, produção, recebimento, fechamento, queda de conexão e
  resposta perdida aprovado nas telas com recursos externos bloqueados.
- Capturas de computador/celular revisadas, incluindo o diálogo de exclusão
  e o cadastro de funcionário sem campo de e-mail.
- Backup da operação restaurado em banco temporário e conferido antes da V12.
- V12 aplicada na operação e no treinamento. Comparação por contagem e hash
  das colunas anteriores de todas as 35 tabelas, exceto o histórico Flyway,
  confirmou preservação dos registros existentes, incluindo hashes das senhas.
  As mesas existentes ficaram ativas e os usuários antigos não receberam
  nome de acesso automático.
- Executável renderizou o login atualizado em HTTPS local com runtime próprio:
  Java 25.0.4.1 e JavaFX 25.0.1+3.

Capturas da entrega estão em `output/interface/hud-simples/`. Os testes de
comandos usam bancos descartáveis; não foram criadas mesas, funcionários ou
vendas de teste no banco de operação. O teste em Android físico e o piloto
com o proprietário continuam pendentes.

## Acabamento anterior da interface — 20/10/2026

Login e navegação foram reformulados, com menu lateral no computador e menu
compacto no Android. O salão recebeu busca por mesa/área e filtros por estado;
os cadastros ganharam um guia, busca de produtos e identificação da edição.
Os estilos das demais telas e a janela Java seguem a mesma identidade visual.

- 17 testes de interface aprovados no modo operação e 17 no treinamento.
  Em cada rodada, o caso de expiração com menu móvel é pulado no desktop.
- 13 testes da API aprovados no treinamento usando JDK 25.
- Fluxo de cadastro, pedido, produção, recebimento e fechamento conferido,
  incluindo perda de conexão e reenvio sem duplicação.
- Busca e filtros conferidos sem enviar comandos de abertura de conta.
- Menu conferido com teclado, identificação da tela atual e sessão expirada.
- Capturas do login, salão e atendimento revisadas em computador e Android.
- Executável Windows conferido em HTTPS local: Java 25.0.4.1 e JavaFX 25.0.1.
  O pacote contém somente as dependências atuais do JavaFX.

As verificações usam bancos descartáveis. Nenhum cardápio ou mesa real foi
preenchido nesta entrega. O teste em Android físico continua pendente.

## Entrega

Treinamento persistente em banco separado, conta e senha próprias, dois produtos
com preços fictícios e duas mesas de exemplo. As telas identificam o ambiente
antes e depois do login. A aplicação real mantém os cadastros para preenchimento
manual pelo proprietário.

O treinamento usa as mesmas regras de atendimento, produção e caixa da API Java.
Os processos, arquivos de controle e cookies são separados da operação real.
A preparação confere um marcador no database antes de alterar o treinamento.
Reabrir não limpa os exercícios e não redefine senhas. Não há comando de reset.

Também foram entregues a tela **Preparação**, com endereço e QR Code locais
para Android, e os backups automáticos com restauração de conferência.
O treinamento não pode acionar o backup do banco real.

## Verificações da base anterior do piloto

- Compilação TypeScript/Vite e Java aprovada.
- 13 testes Java e dez testes de tela aprovados no modo operação.
- 13 testes Java e dez testes de tela aprovados no modo treinamento.
- Cinco testes de agendamento, exclusão mútua, checksum e manifesto aprovados.
- Cópia real restaurada em banco temporário e comparada às contagens das
  36 tabelas do mesmo snapshot. O banco de operação não foi substituído.
- Solicitação de backup consumida pelo trabalhador automático; nova cópia
  restaurada e conferida, com estado de sucesso registrado.
- QR Code renderizado em telas de computador e Android; endereço arbitrário
  e acesso sem permissão rejeitados pela API.
- Os testes de interface cobrem tamanhos de computador e Android, identificação
  do ambiente, pedido/produção/pagamento/fechamento, perda de conexão e resposta
  perdida depois da confirmação no servidor.
- Preparação executada duas vezes: sem duplicar produtos/mesas nem alterar
  o usuário ou a senha do treinamento.
- Comparação dos cadastros reais antes/depois: unidade, usuários/senhas, áreas,
  mesas, categorias, produtos, contas, pedidos, pagamentos e turnos preservados.
- Nomes inválidos ou iguais ao banco real rejeitados pela configuração do treinamento.
- Login/logout reais do treinamento conferidos: cookie `BAR_SESSION` da operação
  preservado e cookie `BAR_TRAINING_SESSION` separado.
- Janela JavaFX abriu e renderizou o login do treinamento.
- Após a atualização, a API de treinamento em HTTPS respondeu com o endereço
  residencial priorizado; QR Code, certificado público e bloqueio de backup
  real foram conferidos. A janela JavaFX renderizou novamente o login por HTTPS.
- Encerrar/reabrir treinamento foi conferido sem encerrar a operação real.

As suítes Java/Playwright usam bancos descartáveis próprios. O banco persistente
de treinamento foi preservado para o exercício do proprietário. O banco de
operação não recebeu dados fictícios.

## Rede e limites

O proprietário confirmou o uso de Android. O diagnóstico desta máquina identificou
`Ethernet 2` com perfil **Pública** no Windows. O proprietário confirmou depois
que se trata de Wi-Fi residencial, mantendo o perfil Público do Windows.
A configuração foi preparada e conferida com `-WhatIf`: preserva esse perfil
e cria regras TCP 8443/18443 para o Java, restritas à interface e à sub-rede
local. A liberação efetiva exige a confirmação administrativa do Windows.
Não marcar as regras como aplicadas antes de conferir o resultado no sistema.

O certificado público também está disponível em `output/android/bar-ca.cer`
para transferência ao celular; os arquivos privados permanecem em `.tools/tls/`.

Backups horários locais estão ativos. A segunda pasta ainda não foi escolhida;
isso pode ser feito por **Configurar Backups.cmd**. Consulte
[BACKUP-E-RECUPERACAO.md](BACKUP-E-RECUPERACAO.md).

Continuam pendentes o firewall e o certificado no Android físico, o teste pelo Wi-Fi do bar,
a queda física da internet mantendo a rede local, o reinício completo do Windows
sem internet e o exercício acompanhado com o proprietário.

Guias: [PILOTO.md](PILOTO.md) e [ANDROID.md](ANDROID.md).

Para repetir as verificações em desenvolvimento:

```powershell
.venv/Scripts/python.exe scripts/validar_piloto.py
.venv/Scripts/python.exe scripts/validar_aplicacao.py --telas
.venv/Scripts/python.exe scripts/validar_aplicacao.py --treinamento --telas
```
