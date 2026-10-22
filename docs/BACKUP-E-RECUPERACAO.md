# Backup automático e recuperação conferida

O sistema faz uma cópia verificada do banco de operação a cada 60 minutos
enquanto o serviço local está em uso. Não é uma tarefa de administrador do
Windows: o trabalhador é iniciado junto com a aplicação, no usuário atual.
Quando o programa volta a iniciar, ele confere se há uma cópia atrasada.

## Acompanhar e solicitar

Entre como administrador, clique em **Configurações** e encontre a seção
**Cópias de segurança**. A tela mostra a última cópia confirmada, a
**Última restauração de teste**, o estado do **Serviço de cópias** e o
resultado da segunda cópia, quando configurada.
O botão **Fazer backup agora** solicita uma cópia ao trabalhador local.

**ferramentas/Fazer Backup.cmd** continua disponível no computador para executar uma
cópia verificada manualmente. Uma trava impede cópias simultâneas.
**ferramentas/Pausar Backups Automaticos.cmd** pausa o agendamento. Para reativar sem mudar
a pasta escolhida, abra **ferramentas/Ativar Backups Automaticos.cmd**.

O treinamento não administra backups reais. O botão de solicitação fica
disponível somente na operação, para usuários com permissão administrativa.

## Escolher uma segunda pasta

1. Conecte o disco ou pendrive que deseja usar.
2. Abra **ferramentas/Configurar Backups.cmd**.
3. Escolha a pasta de destino na janela do Windows.
4. O utilitário cria a subpasta `Bar-do-Zico-Backups` e configura a segunda cópia.

O destino externo ainda depende dessa escolha do proprietário. Até lá, as
cópias ficam em `.tools/backups/`, no disco local. A tela identifica essa situação.
Se o disco escolhido estiver desconectado, a cópia local continua protegida
e o estado da segunda cópia aparece como indisponível. Os arquivos existentes
não são apagados automaticamente.

## O que uma cópia confirmada significa

O `pg_dump` usa o mesmo snapshot das contagens das tabelas. Isso permite
conferir os dados restaurados mesmo enquanto há atendimento concorrente.
Antes de marcar sucesso, o sistema:

1. Grava o arquivo temporário do backup.
2. Restaura em um banco novo de nome aleatório, diferente do banco real.
3. Compara todas as contagens com o snapshot e consulta as views de contas e caixa.
4. Remove somente o banco temporário criado para a conferência.
5. Confirma o arquivo `.dump` e seu `.manifest.json`, com SHA-256 e resultado.

Uma falha não substitui a última cópia confirmada. Arquivos `.part` não são
considerados backups concluídos. O sistema preserva as cópias anteriores.
Referência técnica: [pg_dump do PostgreSQL 17](https://www.postgresql.org/docs/17/app-pgdump.html).

## Conferir uma cópia existente

1. Mantenha o Docker e o banco funcionando.
2. Abra **ferramentas/Conferir Backup.cmd**.
3. Selecione um `.dump` criado pelo sistema. O `.manifest.json` correspondente
   deve estar na mesma pasta.
4. Aguarde a conferência em banco temporário. O banco real não será substituído.

O utilitário rejeita arquivo com checksum diferente do manifesto. Use apenas
cópias confiáveis produzidas por esta instalação. As cópias antigas, anteriores
à criação de manifestos, continuam preservadas, mas exigem conferência técnica
manual antes de uma restauração.

## Recuperar após falha do computador

O procedimento de conferência acima prova que os dados podem ser restaurados;
ele não troca automaticamente o banco utilizado pelo sistema. Para recuperação
de produção, interrompa os atendimentos e faça a operação acompanhada:

1. Prepare Windows/Docker, PostgreSQL e as credenciais da instalação de destino.
2. Transfira o par `.dump`/`.manifest.json` de uma cópia confirmada.
3. Restaure primeiro em banco separado e confira contagens, unidade e usuários.
4. Reaplique as permissões da credencial `bar_app` no banco recuperado.
5. Só depois da conferência, ajuste a conexão da aplicação e valide o fluxo
   antes de liberar atendimentos.

O arquivo do banco preserva os hashes de senha dos usuários, mas não substitui
os arquivos locais `.env`, certificados privados, configuração ou instalação
das ferramentas. Se a autoridade de certificado do computador for recriada,
será necessário configurar novamente a confiança nos Android.

Ainda precisam ser definidos pelo proprietário o responsável diário pelas
cópias e o segundo destino. Um teste completo de recuperação em outro computador
continua pendente.
