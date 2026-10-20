# Validação da entrega inicial

Data: 19/10/2026.

| Verificação | Resultado |
|---|---|
| PostgreSQL real | 17.11, validado nativamente no Windows e em container Linux/WSL 2 |
| Flyway Community | 13.10.3 |
| Migração de database vazio | 10 migrações aplicadas com sucesso |
| Validação de checksums | Aprovada |
| Reexecução de migrate | Nenhuma migração reaplicada |
| Estrutura | 33 tabelas do modelo, 81 FKs e 4 views |
| Catálogo inicial | 19 permissões; nenhum usuário/unidade fictício |
| Testes de integração | 31 aprovados no ambiente nativo e 31 aprovados no Docker |
| Docker Desktop | 4.95.0, instalado e operacional |
| Docker Engine/CLI e Compose | 29.8.2 e 5.6.0 |
| Container PostgreSQL | gestao-bar-db-1: running/healthy, 127.0.0.1:5432 |
| Concorrência real | Recebimentos, estornos, fechamento de caixa e pedido durante fechamento de conta |
| Credencial bar_app | Criada e conferida nos dois clusters; histórico Flyway e criação de tabela negados |
| Artefato de modelagem | DBML gerado do schema validado |

Os testes foram executados em databases descartáveis, criados pelo runner e
excluídos ao final. O database local de desenvolvimento também recebeu as dez
migrações e permaneceu sem dados fictícios.

O PostgreSQL usado para validação foi iniciado temporariamente, restrito ao
localhost. Seus binários, dados e credenciais locais ficaram em `.tools/`, fora
do versionamento; o processo foi encerrado após a conferência. A execução
reproduzível prevista para desenvolvimento está em `compose.yaml`.

Após a reinicialização do Windows pelo proprietário, WSL 2 foi confirmado e a
instalação do Docker foi concluída. O Compose subiu PostgreSQL e o Flyway em
container aplicou e validou as dez migrações. A suíte também foi executada contra
o PostgreSQL desse container em databases descartáveis, com os 31 testes aprovados.

O database Docker de desenvolvimento tem 33 tabelas, quatro views e 19 permissões
globais. O schema contém 81 FKs. Depois da validação estrutural, a primeira
unidade real e seu administrador foram cadastrados por solicitação do proprietário;
esses registros não são dados de teste nem seeds de migração. O bootstrap gera
hash Argon2id, arquivo privado de credencial, auditoria e evento de outbox.
A senha da role de execução Docker fica em `.tools/docker-app-password.txt`,
fora do versionamento. O container de banco ficou ativo ao final desta etapa.

Na entrega inicial acima, a API Java, o publicador da outbox, a autenticação
HTTP e os fluxos de tela ainda não estavam implementados. A atualização abaixo
registra a entrega seguinte.

As limitações e transações que cabem ao Java estão em `docs/banco/README.md`.

## Atualização — aplicação local e V11

- V11 aplicada e validada no Docker, preservando as dez migrações existentes.
- 33 tabelas de negócio/referência, duas de infraestrutura da API, quatro views,
  83 chaves estrangeiras e 11 migrações. O histórico do Flyway é adicional.
- Os 31 testes PostgreSQL passaram novamente após a nova migração.
- Dez testes Java passaram com PostgreSQL real e credencial `bar_app`, incluindo
  sessão, permissões, unidade, senha, complementos, idempotência, concorrência
  financeira, estorno e correção de caixa.
- Seis testes Playwright passaram, nos formatos computador e celular: cadastro
  manual, pedido, produção, recebimento, fechamento e perda/retorno de conexão.
  O fluxo foi executado bloqueando recursos externos ao servidor local. Também
  foi simulada resposta perdida após commit: conferir o envio não duplicou o cadastro.
- O pacote Windows foi gerado com runtime e a tela de login foi renderizada
  no JavaFX WebView. O login do administrador real foi conferido sem redefinir
  a senha; a sessão de conferência foi encerrada.
- Backup criado e restaurado em banco separado, removido após a conferência.
- HTTPS e cadeia de certificado foram conferidos no loopback e no IP local do
  computador, além da renderização JavaFX usando HTTPS. Isso não substitui o teste
  em celular físico ou a validação do firewall na rede do bar.
- Os dados reais de mesas e produtos permanecem vazios para cadastro manual.

Os testes usam bancos gerados para cada execução e não removem dados reais.
Consulte [COMO-USAR.md](../COMO-USAR.md) para abrir o programa e fazer backup.
A configuração HTTPS local está preparada, mas confiança de certificado,
firewall, acesso por aparelho físico e piloto com o proprietário exigem
validação na rede do bar. Não foi simulada queda física da internet/roteador
nem reinício completo do Windows sem internet nesta entrega.


## Atualização — acesso simples e V12

A V12 foi aplicada e validada no Docker de operação e no treinamento separado,
sem alterar as migrações anteriores. `users.email` aceita ausência, `username`
é único por unidade, e `bar_tables.active` permite retirar/recuperar mesas sem
apagar histórico. Triggers protegem a concorrência entre retirada e abertura
de conta. A aplicação usa usuário/senha e mantém os acessos antigos por e-mail.

Nesta entrega passaram 22 testes Java e 21 testes de interface em cada modo,
com PostgreSQL real em bancos descartáveis. Os testes Java incluem SQL direto,
concorrência, preservação de pagamentos e autorização por unidade. Os 31 testes
de banco registrados acima pertencem à validação anterior; não foram reexecutados
nesta rodada. Os dados existentes das 35 tabelas fora do histórico Flyway foram
comparados antes/depois da atualização nos dois ambientes, por contagem e hash
das colunas anteriores. Nenhum registro foi alterado. Backup real restaurado e
conferido em banco temporário antes da migração.

Resultados e limites em [VALIDACAO-PILOTO.md](../VALIDACAO-PILOTO.md).


## V13/V14 — estoque e novo front

São 37 tabelas incluindo Flyway, 87 chaves estrangeiras, quatro views e
14 migrações versionadas. Os 29 testes Java passaram em bancos descartáveis;
o estoque foi conferido com concorrência, rollback integral, idempotência,
cancelamento e bloqueio de alteração direta/histórico. Dados anteriores
preservados por comparação de contagens/hashes em operação e treinamento.
Backup real restaurado e conferido antes e depois da atualização, incluindo
a nova tabela de estoque. Os testes PostgreSQL originais de 31 casos são
históricos e não foram reexecutados nesta rodada. Consulte
[ENTREGA-NOVO-FRONT.md](../ENTREGA-NOVO-FRONT.md).
