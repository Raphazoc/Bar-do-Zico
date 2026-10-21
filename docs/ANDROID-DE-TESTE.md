# Celular Android virtual no computador

Na Área de Trabalho, dê dois cliques em **Bar do Zico - Android de Teste**.
Também é possível abrir **Iniciar Android de Teste.cmd**, na pasta `E:\Bar`.

O atalho prepara o serviço de treinamento, abre a janela de um Android virtual
e instala/atualiza **Zico - Treinamento**. Aguarde a inicialização: a primeira
abertura pode demorar até três minutos. Não precisa instalar Android Studio.

1. Clique na tela usando o mouse, como se estivesse tocando no celular.
2. Abra **Mesas → Adicionar mesa**, informe o nome/número e salve.
3. Use **Abrir mesa → Adicionar produto**, escolha a **Categoria** e depois o
   **Produto** daquela categoria para testar o atendimento.
4. No PC, abra **Bar do Zico - Treinamento** para conferir os mesmos dados.
5. Para configurar o servidor, use **Menu → Configurações → Conexão com o servidor**.

O teclado do computador pode ser usado para escrever. Para rolar, use a roda do
mouse ou arraste a tela. Feche a janela do Android quando terminar; o serviço
de treinamento continua disponível. Reabrir o atalho mantém os dados do Android.

O atalho instala somente o APK de treinamento e usa seu banco separado.
Os cadastros feitos nesse ambiente são exercícios, sem alterar a operação.
Não há limpeza automática de dados, mesas ou envios pendentes.

## Ambiente preparado

Android 15/API 35, dispositivo virtual `BarZicoTest`, aceleração WHPX, 4 núcleos e
4 GB de memória. SDK, imagem Android e armazenamento do dispositivo ficam em `.tools`.
Não há alteração do Java 25 do programa Windows ou do perfil Público da rede.
Log do emulador: `.tools/android-teste.log`.

Ambiente conferido em 21/10/2026: janela Android visível, inicialização concluída,
APK de treinamento instalado e aberto. O formulário Adicionar mesa foi
conferido no Android virtual, com número/nome, local, Salvar mesa e Cancelar.

Se o aplicativo não conectar, confira se o treinamento iniciou e copie no PC o
endereço em **Configurações → Acesso pelo Android**. Informe esse endereço nas
configurações do APK. O teste no emulador não substitui a conferência no celular físico.

## Desempenho ajustado

- Renderização SwiftShader para compatibilidade; o modo `host` travou no driver NVIDIA deste PC.
- Memória do Android aumentada de 1,5 GB para 4 GB, mantendo 4 núcleos.
- Tela de 720 × 1560 pixels, densidade 320, para reduzir a carga gráfica.
- Animações do Android em 0,5×, com transições mais curtas.
- Inicialização completa, sem restauração de snapshots gráficos.

Cada abertura inicializa o Android por completo. Aplicativos e
dados persistentes são mantidos. O inicializador também evita a janela de
consentimento de relatório de erro que estava bloqueando a abertura do emulador,
usando `-no-metrics -crash-report-mode never`; nenhum relatório é enviado.
O atalho continua o mesmo. Cópias da configuração anterior ficam em
`.tools/android-desempenho-backups`; o armazenamento do Android não é apagado.

Referências oficiais: [aceleração gráfica e WHPX](https://developer.android.com/studio/run/emulator-acceleration)
e [Quick Boot/snapshots](https://developer.android.com/studio/run/emulator-snapshots).

Conferência inicial após o ajuste: o Android informou renderização OpenGL pela RTX 3060,
4 núcleos, aproximadamente 4 GB de RAM e tela 720 × 1560/densidade 320.
As três escalas de animação foram confirmadas em 0,5× e o APK de treinamento
abriu normalmente. Não foi realizado benchmark comparativo de FPS ou tempo de boot.

Ao reabrir, o Windows registrou falha do emulador em `nvoglv64.dll`; o perfil
foi ajustado para SwiftShader, mantendo memória, núcleos e resolução. Não foram
alterados drivers do Windows, dados do APK ou dados do bar.
