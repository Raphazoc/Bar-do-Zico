# Android — Bar do Zico

Aplicativo Java com WebView, projeto importável no Android Studio. Duas variantes:
`operacao` e `treinamento`, com identificadores, armazenamento e nomes separados.
As telas React são servidas pela API Java local do bar. Não há tela de login.

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

O WebView aceita apenas navegação na origem configurada, bloqueia HTTP, conteúdo
misto, arquivos locais e acesso externo; cancela erros TLS. JavaScript e DOM
Storage são necessários para o React. Não existe ponte JavaScript nativa.
O aplicativo não exporta os dados locais para backup em nuvem ou transferência.

Referências oficiais: [WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview),
[certificados locais](https://developer.android.com/privacy-and-security/security-config),
[compatibilidade do AGP](https://developer.android.com/build/releases/agp-8-13-0-release-notes).
