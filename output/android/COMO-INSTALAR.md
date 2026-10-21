# Instalar o Bar do Zico no Android

São dois aplicativos separados, para Android 8 ou mais recente:

- **Bar-do-Zico-Treinamento.apk:** exercícios com dados e preços fictícios.
- **Bar-do-Zico.apk:** dados da operação do bar.

Versão Android **0.2.3, código 4**. Instale por cima da versão anterior para receber
o perfil de **atendimento**; a assinatura foi preservada. Não é necessário desinstalar.
O **PC é administrador**, com executável **0.2.2**. Os dois APKs mostram **Mesas,
Cardápio e Configurações do aplicativo**. Toque em **Abrir mesa** para iniciar,
use **Adicionar mesa** para cadastrar uma mesa nova, consulte o consumo e adicione produtos.
Edição/exclusão de mesas, caixa e demais cadastros de gestão ficam no computador. O sistema continua sem login.

## Primeiro teste

1. No computador, feche a janela antiga e reabra **Bar do Zico - Treinamento**
   na Área de Trabalho, ou **Iniciar Treinamento.cmd** na pasta `E:\Bar`.
2. Copie **Bar-do-Zico-Treinamento.apk** para o celular, por exemplo usando um
   cabo USB. No celular, abra o arquivo pelo aplicativo **Arquivos**.
3. Se o Android pedir, permita a instalação por esse aplicativo de Arquivos.
   Conclua a instalação. Não precisa publicar ou procurar na Play Store.
4. Conecte o celular ao Wi-Fi do mesmo roteador do computador. O PC pode usar
   cabo; o perfil Público do Windows pode ser mantido. O acesso por 4G/outra rede
   depende de publicar e autorizar o servidor Azure, que ainda não foi criado.
5. Toque no ícone **Zico - Treinamento**. Confira **TREINAMENTO — Atendimento**
   no alto e o menu com Mesas, Cardápio e Configurações. Não há troca para
   administrador no APK.
6. No APK, abra **Menu → Configurações → Conexão com o servidor**. Se a tela
   não carregar, toque em **Configurações** no aviso de erro. No computador,
   abra **Configurações → Acesso pelo Android** e copie o endereço HTTPS.
7. Cole esse endereço no aplicativo e toque em **Salvar e conectar**.

O aplicativo já inclui o certificado público desta instalação. Não precisa
desativar a verificação de certificados. Se o certificado de confiança (CA) desta instalação for substituído,
será necessário gerar e instalar um APK atualizado. O endereço pode ser alterado
em **Menu → Configurações → Conexão com o servidor**.

Para operação real, use **Iniciar Bar na Rede.cmd** e instale o outro APK.
O aplicativo real usa a porta 8443; o treinamento usa 18443. Cada um guarda
sua própria configuração e seus próprios dados locais.

No PC, **Abrir caixa** começa com **R$ 0,00**, editável. Para continuar um caixa
existente, use **Conferir caixa aberto → Usar caixa aberto**. A atualização não
zera aberturas anteriores, recebimentos ou saldos. Cookies antigos do Android
não mantêm acesso de administrador. **Configurações** no menu do APK permite
ajustar a conexão, sem funções de gestão. O botão **Conexão** saiu do topo.

## Se não conectar

No futuro piloto Azure, será necessário confirmar a URL HTTPS pública e a
autorização do aparelho. Atualmente o acesso disponível é residencial. Nesse modo,
confirme que o computador está ligado, o serviço está aberto e os aparelhos
estão na mesma rede. Redes de convidados podem impedir comunicação local.
O Windows precisa permitir o acesso local do Java. O utilitário
**Configurar Rede do Bar.cmd** solicita a confirmação administrativa do Windows
para isso e preserva o perfil da rede. Não abra o PostgreSQL ou o roteador à internet.

Sem internet externa, o atendimento local continua. Sem Wi-Fi ou sem computador,
novos pedidos não são confirmados. Use **Conferir envio** depois de reconectar.
Não limpe os dados/desinstale o aplicativo enquanto houver envio pendente.

Usar o banco local e o futuro banco Azure com os mesmos dados durante quedas
de internet exigirá sincronização, que ainda não está implementada. Veja
[o estado real da revisão](../../docs/REVISAO-REDE-E-AZURE.md).
Detalhes dos acessos: [PC administrador e celular de atendimento](../../docs/PERFIS-PC-CELULAR.md).

## Atualizações

Instale a nova versão por cima da anterior, com a mesma assinatura. O código
Android está em `apps/android` e pode ser aberto no Android Studio. A chave de
atualização fica na pasta privada `.tools/android-signing`; preserve essa pasta.
