# Bar do Zico — computador e Android

Programa Windows em Java 25, API Java local e PostgreSQL. O aplicativo Android
instalável usa as mesmas telas pelo Wi-Fi do bar. A interface abre diretamente
em **Mesas**, sem tela de login, conforme solicitado pelo proprietário.

## Abrir e testar

Na Área de Trabalho, dê dois cliques em **Bar do Zico - Treinamento** para
praticar com dados separados. Para a operação, abra **Bar do Zico**.
Os atalhos iniciam os serviços e abrem a janela. Fechar a janela mantém o
atendimento dos celulares ativo. Se precisar dos atalhos, execute
**Criar Atalhos.cmd** em `E:\Bar`.

O executável portátil fica em `output/app/Bar do Zico/Bar do Zico.exe`, com Java
incluído. Use os atalhos/inicializadores para preparar também banco e API.
Não há instalador MSI nesta entrega.

- [Como usar o programa](docs/COMO-USAR-NOVO-FRONT.md)
- [Instalar no Android](output/android/COMO-INSTALAR.md)
- [APK de treinamento](output/android/Bar-do-Zico-Treinamento.apk)
- [APK da operação](output/android/Bar-do-Zico.apk)
- [Resultados e limites da entrega](docs/ENTREGA-NOVO-FRONT.md)

## Interface e funções

O menu principal tem **Mesas, Caixa, Funcionários, Estoque e Cardápio**.
No Android, o botão **Menu** dá acesso às áreas; registros usam blocos legíveis.

- Mesas: busca, situação, adicionar, editar, excluir permanentemente com confirmação e reutilizar o número.
- Locais: edição e exclusão com transferência das mesas, inclusive ocupadas.
- Atendimento em tela própria: consumo, produtos/opcionais salvos diretamente
  na mesa e passagem da mesma conta para o caixa, sem preparo ou entrega.
- Caixa: dinheiro em caixa e vendas líquidas com opção de ocultar, descontos,
  seis formas de pagamento, parcelas, troco, entradas/retiradas e fechamento.
- Vendas: cada atendimento encerrado por mesa, data/hora e valor, com período
  e paginação; auditoria separada em Configurações, com nomes simples.
- Funcionários: cadastro/edição sem e-mail ou senha, ativação, exclusão definitiva
  do cadastro com histórico preservado, perfis e permissões agrupadas.
  O responsável do aparelho pode ser escolhido voluntariamente em Configurações.
- Estoque: quantidade, mínimo, busca/filtros/ordenação, entradas/saídas/ajustes,
  histórico, alertas, exclusão de produtos e baixa ao adicionar à mesa, com devolução quando aplicável.
- Cardápio: preço, quantidade e disponibilidade no mesmo cadastro do estoque.
- Configurações: endereços/QR para Android e backups com restauração de conferência.

Os dados existentes foram preservados. Produtos antigos continuam sem controle
numérico até a conferência manual. Sem login, os perfis organizam as funções do
aparelho e não comprovam a identidade de quem o utiliza.

Internet externa não é necessária para o uso local depois da instalação.
O computador e a rede local precisam funcionar. Sem contato com o computador,
novos envios ficam bloqueados e comandos sem confirmação podem ser conferidos
com a mesma chave, sem duplicar o lançamento. Não há operação autônoma do
celular sem Wi-Fi/servidor, emissão fiscal, cobrança bancária automática,
impressão integrada, delivery ou controle de receitas/ingredientes nesta entrega.

## Arquivos para abrir nesta máquina

| Arquivo | Uso |
|---|---|
| `Iniciar Bar.cmd` | Abre o programa e mantém o último modo da operação |
| `Iniciar Bar na Rede.cmd` | Operação em HTTPS para computador e celulares |
| `Iniciar Treinamento.cmd` | Abre os exercícios e mantém seu último modo |
| `Treinamento no Android.cmd` | Treinamento com HTTPS local |
| `Parar Bar.cmd` / `Parar Treinamento.cmd` | Encerra apenas o serviço correspondente |
| `Configurar Rede do Bar.cmd` | Regras locais do Java com confirmação administrativa do Windows |
| `Verificar Rede.cmd` | Diagnóstico de rede sem alterar configurações |
| `Fazer Backup.cmd` | Cópia real com restauração em banco temporário |
| `Configurar Backups.cmd` | Segunda pasta de cópias, fora do disco principal |
| `Conferir Backup.cmd` | Verificação de uma cópia já existente |
| `Criar Atalhos.cmd` | Recria os atalhos da Área de Trabalho |

Os APKs de operação e treinamento têm nomes e dados locais separados, aceitam
Android 8 ou mais recente e incluem a CA pública desta instalação. A instalação
no aparelho físico e o piloto no Wi-Fi do bar ainda devem ser acompanhados.

## Desenvolvimento

- Computador/API: Java 25, JavaFX 25.0.1, Spring Boot 3.5.16 e Spring JDBC.
- Interface: React, TypeScript e Vite; arquivos servidos localmente pela API.
- Banco: PostgreSQL 17.11, 16 migrações versionadas Flyway, 37 tabelas incluindo
  o histórico, 87 chaves estrangeiras e quatro views.
- Android: Java/WebView, AGP 8.13.2, Gradle 8.13, compile/target SDK 35, API mínima 26.
  JDK 21 só para a compilação Android, sem alterar o Java 25 do computador/API.

Para compilar o Windows, execute na raiz do projeto:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/compilar.ps1 -Empacotar
```

Para compilar os APKs:

```powershell
.venv/Scripts/python.exe scripts/preparar_android.py
.venv/Scripts/python.exe scripts/compilar_android.py
```

Projeto importável no Android Studio em `apps/android`. A chave de assinatura
privada permanece em `.tools/android-signing`, fora do controle de versão; é
necessário preservá-la para atualizar o aplicativo. JDKs, SDK, Gradle, caches e
credenciais locais também ficam em `.tools`, sem mudança global do Windows.

Os testes de API/telas criam bancos descartáveis e preservam os dados do bar:

```powershell
.venv/Scripts/python.exe scripts/validar_aplicacao.py --telas
.venv/Scripts/python.exe scripts/validar_aplicacao.py --treinamento --telas
.venv/Scripts/python.exe scripts/validar_migracao_v15.py
.venv/Scripts/python.exe scripts/validar_migracao_v16.py
```

Use o JDK 25 selecionado por `scripts/ambiente.ps1` ou `scripts/java_tools.py`
para executar os testes Java. Há 42 testes da API e 38 casos de interface entre
computador e Android emulado. As verificações do pacote APK/Windows e do banco
estão detalhadas no registro da entrega.

Referências: [arquitetura e retomada](docs/PLANO-DE-TRABALHO.md),
[contratos da API](docs/API.md), [banco](docs/banco/README.md),
[projeto Android](apps/android/README.md).
