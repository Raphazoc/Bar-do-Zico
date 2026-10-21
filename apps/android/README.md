# Android — Bar do Zico

Aplicativo Java com WebView, projeto importável no Android Studio. Duas variantes:
`operacao` e `treinamento`, com identificadores, armazenamento e nomes separados.
As telas React são servidas pela API Java local do bar. Não há tela de login.
O **PC é administrador**; o **APK é de atendimento**, com Mesas e Cardápio para
consultar, **Abrir mesa**, ver consumo e adicionar produtos. O menu também tem
**Configurações**, limitadas ao aplicativo e à conexão. Não recebe
caixa, exclusões, equipe, estoque administrativo ou relatórios.

Versão Android **0.2.3, código 4**. Instalar por cima da anterior, preservando
assinatura e dados. O executável do PC permanece na versão **0.2.2**.
A mesma API também pode ser hospedada no Azure, depois
de publicar o piloto e implementar a autorização dos aparelhos. A assinatura
Azure foi consultada nesta revisão e ainda não contém recursos. Não há servidor
de nuvem ou sincronização local/nuvem em funcionamento.

Build: AGP 8.13.2, Gradle 8.13, compile/target SDK 35, mínimo API 26. A compilação
Android usa o JDK 21 existente, selecionado só para o processo. O computador/API
continuam usando Java 25. Nenhuma configuração global do Java é alterada.

```powershell
.venv/Scripts/python.exe scripts/preparar_android.py
.venv/Scripts/python.exe scripts/compilar_android.py
```

Os comandos acima são executados na raiz `E:\Bar`. SDK, Gradle, caches e chave
ficam em `.tools`. Os APKs assinados são copiados para `output/android`.
Para abrir no Android Studio, selecione esta pasta e use JDK 21 em Gradle JDK.
O wrapper fixa a versão do Gradle e verifica o SHA-256 da distribuição.

O build copia somente a CA pública local para `res/raw/bar_ca.cer`. Não embutir
senha do banco, chave privada TLS ou chave de assinatura no APK. A assinatura
release usa as variáveis `BAR_ANDROID_KEYSTORE` e `BAR_ANDROID_KEY_PASSWORD`,
preparadas pelo script sem colocar a senha na linha de comando. Preserve a chave
e sua senha em backup privado para permitir atualizações do aplicativo.

Quando o piloto Azure estiver publicado, o endereço HTTPS real pode ser salvo
por **Menu → Configurações → Conexão com o servidor**, sem recompilar.
Para torná-lo o padrão de novas instalações,
forneça as variáveis abaixo antes de compilar (substitua pelo hostname real):

```powershell
$env:BAR_ANDROID_URL='https://seu-app.azurecontainerapps.io'
$env:BAR_ANDROID_TRAINING_URL='https://seu-app-treino.azurecontainerapps.io'
.venv/Scripts/python.exe scripts/compilar_android.py
```

O APK confia nos certificados públicos do sistema Android para HTTPS Azure. A
CA local continua incluída para preservar o uso em rede local.

Se uma versão anterior tiver salvo o hostname provisório do Azure, o aplicativo
o descarta automaticamente e volta ao endereço local padrão. Um endereço real
de nuvem é preservado quando há erro de conexão ou certificado. O modo local
continua funcionando com o computador e a API ligados.

O WebView aceita apenas navegação na origem configurada, bloqueia HTTP, conteúdo
misto, arquivos locais e acesso externo; cancela erros TLS. JavaScript e DOM
Storage são necessários para o React. Não existe ponte JavaScript nativa.
O aplicativo não exporta os dados locais para backup em nuvem ou transferência.

Antes do primeiro carregamento, o WebView acrescenta
`BarDoZicoAndroid/0.2.3` ao User-Agent. O marcador acompanha navegação, recursos
e fetch/XHR e serve apenas para reduzir acesso no servidor, nunca para conceder
administração. No modo local V17, a API ignora cookies administrativos antigos
ao determinar o perfil: só a conexão de loopback real do PC, sem esse marcador,
recebe gestão. A conexão pela rede e o APK usam uma identidade interna limitada
a atendimento. Não há escolha de responsável nem opção de virar administrador.
O acesso nativo às configurações usa `bardozico://settings`, tratado pelo próprio
APK para abrir **Configurações do aplicativo → Conexão com o servidor**. Essa
opção não abre a gestão do PC nem altera o perfil de atendimento. O botão
**Conexão** saiu do topo; em falhas de conexão há um atalho **Configurações**
para corrigir o endereço mesmo sem carregar a tela web.

Nova abertura de caixa no PC começa em zero, editável; valores existentes são
preservados. Mais detalhes: [perfis PC/celular](../../docs/PERFIS-PC-CELULAR.md).

Referências oficiais: [WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview),
[certificados locais](https://developer.android.com/privacy-and-security/security-config),
[compatibilidade do AGP](https://developer.android.com/build/releases/agp-8-13-0-release-notes).
