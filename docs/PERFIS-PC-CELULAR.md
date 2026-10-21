# Computador administrador e celular de atendimento

## Uso no dia a dia

O **programa do PC é o administrador**: Mesas, Caixa, Funcionários, Estoque,
Cardápio e Configurações. O **APK é de atendimento**: consultar mesas e cardápio,
**Adicionar mesa**, **Abrir mesa**, visualizar o consumo e adicionar produtos à mesa.
Recebimentos, descontos, exclusões e demais tarefas de gestão ficam no PC.
Atendente e administrador podem criar mesas. Veja [o passo a passo](MESAS-NO-CELULAR.md).
Os dois continuam abrindo sem tela de login, em operação e treinamento.

No celular não aparece uma opção para virar administrador. Para ajustar o
endereço, use **Menu → Configurações → Conexão com o servidor**. São configurações
do aplicativo, sem funções de gestão. O botão **Conexão** saiu do topo; a tela
de erro oferece um atalho **Configurações**, acessível mesmo sem o servidor.

## Atualizar e conferir

1. No PC, mantenha **Bar do Zico** ou **Bar do Zico - Treinamento** iniciado.
   O executável permanece em **0.2.2**, com acesso de administrador.
2. No Android, instale o APK **0.2.3, código 4**, correspondente ao ambiente,
   por cima da versão anterior. A assinatura e os dados locais são preservados.
3. No computador, confira o perfil de administrador. No celular, confira o
   cabeçalho **Atendimento** ou **TREINAMENTO — Atendimento**. No menu ficam
   Mesas, Cardápio e Configurações do aplicativo. Toque em **Abrir mesa** numa
   mesa livre para começar; **Ver consumo** entra no atendimento já aberto.
4. Não limpe os dados nem desinstale o aplicativo se houver envio pendente.
   Reconecte e use **Conferir envio** antes de fazer outro lançamento.

## Abrir o caixa

No PC, clique em **Abrir caixa**. O dinheiro inicial começa em **R$ 0,00** e
pode ser alterado para o valor que realmente existe na gaveta.
Se o caixa já estiver aberto, use **Conferir caixa aberto** e depois
**Usar caixa aberto**. Não é necessário abrir outro turno para continuar.
O valor padrão zero vale para novas aberturas: caixas, recebimentos e saldos
anteriores continuam com os valores originais.

## Como os acessos são separados

No modo local padrão (`BAR_OPEN_ACCESS=true` e `BAR_DEVICE_ACCESS=true`), a API
determina o perfil pela conexão recebida. Administração exige uma conexão
real de loopback do próprio PC (`127.0.0.0/8` ou `::1`), sem o marcador do APK.
Conexões pela rede residencial recebem apenas atendimento, inclusive ao abrir
o endereço da rede no navegador do próprio PC. Use o atalho Windows para gerir.

O APK envia `BarDoZicoAndroid/0.2.3` no User-Agent. Esse marcador **só reduz** o
acesso para atendimento, inclusive em testes por ADB; nunca concede gestão.
Headers de encaminhamento, Host e cookies administrativos antigos não
transformam uma conexão de rede em administrador. A API aplica a separação
às leituras, comandos e dados retornados; esconder botões não é a proteção.
Cookies de seleção de funcionário de versões anteriores são ignorados nesse
modo. O PC usa o responsável de gestão fixo; o celular usa uma identidade
interna de atendimento. A escolha voluntária de responsável foi substituída.

V17 adiciona `users.system_attendant`, com padrão `false` nos cadastros antigos,
identidade interna sem e-mail/username e unicidade por unidade. A identidade
interna não aparece como funcionário editável e não redefine pessoas, senhas,
vendas ou movimentações antigas. Há 18 migrações versionadas; V18 acrescenta
`table.create` para o cadastro de novas mesas. V1–V17 não mudam.
O teste `scripts/validar_migracao_v17.py` migra um banco V16 preenchido e
descartável, comparando os hashes de 36 tabelas e os checksums anteriores.

## Verificação do Android 0.2.3

Passaram os 50 casos de interface, incluindo abertura de mesa pelo atendente,
configurações no menu e acesso às configurações quando a identificação do
servidor falha. Passaram também as dez verificações unitárias Android e lint
dos dois aplicativos. Os APKs foram assinados com a chave existente, com
versão 0.2.3/código 4 e hashes conferidos.

No emulador Android, foi conferido o caminho Menu → Configurações → Conexão
com o servidor, incluindo o diálogo nativo e a manutenção do endereço HTTPS.
Os serviços de operação e treinamento receberam a nova interface; o executável
Windows permanece em 0.2.2. Esta revisão não modifica o esquema do banco.
A instalação no celular físico continua a cargo do usuário.

## Histórico de verificação da versão 0.2.2

Os resultados abaixo pertencem à entrega anterior. Eles não comprovam a
validação do novo menu e do APK 0.2.3; essa revisão deve ter sua própria rodada.

Passaram 77 testes Java/API/endereço, 50 casos de interface em navegador com
tamanhos de computador e celular, oito verificações unitárias Android e lint
dos dois aplicativos. O executável foi conferido no JavaFX 25. Os dois APKs
foram assinados com a chave existente e seus hashes foram verificados.

Operação e treinamento foram atualizados para V17 e conferidos por HTTPS:
conexão local do PC como administrador e conexão pela rede como atendimento.
Os backups anteriores à atualização foram verificados; a comparação dos hashes
das 36 tabelas de cada banco confirmou que os dados existentes foram preservados.
A instalação da nova versão em um Android físico ainda precisa ser conferida.

## Rede residencial e Azure

Hoje o celular deve usar o Wi-Fi do mesmo roteador do PC, que pode estar no cabo.
O computador e o serviço precisam estar ligados. O perfil **Público** do Windows
é mantido; não é necessário mudar a rede para Privada. A internet externa pode
cair sem interromper o serviço local, desde que PC e rede residencial funcionem.

**4G e Wi-Fi de outra casa ainda dependem da implantação no Azure e da
autorização dos aparelhos.** A regra de loopback desta instalação local não
deve ser usada para conceder administração através de proxy na nuvem. O modo
local exige `server.forward-headers-strategy=NONE`; o piloto remoto precisa de
acesso autenticado próprio. Não há sincronização entre banco local e Azure.

Guias: [uso no PC](COMO-USAR-NOVO-FRONT.md),
[instalação Android](../output/android/COMO-INSTALAR.md) e [API](API.md).
