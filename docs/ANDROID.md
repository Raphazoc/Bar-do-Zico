# Conectar o Android ao Bar do Zico

**Guia atual:** o programa abre sem login e há APK instalável. Consulte
[Como usar o novo front](COMO-USAR-NOVO-FRONT.md) e
[Instalar o Android](../output/android/COMO-INSTALAR.md). O roteiro abaixo
registra a configuração anterior pelo navegador e não exige mais a entrada
por usuário/senha na versão atual.

O APK atual é **0.2.3 (código 4)**, de atendimento. Instale por cima da versão
anterior, com a mesma assinatura. O executável do PC continua em **0.2.2**, como
administrador. No celular, toque em **Abrir mesa** para iniciar um atendimento.
Para ajustar o endereço, use **Menu → Configurações → Conexão com o servidor**;
essa configuração pertence ao aplicativo e não dá acesso à gestão do bar.
Se não houver conexão, toque em **Configurações** na tela de erro. O botão
**Conexão** não fica mais no topo.

Use o APK de `output/android`, instalado pelo aplicativo Arquivos do Android.
Ele já contém a CA pública da instalação e não exige instalar certificado
manualmente no sistema Android. O roteiro de certificados abaixo é necessário
apenas se você escolher usar o Chrome em vez do APK.

Azure foi confirmado para futuro acesso por 4G/outra rede, mas a conta ainda não
tem servidor criado. [Veja a revisão e as fases pendentes](REVISAO-REDE-E-AZURE.md).
Os endereços residenciais abaixo funcionam no mesmo roteador do computador.

## Começar pelo treinamento

1. No computador, abra `E:\Bar` e dê dois cliques em **Treinamento no Android.cmd**.
   **Iniciar Treinamento.cmd** e o atalho da Área de Trabalho também iniciam
   HTTPS. O inicializador atualiza automaticamente um serviço HTTP antigo.
2. Aguarde o banco de treinamento e o serviço iniciarem.
3. Execute **ferramentas/Verificar Rede.cmd**. Ele mostra os endereços disponíveis e não
   altera a configuração do Windows.
4. Escolha o endereço da rede do bar, por exemplo `https://192.168.0.8:18443`.
   Use o endereço informado na sua máquina; ele pode mudar com o roteador.
   Não use `127.0.0.1` no celular: esse endereço significa o próprio aparelho.

O treinamento usa a porta **18443**. O ambiente real usa **8443**.
Os bancos e aplicativos dos dois ambientes são separados. Não há login no modo local.

O proprietário confirmou uma conexão residencial com perfil **Público** no Windows.
O utilitário **ferramentas/Configurar Rede do Bar.cmd** preserva o perfil atual do Windows,
inclusive **Público**, e aplica regras restritas ao Java, à interface confirmada
e à sub-rede local. O Windows pede
confirmação de administrador. Execute esse utilitário na rede residencial
confirmada; ele verifica a interface antes de aplicar.

Em **Configurações → Acesso pelo Android**, o administrador também pode consultar
o endereço e o QR Code do ambiente iniciado em rede. A conexão com gateway é priorizada sobre
adaptadores virtuais. No programa Java, o botão **Certificado Android** abre a
pasta que contém somente a cópia pública a transferir ao celular.

## Preparar o certificado no celular

O certificado permite que o navegador reconheça o computador do bar. Copie
somente o arquivo público `E:\Bar\output\android\bar-ca.cer` para a pasta Downloads
do Android, por exemplo usando um cabo USB. Não copie a pasta `.tools/tls`
inteira: ela também contém chaves privadas e senha do computador.

A cópia pública foi preparada em `output/android/`. O original permanece em
`.tools/tls/bar-ca.cer`; os dois identificam a mesma autoridade local.

No Android, procure nas Configurações por **Instalar certificado** ou
**Criptografia e credenciais**. O caminho costuma ficar em **Segurança e
privacidade → Mais configurações de segurança**; os nomes variam por fabricante
e versão. Para confiar no HTTPS deste sistema, escolha **Certificado de CA**
quando essa opção estiver disponível, selecione `bar-ca.cer` e confirme com
o PIN do próprio aparelho. Essa é uma configuração de confiança, não a senha
do sistema. Não selecione a opção de certificado de Wi-Fi empresarial.

A documentação do Google descreve a área de credenciais e observa diferenças
entre versões do Android: [adicionar e remover certificados](https://support.google.com/pixelphone/answer/2844832?hl=pt-BR).
O caminho para instalar uma CA no Android 11 ou posterior também aparece na
[seção de certificado raiz do guia técnico do Google](https://support.google.com/device-usage-study-help/answer/15713321?co=GENIE.Platform%3DAndroid&hl=en).
Aqui esse caminho foi adaptado para selecionar o certificado público do bar.
O procedimento deve ser confirmado no modelo do aparelho durante o piloto.
Não é necessário desativar as proteções do Android ou do Chrome.

## Abrir o sistema

1. Conecte o Android ao mesmo Wi-Fi do computador.
2. Abra o Chrome e digite o endereço HTTPS do treinamento, com a porta `18443`.
3. Confira se a tela mostra **TREINAMENTO** antes de fazer qualquer lançamento.
4. A tela Mesas abre diretamente, sem usuário ou senha. No APK, use
   **Menu → Configurações → Conexão com o servidor** para salvar o endereço
   mostrado em **Configurações → Acesso pelo Android** no computador.
5. Faça o roteiro de [PILOTO.md](PILOTO.md).

Se aparecer erro de certificado, confira o endereço, a data/hora dos aparelhos
e, no navegador, a instalação da CA. No APK, confirme que a versão atual foi
gerada com a CA desta instalação. Não ignore o erro de certificado.

## Se o celular não conseguir abrir

- Confirme que o computador está ligado e que o serviço está no modo de rede.
- Confira se o endereço usa a porta correta e o IP atual do computador.
- Uma rede de convidados pode impedir comunicação entre os aparelhos.
- O Firewall do Windows precisa permitir a porta utilizada na conexão residencial do bar.
  **ferramentas/Configurar Rede do Bar.cmd** prepara as regras 8443 e 18443 sem desativar
  o firewall. **ferramentas/Verificar Rede.cmd** continua sendo somente de leitura.

O teste feito no próprio computador não comprova o acesso pelo Android.
O piloto só pode ser marcado como concluído depois de testar no aparelho real.
