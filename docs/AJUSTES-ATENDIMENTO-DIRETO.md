# Exclusões e atendimento direto — 20/10/2026

Solicitação implementada no Windows e na interface carregada pelo APK Android.

- **Estoque → Excluir:** botão ao lado de Editar, com confirmação e cancelamento.
  Retira o produto do cadastro, cardápio e seletor do atendimento. O registro
  interno permanece para preservar vendas, movimentações e cancelamentos antigos;
  não pode ser restaurado por edição. A quantidade histórica não é zerada.
- **Mesas → Excluir:** exclusão física e permanente do cadastro, com confirmação.
  O número fica livre. Conta aberta impede a exclusão. Contas e autorizações antigas
  preservam o nome em `table_number_snapshot`; autorizações são revogadas.
  A recuperação de mesas foi retirada. As oito mesas antes arquivadas no treinamento
  foram removidas, mantendo os registros dos atendimentos.
- **Atendimento:** Adicionar produto → escolher quantidade/opcionais →
  **Adicionar à mesa**. O consumo e o estoque são salvos imediatamente.
  Sem Enviar pedido, Acompanhar pedido, preparo ou entrega na interface.
  **Remover** corrige um lançamento com motivo; **Ir para o caixa** abre o recebimento.
  Rascunhos antigos são apresentados para decisão manual, sem perda silenciosa.

## Verificação

| Verificação | Resultado |
|---|---|
| TypeScript/Vite | Compilação aprovada |
| API/PostgreSQL | 31 testes aprovados, incluindo histórico, permissões, unidade e idempotência |
| Interface | 26 casos aprovados em operação e 26 em treinamento, computador e formato de celular |
| Atualização de banco preenchido | V14 → V15 com conta fechada, mesa arquivada, autorização e estoque anteriores, aprovada |
| Instalação local | V15 aplicada/validada nos dois bancos; ambas as APIs HTTPS servem o mesmo pacote atual |
| Preservação | Hashes e contagens das 36 tabelas fora do Flyway conferidos, permitindo apenas as exclusões antigas de mesas e desvínculos previstos |
| Backups | Operação e treinamento copiados e restaurados/conferidos antes; nova cópia da operação conferida após |
| Windows | Executável existente renderizou Mesas sem login no JavaFX 25 pelo HTTPS do treinamento |

O teste de resposta perdida confirma a adição direta sem duplicar consumo ou
baixa de estoque. A exclusão de produto conserva a venda e permite cancelar um
lançamento anterior; o teste verifica o retorno aplicável ao estoque. A exclusão
de mesa mantém pedidos/pagamentos, libera o número e rejeita restauração do ID antigo.

Durante esta própria entrega, a V15 teve o preenchimento dos nomes históricos
movido para depois das alterações de estrutura: contas existentes disparam
verificações diferidas que impediam o DDL posterior na mesma transação.
A tentativa no treinamento foi revertida automaticamente. O banco de operação,
que já recebera a V15 sem contas, teve somente seu checksum V15 alinhado pelo
comando oficial [Flyway repair](https://documentation.red-gate.com/flyway/reference/commands/repair).
V1–V14 e seus checksums permaneceram inalterados. A ordem corrigida foi testada
em banco preenchido antes da aplicação final no treinamento. Não incluir repair
na inicialização normal; migrações entregues continuam imutáveis.

## Uso e evidências

Feche e reabra o programa pelo atalho **Bar do Zico - Treinamento** ou use o botão
**Reconectar** da barra verde do Windows para carregar a interface atualizada.
No Android, feche/reabra o aplicativo. A interface vem da API local: esta alteração
não exige reinstalar o APK. O teste físico no Android do proprietário permanece
pendente; a rodada desta atualização usou telas emuladas no Playwright.

- Guia atualizado: [Como usar](COMO-USAR-NOVO-FRONT.md).
- Capturas: `output/interface/ajustes-v15/`.
- Teste de atualização: `scripts/validar_migracao_v15.py`.
- Logs privados: `.tools/validacao-exclusoes-direto-final.log` (31 testes API),
  `.tools/validacao-exclusoes-telas-final.log`, `.tools/validacao-exclusoes-treinamento.log`
  e `.tools/validacao-migracao-v15.log`.

Os testes automatizados usaram bancos descartáveis. Não foram criadas vendas,
mesas ou produtos fictícios nos bancos persistentes durante esta atualização.
