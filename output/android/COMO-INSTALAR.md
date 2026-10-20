# Instalar o Bar do Zico no Android

São dois aplicativos separados, para Android 8 ou mais recente:

- **Bar-do-Zico-Treinamento.apk:** exercícios com dados e preços fictícios.
- **Bar-do-Zico.apk:** dados da operação do bar.

## Primeiro teste

1. No computador, abra **Treinamento no Android.cmd** na pasta `E:\Bar`.
2. Copie **Bar-do-Zico-Treinamento.apk** para o celular, por exemplo usando um
   cabo USB. No celular, abra o arquivo pelo aplicativo **Arquivos**.
3. Se o Android pedir, permita a instalação por esse aplicativo de Arquivos.
   Conclua a instalação. Não precisa publicar ou procurar na Play Store.
4. Conecte o celular ao mesmo Wi-Fi do computador.
5. Toque no ícone **Zico - Treinamento**. O sistema abre sem pedir login.
6. Se não conectar, toque em **Conexão** no alto do aplicativo. No computador,
   abra **Configurações → Acesso pelo Android** e copie o endereço HTTPS.
7. Cole esse endereço no aplicativo e toque em **Salvar e conectar**.

O aplicativo já inclui o certificado público desta instalação. Não precisa
desativar a verificação de certificados. Se o certificado de confiança (CA) desta instalação for substituído,
será necessário gerar e instalar um APK atualizado. O endereço pode ser alterado por Conexão.

Para operação real, use **Iniciar Bar na Rede.cmd** e instale o outro APK.
O aplicativo real usa a porta 8443; o treinamento usa 18443. Cada um guarda
sua própria configuração e seus próprios dados locais.

## Se não conectar pelo Wi-Fi

Confirme que o computador está ligado, o serviço está aberto e os aparelhos
estão na mesma rede. Redes de convidados podem impedir comunicação entre aparelhos.
O Windows precisa permitir o acesso local do Java. O utilitário
**Configurar Rede do Bar.cmd** solicita a confirmação administrativa do Windows
para isso e preserva o perfil da rede. Não abra o PostgreSQL ou o roteador à internet.

Sem internet externa, o atendimento local continua. Sem Wi-Fi ou sem computador,
novos pedidos não são confirmados. Use **Conferir envio** depois de reconectar.
Não limpe os dados/desinstale o aplicativo enquanto houver envio pendente.

## Atualizações

Instale a nova versão por cima da anterior, com a mesma assinatura. O código
Android está em `apps/android` e pode ser aberto no Android Studio. A chave de
atualização fica na pasta privada `.tools/android-signing`; preserve essa pasta.
