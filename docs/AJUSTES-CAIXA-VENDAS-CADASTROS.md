# Cadastros, caixa e vendas por mesa — V16

Atualização de 20/10/2026, conforme as escolhas confirmadas pelo proprietário.

## Como usar

- **Mesas → Locais do bar → Excluir:** se houver mesas, escolha outro local ativo.
  Todas são transferidas, inclusive ocupadas; conta, consumo e situação continuam
  iguais. Sem outro local ativo, cadastre um antes. A confirmação mostra a quantidade.
- **Funcionários → Excluir:** retira o funcionário das listas e seletores e revoga
  suas sessões/liberações de mesas. Os lançamentos antigos mantêm o autor original.
  Não é possível restaurar o cadastro, excluir o responsável em uso ou deixar o
  bar sem administrador. Ativar/Desativar continua disponível como outra operação.
- **Caixa → Valores deste caixa:** mostra dinheiro previsto na gaveta e recebimentos
  líquidos de vendas em cartões, Pix, dinheiro e demais formas. Abertura e entradas
  manuais não são vendas. **Ocultar valores / Mostrar valores** lembra a escolha
  neste aparelho; operação e treinamento têm preferências separadas.
- **Caixa → Vendas:** abre no dia atual e mostra uma linha por atendimento encerrado,
  com mesa, data/hora e total final. A mesma mesa pode aparecer várias vezes.
  Pagamentos parciais não duplicam linhas. Datas usam o fuso do bar. Há paginação
  de 50 registros e filtros de período; os relatórios anteriores ficam recolhidos.
- **Configurações → Histórico de alterações:** substitui os códigos técnicos por
  textos como “Pagamento registrado”, conforme a permissão de consulta.
- **Perfis e permissões:** opções agrupadas, nomes simples e sem preparo/entrega
  ou controles de sessão na tela. Códigos antigos são preservados ao editar perfis.

O cabeçalho e a navegação compartilham um bloco fixo de altura automática.
Mudanças de tela voltam ao início; fechar janelas restaura o foco sem deslocar
a página. Margens de rolagem mantêm campos e seus rótulos abaixo da navegação.

## Dados e contratos

V16 acrescenta `users.deleted_at`, proteção contra restauração e índices para
vendas encerradas e pagamentos/devoluções por sessão. Não modifica dados anteriores
nem as migrações V1–V15. Funcionários excluídos permanecem internamente para
referenciar os históricos; locais são removidos após a transferência das mesas.

Novos comandos `area.delete` e `staff.delete` usam as permissões existentes,
transação, auditoria e idempotência. Exclusão de locais/cadastro de mesas e
mudanças de equipe/perfis são serializados por unidade. A API revalida o gestor
e o administrador restante para evitar exclusões concorrentes inconsistentes.

`/api/state` acrescenta `cashSummaries`. `GET /api/reports/sales` consulta todas
as contas encerradas do período, com paginação e nome histórico da mesa; não usa
as listas operacionais limitadas. Valores monetários continuam strings decimais.
Detalhes em [API.md](API.md) e [banco/README.md](banco/README.md).

## Verificação e instalação

- 42 testes Java aprovados com PostgreSQL real e credencial `bar_app`.
- 38 cenários de interface aprovados entre computador e formato de celular;
  rodada adicional no modo Treinamento também aprovada.
- Cenários incluem transferência de mesa ocupada, exclusões, permissões/unidade,
  concorrência, reenvios, histórico após exclusão de mesa, reabertura, datas no
  fuso do bar, 554 atendimentos no período e saldo com 600 pagamentos mesmo com
  lista operacional limitada a 500.
- Migração V15 → V16 aprovada com dados existentes, comparando hashes e checksums.
- Backups de operação e treinamento restaurados/conferidos antes da atualização;
  nova cópia da operação restaurada/conferida depois da V16.
- V16 aplicada/validada nos dois ambientes; hashes das colunas anteriores das
  36 tabelas fora do Flyway iguais antes/depois, incluindo usuários e vendas.
- HTTPS e novos contratos conferidos nos dois serviços. JavaFX 25 validado com
  consultas de Mesas, resumo de caixa, vendas e geometria da navegação; o executável
  instalado também abriu a tela inicial sem login.

Capturas com dados descartáveis: `output/interface/ajustes-v16/`. Registros locais
de validação: `.tools/validacao-v16-*.log`, `.tools/validacao-migracao-v16.log` e
`.tools/native-v16.txt`. São evidências de desenvolvimento; testes em Android
físico e o piloto do proprietário continuam pendentes.

Para carregar a atualização, use **Reconectar** na barra verde do Windows ou
feche e reabra o programa. No Android, feche/reabra o aplicativo. Não é necessário
reinstalar o APK: a interface é servida pelo computador. Os serviços de operação
e treinamento permanecem separados e funcionando em HTTPS na rede local.

Guia atualizado: [COMO-USAR-NOVO-FRONT.md](COMO-USAR-NOVO-FRONT.md).
