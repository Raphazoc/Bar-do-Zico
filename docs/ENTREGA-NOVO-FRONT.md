# Entrega — novo front, estoque e APK Android

Atualização mais recente: [cadastros, caixa e vendas por mesa](AJUSTES-CAIXA-VENDAS-CADASTROS.md).

Atualização posterior: [exclusões e atendimento direto](AJUSTES-ATENDIMENTO-DIRETO.md)
substituem a recuperação de mesas e o fluxo de preparo descritos neste registro original.

Data: 20/10/2026. Implementação autorizada pelo proprietário após as prévias
visuais. A versão funcional abre diretamente em Mesas, sem tela de login.

## Entregue

- Navegação principal com Mesas, Caixa, Funcionários, Estoque e Cardápio.
- Mesas com busca, filtro, cadastro/edição em janelas próprias, exclusão com
  confirmação e recuperação; atendimento separado da lista.
- Consumo confirmado separado do rascunho, opcionais, envio, acompanhamento de
  preparo e passagem da mesma conta para o caixa. Cozinha/bar acessíveis por Mesas.
- Caixa organizado em contas, pagamento, movimentos, fechamento e vendas,
  preservando as regras de descontos, parcelas, troco, compensações e correções.
- Funcionários sem credenciais de entrada, edição, ativação, perfis e permissões.
  A identificação do responsável pelo aparelho é voluntária e não comprova identidade.
- Estoque por produto com saldo/mínimo, entradas, saídas, contagem, histórico,
  alertas, baixa atômica pelo pedido e retorno por cancelamento quando aplicável.
- Disponibilidade efetiva no cardápio e no seletor de pedidos, incluindo saldo
  positivo para produto controlado. Produtos anteriores permanecem sem controle
  numérico até a conferência manual, sem inferir estoque de vendas antigas.
- APKs release assinados de operação e treinamento, com dados separados,
  configuração do endereço, validação TLS/CA local e mensagens de reconexão.
- Rascunhos e comandos sem confirmação persistidos no armazenamento do aparelho,
  com conferência idempotente antes de permitir novo envio. A confirmação de
  pedido limpa o rascunho, inclusive ao conferir uma resposta perdida.

## Verificação

| Camada | Resultado |
|---|---|
| Interface | TypeScript/Vite compilados; 22 casos Playwright aprovados em cada modo, computador e Android emulado |
| API | 29 testes Java aprovados em operação e treinamento, com PostgreSQL real e credencial restrita |
| Banco | V13/V14 aplicadas e validadas; 37 tabelas com Flyway, 87 FKs e quatro views |
| Preservação | Contagens e hashes das colunas anteriores das 35 tabelas fora do Flyway iguais antes/depois, nos dois ambientes |
| Backup | Cópia real restaurada em banco temporário antes da atualização; nova cópia das 37 tabelas restaurada/conferida após a atualização |
| Windows | Pacote com runtime Java 25.0.4.1 / JavaFX 25.0.1+3 abriu Mesas sem login por HTTPS local |
| APKs | Operação e treinamento compilados e assinados; assinaturas verificadas por apksigner; lint aprovado |
| Android | Dois APKs release instalados no emulador API 35, conectados por HTTPS ao IP local; falha de conexão e reconexão verificadas |
| Rede Windows | Regras TCP 8443/18443 aplicadas e conferidas, restritas ao Java 25 e LocalSubnet em Ethernet 2; perfil Public preservado |

Os sete novos testes Java complementam os 22 anteriores: abertura sem login,
proteção de origem, cadastro/edição sem credenciais, seleção de perfil,
estoque/idempotência, saldo direto/histórico protegidos, concorrência sem venda
além do saldo, rollback integral do pedido e cancelamento com perda/retorno físico.

Os testes de tela verificam cadastro/edição/exclusão/recuperação de mesa,
proteção de atendimento aberto, pedido/consumo/estoque, pagamento com troco e
fechamento, funcionários/perfis, produtos e cardápio, queda de conexão e
resposta perdida sem cadastro duplicado. Todos os dados desses testes ficam
em bancos descartáveis. Não foram criadas vendas ou mesas fictícias na operação.

As capturas em `output/interface/novo-front/` incluem o executável Windows,
os dois APKs, erro de conexão e as telas testadas por Playwright.
Os logs locais de validação ficam em `.tools/validacao-novo-front-*.log` e
`.tools/android-validacao.json`; não são arquivos para publicar.

## Arquivos e uso

- Windows: abrir **Bar do Zico - Treinamento** na Área de Trabalho.
- Android: `output/android/Bar-do-Zico-Treinamento.apk` e `Bar-do-Zico.apk`.
- Instruções: [COMO-USAR-NOVO-FRONT.md](COMO-USAR-NOVO-FRONT.md) e
  [COMO-INSTALAR.md](../output/android/COMO-INSTALAR.md).
- Código: `apps/web/src/Front.tsx`, `apps/api` e `apps/android`.

O Android foi construído com Java/WebView, AGP 8.13.2, Gradle 8.13, SDK 35,
API mínima 26 e JDK 21 somente para o build. O computador/API continuam Java 25.
CA pública embutida no APK; chaves TLS/assinatura e senhas ficam privadas em
`.tools`. A chave de assinatura é persistente e deve ser preservada para atualizar.

## Pendências de piloto

Instalar e testar no Android físico do proprietário, conferir cobertura e
isolamento do Wi-Fi do bar, testar queda da internet externa mantendo a LAN,
reinício completo do Windows sem internet, exercício acompanhado e escolha da
segunda pasta externa de backups. O emulador não substitui esses testes físicos.

Não estão incluídos emissão fiscal, cobrança bancária/maquininha automática,
impressão, receitas/ingredientes/complementos de estoque ou confirmação de
pedidos sem contato com o computador. As regras financeiras existentes foram
preservadas; esta entrega ainda precisa da homologação prática do proprietário.
