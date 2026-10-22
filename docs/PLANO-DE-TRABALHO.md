# Plano de trabalho — Bar do Zico

## Autoria de mesas e itens — V21 (21/10/2026)

As mesas mostram **Criada por**. No consumo, **Atendimento aberto por** informa
quem começou a conta e **Adicionado por** identifica o responsável por cada item,
tanto no PC quanto no celular. O Caixa mantém a coluna de atendente por produto.
Trocar de atendente não altera a autoria anterior.

V21 grava o identificador e o nome original do criador da mesa; edições e
transferências preservam esses campos. O banco valida a unidade e impede mudar
o criador. A migração recupera cadastros anteriores somente quando encontra um
evento `table.create`; os demais exibem **Não identificado**, sem atribuir ao editor.

Validação: **98 testes Java/API** e **62 casos de interface** aprovados. Incluiu
dois atendentes na mesma mesa, troca de nome, edição, transferência de mesa
ocupada e rejeição de criador forjado. Migração conferida em cópia preenchida,
com recuperação de criação comprovada e preservação dos registros anteriores.
Operação e treinamento atualizados por HTTPS; os registros anteriores das
36 tabelas de cada banco foram conferidos durante a instalação. Nenhum cadastro
ou venda foi apagado. Para carregar as novas telas, use Reconectar no PC e
reabra o APK, sem reinstalação.

## PC administrador e nome do atendente — V20 (21/10/2026)

A aba Funcionários foi retirada. O PC usa Mesas, Caixa, Estoque, Cardápio e
Configurações. O APK pede somente o nome na primeira abertura, lembra neste
aparelho e permite trocar em Menu → Configurações. A troca não altera outros
celulares nem renomeia responsáveis por pedidos anteriores. Envios pendentes
devem ser conferidos antes da troca. O consumo consultado no Caixa mostra o
atendente de cada produto. Sem e-mail, senha ou cadastro prévio.

V20 separa os identificadores internos por nome da identidade genérica de
compatibilidade. Cookies exclusivos de atendimento, separados por ambiente,
não concedem gestão; o administrador local continua definido pelo PC. Rotas,
cadastros e referências históricos permanecem no banco.

Validação: **97 testes Java/API** e **60 casos de interface** aprovados.
Migração V19 → V20 conferida em cópia preenchida, preservando os registros de
36 tabelas. Operação e treinamento atualizados por HTTPS, com os registros
das 36 tabelas de cada banco conferidos durante a instalação. O treinamento
continua sem mesas, produtos, vendas ou caixas. Não exige reinstalar o APK.

Arquivos organizados: comandos de manutenção em `ferramentas/`, guias reunidos
no índice `docs/README.md` e registro datado em `docs/historico/`.
Inicializadores e atalhos de abertura permanecem na pasta principal.
Nove comandos movidos com os caminhos ajustados; diagnóstico de rede executado
a partir de outra pasta, sem alterar a rede. Links relativos dos guias conferidos.

## Treinamento reiniciado vazio (21/10/2026)

Limpeza solicitada pelo proprietário: treinamento recriado sem mesas, locais,
categorias, produtos, funcionários adicionais, atendimentos, vendas, estoque
ou caixas anteriores. Mantidos apenas o responsável inicial, permissões e
configurações necessárias ao funcionamento. Sem caixa aberto; abertura padrão
continua em R$ 0,00. Os exemplos iniciais também foram retirados e não voltam
ao reabrir o programa. As 36 tabelas do banco de operação foram conferidas e
preservadas; a limpeza afetou somente o treinamento.

## Seleção do caixa sem aberturas antigas (21/10/2026)

**Caixa em uso** mostra somente caixas abertos, incluindo os que exigem
fechamento antes de uma nova abertura. Caixas já encerrados não aparecem na
seleção nem são selecionados automaticamente. Sem abertura atual, a tela
informa **Nenhum caixa aberto** e oferece **Abrir caixa**. O resultado de um
fechamento recém-realizado permanece disponível para conferência até sair
da seção; o histórico financeiro continua no banco.

Compilação da interface e **64 casos de interface aprovados**, incluindo
aberturas antigas preservadas, abertura nova, fechamento com totais calculados,
reentrada na tela e caixa anterior pendente, no computador e em tela de celular.
Sem alterações no banco ou no APK; mantém PC 0.2.4 e Android 0.2.3.

Operação e treinamento atualizados por HTTPS e conferidos com a mesma interface
compilada. Treinamentos do PC e Android abertos. Após a mudança do IP residencial
para 192.168.0.3, a conexão do Android de teste foi ajustada nas configurações;
o perfil Público do Windows foi preservado. Azure/4G, sincronização local/nuvem
e a validação da impressora física continuam como etapas futuras.

## Fechamento com totais do sistema e impressão livre (21/10/2026)

Decisão mais recente: retirar a contagem manual do fechamento normal e a etapa
obrigatória de conferência da guia. Pagamento e **Visualizar / imprimir guia**
ficam disponíveis juntos. Prévia nativa sem bloquear a janela do caixa; no
navegador, prévia dentro da própria tela. É possível receber sem imprimir e
concluir o pagamento com a guia aberta. PC **0.2.4**, APK **0.2.3** sem reinstalar.

**Fechar caixa** envia `useSystemTotals:true`; o servidor calcula as seis formas
sob lock usando o banco completo, registra os valores e sua origem e preserva
idempotência e histórico. Sem campos obrigatórios de contagem. Mantém o
fechamento do turno anterior antes da abertura nova, cujo padrão é R$ 0,00.
Não há migração nova; V1–V19 permanecem intactas.

Validação: **93 testes Java/API** e **62 casos de interface** aprovados. Incluiu
fechamento com mais de 500 pagamentos, todas as formas, troco, devoluções,
entradas/retiradas, reenvio e fechamento concorrente com movimento; pagamento
parcial sem abrir a guia e finalização com a prévia aberta. Prévia nativa de
80 mm conferida no executável 0.2.4. Backups de operação e treinamento
restaurados em bancos temporários para conferência antes da instalação.
Operação e treinamento atualizados por HTTPS; dados de 36 tabelas de cada banco
preservados durante a instalação. O executável Windows 0.2.4 foi empacotado no
mesmo caminho dos atalhos existentes.

## Conferência antes de receber e abertura diária do caixa — V19 (21/10/2026)

Decisão confirmada: exigir conferir e fechar o caixa anterior antes de uma
nova abertura do programa. Não fechar automaticamente nem preencher contagem.
`cash.start` é idempotente por janela e marca turnos abertos para conferência.
Caixa pendente bloqueia recebimentos/movimentos e novas aberturas na API e no
banco; `cash.close` preserva o fechamento contado. Atualizar a mesma janela
não reaplica a inicialização. Nova abertura padrão R$ 0,00. Fechar caixa fica
visível ao lado da seleção, além da aba de fechamento.

No recebimento: **1. Conferir a conta → Visualizar guia antes de receber → Conta
conferida. Ir para pagamento → 2. Receber pagamento**. Alterações no consumo
invalidam a conferência; totais esperados são validados sob lock antes da
gravação. A guia saiu do comprovante posterior; reimpressão histórica permanece.
Manteve PC administrador, Android atendimento, sem reinstalar o APK.

V19 preserva valores e migrações V1–V18. Verificação em banco preenchido com
36 tabelas, incluindo um turno aberto e um encerrado. Backups de operação e
treinamento conferidos por restauração antes da instalação.

Validação: **91 testes Java/API** e **62 casos de interface** aprovados, incluindo
duas janelas do PC, fechamento obrigatório anterior, reenvio da inicialização,
abertura concorrente e conta alterada após conferência. Os testes usam bancos
descartáveis; não criam vendas ou fechamentos nos bancos persistentes.
V19 instalada em operação e treinamento: os dados anteriores de 36 tabelas em
cada banco permaneceram intactos. HTTPS e prévia da tela inicial no JavaFX
conferidos após a atualização. Abrir o treinamento marca apenas seus caixas
anteriores como pendentes; nenhuma contagem ou fechamento foi inventado.

## Guia da mesa e impressora térmica — 80 mm (21/10/2026)

Decisão confirmada: papel de **80 mm**. O proprietário ainda não tem a impressora.
Implementados documento canônico pela API, prévia no executável Windows e
diálogo de impressão pelo driver instalado. Apenas **Imprimir** e **Fechar**;
documento em memória, sem etapa de salvar. Botões no atendimento, Caixa,
comprovante e Vendas. Produtos, quantidades, valores unitários e por linha,
adicionais, ajustes e total vêm do atendimento completo. Nenhum pagamento ou
encerramento é registrado pela impressão. APK permanece de atendimento.

Executável Windows **0.2.3**, no mesmo local dos atalhos; APK sem reinstalação.
Sem migração ou alteração de dados comerciais. Validação: **87 testes Java/API**
e **58 casos de interface** aprovados; prévia nativa conferida no JavaFX e contas
longas conferidas em 20 páginas de 80 mm. Cancelamentos, adicionais, ajustes e
suas reversões, pagamentos parciais, devoluções, permissões, histórico de mesa
excluída e acesso de outra unidade foram verificados. Backups de operação e
treinamento com restauração de conferência aprovados antes da atualização.
Operação e treinamento atualizados em HTTPS, com os dados de 34 tabelas de cada
banco preservados. Prévia nativa também conferida no treinamento por HTTPS.
Teste físico de papel, margens e corte fica para quando a impressora estiver
disponível. [Uso e detalhes](IMPRESSAO-DA-MESA.md).

## Seleção de produto por categoria — PC e celular (21/10/2026)

Decisão confirmada: ao adicionar consumo, escolher **Categoria → Produto**.
A lista fica restrita à categoria ativa escolhida, com produtos disponíveis.
Trocar a categoria limpa produto, complementos, quantidade e observação;
categorias vazias não permitem enviar. Uma atualização do catálogo que retira
o produto escolhido também desabilita o envio. A tela é compartilhada pelo
executável Windows e pelo APK; não há mudança de banco ou reinstalação do APK.

Validação: 54 casos de interface aprovados nos tamanhos de computador e celular,
incluindo o perfil do APK. Foram conferidos categoria obrigatória, filtro por
categoria, produtos indisponíveis/sem estoque, categoria vazia, limpeza de
complementos e retirada de um produto já selecionado por atualização do catálogo.
Serviços de operação e treinamento atualizados com a nova interface.

## Cadastro de mesas pelo atendente e administrador — V18 (21/10/2026)

Decisão confirmada: **atendente e administrador criam mesas**. Adicionar mesa
cadastra uma nova mesa; Abrir mesa inicia seu atendimento. O celular recebe
cadastro sem edição/exclusão nem administração dos locais. O PC mantém gestão.
Detalhes e uso: [MESAS-NO-CELULAR.md](MESAS-NO-CELULAR.md).

V18 introduz `table.create` e mantém V1–V17 intactas. O primeiro cadastro pode
criar Salão automaticamente. Nomes repetidos, local de outra unidade, edição
disfarçada e cadastros simultâneos são verificados pela API. A atualização é da
API/interface compartilhada: APK 0.2.3 e executável 0.2.2 permanecem compatíveis.

Verificação: 82 testes Java/API e 50 casos de interface aprovados. Migração
V17 → V18 conferida em banco preenchido, sem alterar dados de 34 tabelas,
permissões antigas ou checksums anteriores. Backups de operação e treinamento
criados e restaurados em bancos temporários antes da aplicação.

Ambiente solicitado para testes do proprietário: Android 15 virtual no Windows,
com o APK de treinamento e atalho **Bar do Zico - Android de Teste**. Guia:
[ANDROID-DE-TESTE.md](ANDROID-DE-TESTE.md).

## Ajuste Android atual — abrir mesa e configurações no menu (0.2.3)

O APK passa para **0.2.3, código 4**, com a mesma assinatura. No atendimento,
o botão da mesa livre será **Abrir mesa**; uma mesa em atendimento continua com
**Ver consumo**. É o fluxo já existente de abertura de conta, sem outra etapa.
**Conexão** sai do topo. O caminho passa a ser **Menu → Configurações → Conexão
com o servidor**, abrindo as configurações nativas do aplicativo por
`bardozico://settings`. Em erro de conexão há um atalho **Configurações**.
Essa opção não expõe gestão nem altera o perfil limitado do celular.

O executável do PC permanece **0.2.2**, como administrador. Esta revisão Android
precisa de sua própria validação; as aprovações da versão 0.2.2 registradas
abaixo são históricas. Instalar o novo APK por cima do anterior, sem limpar dados.

## Direção atual — PC administrador e APK de atendimento (V17, 20/10/2026)

O proprietário confirmou: **PROGRAMA NO PC: ADM; CELULAR: ATENDIMENTO**.
O PC abre com gestão fixa por conexão real de loopback. O APK e as conexões pela
rede consultam mesas/cardápio, abrem atendimento e adicionam produtos; não
recebem caixa, exclusões ou gestão. A API aplica a restrição também a leituras
e comandos, ignora cookies antigos nesse modo e usa o marcador Android somente
para reduzir acesso. Sem login e sem escolha voluntária de responsável.

Nova abertura de caixa começa em **R$ 0,00**, editável. Caixa existente deve ser
conferido/usado, sem outra abertura. Valores e referências anteriores permanecem.
V17 acrescenta a identidade interna de atendimento, sem editar V1–V16. Há
17 migrações. A entrega anterior publicou Android **0.2.2, código 3**, com mesma
assinatura; o ajuste atual 0.2.3 está descrito acima. Guia:
[PERFIS-PC-CELULAR.md](PERFIS-PC-CELULAR.md).

Na entrega 0.2.2, a migração V16 → V17 foi validada em banco preenchido descartável por
`scripts/validar_migracao_v17.py`, preservando hashes das 36 tabelas e checksums.
Validação desta entrega: 77 testes Java/API/endereço e 50 casos de interface
aprovados, além de oito verificações unitárias Android e lint dos dois APKs.
O executável 0.2.2 renderizou a tela no JavaFX 25; os dois serviços HTTPS foram
conferidos como administrador no PC e atendimento pela rede. Backups anteriores
à atualização foram verificados e os hashes de 36 tabelas em cada banco
confirmaram a preservação dos dados existentes. Os registros abaixo conservam
os resultados históricos.

Próximo passo: conferir no Android físico o perfil de atendimento e no PC a
gestão/caixa, preservando o perfil Público do Windows. Na rede residencial, PC e
celular usam o mesmo roteador. Acesso por 4G/outra rede continua dependente do
piloto Azure, autorização dos aparelhos e futura sincronização local/nuvem.

## Direção confirmada mais recente — Azure e rede residencial

O proprietário confirmou acesso também por 4G/outras redes, com **Azure** como
nuvem. O perfil **Público** do Windows será mantido na conexão residencial.
Consulte [REVISAO-REDE-E-AZURE.md](REVISAO-REDE-E-AZURE.md): assinatura ativa
consultada, sem recursos criados; HTTPS residencial e APKs corrigidos/validados.

Próximas fases: piloto Azure isolado com infraestrutura/custos avaliados;
autorização dos aparelhos sem login diário; sincronização local/nuvem para
quedas de internet; só depois ativação real acompanhada. A sincronização não
existe hoje. Publicar um banco vazio no Azure não replica os dados do bar.
Os detalhes antigos abaixo registram o desenvolvimento local e não comprovam
publicação na nuvem. A revisão anterior de rede reuniu 58 testes Java/API e 40 de tela.

Status: base funcional implementada e validada em ambiente de desenvolvimento.
Interface simplificada para o uso diário, com exclusão visível de mesas
e cadastro de funcionários sem e-mail. Validação no computador e no Android emulado.
O piloto com o proprietário e a configuração em celulares físicos permanecem pendentes.

**Direção mais recente:** novo front implementado conforme a referência visual e
funcional do proprietário: **Mesas, Caixa, Funcionários, Estoque e Cardápio**.
Abertura direta em Mesas, sem login. Aplicativos Android instaláveis em APK para
operação e treinamento, com a interface compartilhada e acesso restrito a atendimento pela V17. Consulte
[NOVO-FRONT.md](NOVO-FRONT.md), [guia de uso](COMO-USAR-NOVO-FRONT.md) e
[registro da entrega](ENTREGA-NOVO-FRONT.md).

## Ajustes de cadastros, caixa e vendas — V16 (20/10/2026)

Implementados os comandos de exclusão de locais (transferindo mesas ocupadas
sem alterar o atendimento) e funcionários (preservando histórico e revogando
seleções). Resumo de dinheiro em caixa e vendas líquidas, com ocultação lembrada
no aparelho. Vendas por atendimento encerrado, com período, nome histórico da mesa
e paginação, sem usar a lista operacional limitada. Auditoria em Configurações
com textos simples; permissões agrupadas e preparo/entrega ocultos. Navegação
fixa em bloco de altura automática, com restauração de foco sem deslocamentos.

V16 acrescenta `users.deleted_at` e índices; não altera registros anteriores.
Conferência de migração preenchida: `scripts/validar_migracao_v16.py`.
Guia de uso atualizado em COMO-USAR-NOVO-FRONT.md. Homologação em Android físico
continua pendente; nenhum teste automatizado deve criar vendas nos bancos persistentes.

## Direção atualizada — exclusões e consumo direto (20/10/2026)

Esta orientação substitui os fluxos históricos de preparo, entrega e recuperação
listados mais abaixo: excluir produto pelo Estoque, excluir mesa permanentemente
com número reutilizável e salvar os produtos diretamente no consumo da mesa.
V15 conserva os nomes das mesas nas contas antigas e mantém vendas e estoque
históricos dos produtos excluídos. Não criar novas etapas de produção na HUD.

Entrega conferida: V15 nos dois bancos; 31 testes Java e 26 casos de tela
aprovados (telas em operação e treinamento). Migração V14 → V15 também validada
com histórico existente. As oito mesas já arquivadas do treinamento foram
removidas permanentemente; vendas, usuários e estoque foram conferidos por hash.
Consulte [registro dos ajustes](AJUSTES-ATENDIMENTO-DIRETO.md).

## Objetivo

Entregar um programa Java instalado no computador para administrar mesas,
consumo e caixa do Bar do Zico, com dados no PostgreSQL local.
Os celulares dos funcionários conversarão com o serviço Java desse computador
pela rede local do bar. O atendimento principal deve continuar sem internet.
O primeiro uso deve ser simples: iniciar os serviços, abrir o programa pelo
atalho e acessar diretamente Mesas, sem tela de login no novo front.

## Arquitetura local e queda de internet

```text
Computador do bar
  Programa Java com janela própria (JavaFX)
                    |
             Serviço Java (API) <--- Wi-Fi local ---> Celulares
                    |
             PostgreSQL local
```

O programa do computador e as telas dos celulares usam a mesma API. Apenas
o serviço Java acessa o banco e aplica as regras de negócio. Não duplicar
o banco nos celulares nem enviar credenciais PostgreSQL para eles.

- **Internet caiu, mas computador e rede local funcionam:** continuar
  consulta de cardápio, consumo nas mesas e registros de caixa locais.
- **Celular perdeu o Wi-Fi ou o serviço ficou indisponível:** mostrar a perda
  de conexão e impedir novos envios sem confirmação. Ao reconectar, consultar
  o resultado de envios pendentes antes de repetir, preservando a idempotência.
- **Computador desligado:** os celulares não conseguem operar neste modelo.
- **Roteador desligado:** a comunicação dos celulares fica indisponível;
  o programa no computador deve continuar usando sua conexão local com a API.
- **Integrações externas:** cobrança automática, serviços fiscais e outras
  integrações podem depender de internet e terão tratamento próprio.

A capacidade de funcionar sem internet vem da instalação local dos serviços
e recursos; uma interface no navegador hospedada localmente também poderia
funcionar assim. A janela Java é a opção adotada para o computador.

Para os celulares, está implementada uma interface web local. O proprietário
confirmou o uso de Android; a validação em aparelho físico está pendente.
Não prometer instalação como PWA, notificações
ou funcionamento sem Wi-Fi antes de validar os requisitos dessas funções.

## Ponto de partida

Estado conferido nesta entrega:

- [x] Banco PostgreSQL com 34 tabelas de negócio/referência, duas de infraestrutura,
      quatro views e 17 migrações.
- [x] 31 testes de integridade e concorrência aprovados em PostgreSQL real.
- [x] Docker Desktop e ferramentas Java/Maven preparados no Windows.
- [x] Unidade Bar do Zico e administrador Raphael cadastrados.
- [x] Credencial da aplicação separada da credencial de migração.
- [x] Projeto Java executável e API, serviço que atende às solicitações das telas.
- [x] Autenticação, permissões aplicadas às requisições e troca de senha.
- [x] Programa Java para computador e interface web de atendimento para celulares.
- [x] Fluxo principal de atendimento, relatórios e backup com restauração de teste.

O administrador existente já pode entrar nas telas. O programa Windows usa
JavaFX WebView para compartilhar a interface React com os celulares.
O fluxo foi validado em bancos descartáveis, sem preencher mesas/produtos reais.
A interface de celular foi testada por emulação. Há APK Android instalável
e validado no emulador; a validação em aparelho físico permanece pendente. Backups automáticos horários,
com restauração em banco temporário para conferência, estão implementados.
Ainda é necessário escolher uma segunda pasta fora do disco principal.
O ponto de partida está entregue para o piloto;
isso não representa homologação para operação real.

Consulte [COMO-USAR.md](COMO-USAR.md) para abrir o programa e
[VALIDACAO.md](banco/VALIDACAO.md) para os resultados e limites dos testes.

## Como conduzir o trabalho

1. Retomar por este arquivo e conferir os arquivos existentes antes de alterar.
2. Trabalhar por entregas pequenas que possam ser demonstradas ao proprietário.
3. Explicar Docker e comandos desde o começo: onde clicar, onde digitar,
   o que o comando faz e qual resultado deve aparecer.
4. Preferir acesso pelas telas assim que estiverem disponíveis. Diferenciar
   PowerShell do Windows, terminal do container e console SQL quando necessários.
5. Preservar a unidade, o administrador, as senhas e os dados já cadastrados.
   Não usar registros reais como dados descartáveis de teste.
6. Registrar decisões, verificações e pendências ao terminar cada entrega.
7. Consultar a documentação oficial ao selecionar versões e implementar
   integrações. Fixar versões compatíveis nos arquivos de dependências.

## Stack de trabalho

| Camada | Tecnologia prevista | Finalidade |
|---|---|---|
| Computador | Java 25 LTS e JavaFX 25.0.1 com WebView | Programa Windows com janela própria e interface compartilhada |
| Back-end | Java 25 LTS, Spring Boot 3.5.16 e Maven | Regras de negócio e API HTTP |
| Segurança | Spring Security e Argon2id | Login, sessões e permissões |
| Persistência | Spring JDBC e driver PostgreSQL | SQL e coordenação explícita dos bloqueios/transações existentes |
| Banco | PostgreSQL 17 e Flyway | Dados, integridade e evolução do schema |
| Celular | React, TypeScript e Vite | Interface web responsiva hospedada no computador do bar |
| Testes da API | JUnit e Spring Boot Test com PostgreSQL real | Validar contratos, transações e permissões |
| Testes das telas | Playwright para a interface web; verificação da interface Java | Conferir o uso no celular e no computador |
| Execução local | Docker Desktop e Docker Compose para os serviços; pacote Windows para o cliente Java | Iniciar a instalação local sem depender da internet |

Organizar o Java como uma aplicação modular, com módulos de identidade,
salão, catálogo, pedidos, produção e caixa. Começar com um único serviço
de negócio e um único banco. Criar o cliente Java em `apps/desktop/`,
a interface dos celulares em `apps/web/` e completar o serviço em `apps/api/`.
Python permanece como ferramenta auxiliar de banco.

O JDK 25.0.4.1+1 preexistente nesta máquina é selecionado pelo marcador local
`.tools/java-home.txt`. Compilação, inicialização, certificados e empacotamento
usam esse mesmo JDK; JavaFX permanece na versão 25.0.1 definida no POM.
O Java 21 global e o `JAVA_HOME` do usuário são preservados. A seleção em
`scripts/ambiente.ps1` vale somente para o processo atual.

Manter o serviço e o banco independentes da janela: fechar a tela do computador
não deve encerrar o atendimento dos celulares. Planejar um inicializador
que confira os serviços e ofereça abrir o programa, com diagnóstico em português.
Preparar o pacote Windows com o runtime Java necessário. Instalação e atualizações
podem exigir download; o uso diário após a preparação não deve exigir.

Referência para a interface Java e empacotamento do runtime:
[documentação oficial do JavaFX](https://openjfx.io/openjfx-docs/).

React/TypeScript/Vite foram implementados. A persistência usa JDBC em vez da
proposta inicial de JPA, para trabalhar diretamente com o contrato SQL existente.
Os arquivos das telas são incluídos na API; não há dependência de CDN no uso diário.

As fases 0–5 abaixo registram a base construída anteriormente. A abertura
sem login e o aplicativo Android instalável da entrega atual prevalecem sobre
os critérios antigos de entrada e navegador. A V17 substitui a escolha de
responsável V13/V14 por PC administrador fixo e rede/APK de atendimento.
Não recolocar login ou seleção de administrador no celular sem nova solicitação.

## Fase 0 — Preparar a retomada

- [x] Conferir Docker, banco, migrações e disponibilidade das ferramentas.
- [x] Conferir a existência da unidade e do administrador, sem exibir senhas.
- [x] Preparar backup e demonstrar restauração em um banco separado.
- [x] Separar configuração local/HTTPS e bancos descartáveis de teste.
- [x] Conferir regras de negócio existentes em [banco/README.md](banco/README.md).
- [ ] Confirmar endereço estável e firewall na rede do bar, acesso
      autenticado e HTTPS local para os celulares, sem expor o banco na rede.

**Critério de homologação:** o ambiente puder ser iniciado com instruções claras,
os dados existentes estiverem preservados e a restauração tiver sido conferida.

## Fase 1 — Acesso inicial ao sistema

### Fase 1 — Back-end

- [x] Criar o projeto Spring Boot com Maven Wrapper, configuração por ambiente,
      tratamento de erros e endpoint de saúde sem informações sensíveis.
- [x] Conectar usando `bar_app`; manter a migração com credencial separada.
- [x] Conferir o contrato do schema na inicialização e nos testes sem recriá-lo;
      manter Flyway como responsável pelo DDL, sem usar JPA nesta entrega.
- [x] Implementar login compatível com o hash Argon2id já cadastrado.
- [x] Definir o mecanismo de sessão, expiração, revogação e proteção contra
      tentativas repetidas de login. Preferir cookies HttpOnly para o navegador
      e aplicar proteção contra CSRF quando o mecanismo exigir.
- [x] Definir autenticação do cliente Java sem gravar senhas em texto aberto;
      login e validação das sessões locais não podem exigir serviços na nuvem.
- [x] Implementar consulta do usuário autenticado, logout e troca da senha.
- [x] Aplicar permissões e vínculo com a unidade em todas as operações protegidas.
- [x] Registrar os eventos de segurança necessários sem senhas ou tokens nos logs.
- [x] Documentar o contrato da API e verificar login inválido, acesso negado,
      acesso entre unidades e revogação de sessão.

### Fase 1 — Front-end do computador

- [x] Criar o cliente JavaFX/WebView com navegação e comunicação com a API local.
- [x] Construir login, troca de senha e página inicial do Bar do Zico.
- [x] Exibir usuário conectado, opção de sair e apenas as ações permitidas.
- [x] Tratar carregamento, erro de conexão e sessão expirada em português.
- [x] Preparar inicializadores `.cmd`, executável com runtime e guia de abertura.

### Fase 1 — Interface dos celulares

- [x] Criar a interface web responsiva com login, logout e identificação da unidade.
- [x] Hospedar os arquivos no computador do bar, sem fontes, scripts ou outros
      recursos obrigatórios carregados de sites externos.
- [x] Documentar como conectar ao Wi-Fi e abrir o endereço local do sistema.
- [x] Mostrar claramente se o serviço está disponível e tratar sessão expirada.
- [ ] Configurar a confiança do certificado e validar o acesso no celular físico.

**Concluída quando:** Raphael conseguir abrir o programa Java, entrar com a conta
existente, trocar a senha, ver a página inicial e sair. O usuário sem sessão
não consegue acessar dados protegidos. Um celular consegue entrar pela rede
local. Repetir o acesso com a internet desconectada e a rede local preservada.

## Fase 2 — Mesas e cardápio

Implementação entregue; falta o preenchimento real pelo proprietário.

### Back-end

- [x] Implementar áreas, mesas, categorias, produtos, disponibilidade e complementos.
- [x] Validar preços, seleção mínima/máxima de complementos e vínculo com o produto.
- [x] Aplicar permissões, isolamento da unidade e auditoria das alterações relevantes.
- [x] Preservar histórico de vendas ao alterar ou desativar cadastros.

### Front-end

- [x] Criar telas de mesas, áreas e cardápio com cadastro, edição e desativação.
- [x] Mostrar estados das mesas e disponibilidade dos produtos com textos claros.
- [ ] Cadastrar os dados reais informados pelo proprietário (preenchimento manual solicitado).
- [x] Concentrar os cadastros administrativos no programa Java e disponibilizar
      consulta de mesas e cardápio na interface dos celulares.

**Concluída quando:** o administrador conseguir preparar mesas e cardápio
pelas telas, sem inserir SQL manualmente.

## Fase 3 — Atendimento e produção

Fluxo implementado e testado em banco descartável; falta o piloto acompanhado.

### Back-end

- [x] Abrir conta e ocupar mesa na mesma transação.
- [x] Registrar pedidos e complementos preservando preços e descrições da venda.
- [x] Validar autorização de mesa e disponibilidade antes de aceitar pedidos.
- [x] Implementar fila e avanço de produção, com cancelamento autorizado e motivo.
- [x] Registrar auditoria e eventos na mesma transação de cada operação.
- [x] Implementar reenvio seguro: repetir a mesma solicitação não duplica o pedido.
- [x] Implementar entrega dos eventos da outbox após confirmação da transação,
      com retentativa e deduplicação; definir atualização das telas.

### Front-end

- [x] Criar atendimento por mesa, seleção de produtos/complementos e resumo da conta.
- [x] Criar painel de produção por destino, com atualização do estado dos pedidos.
- [x] Mostrar falhas de envio e permitir recuperação sem duplicar lançamentos.
- [x] Priorizar lançamento de pedidos pelo celular; oferecer acompanhamento
      no computador e validar o fluxo sem acesso à internet.

**Concluída quando:** um pedido feito na mesa aparecer na produção, avançar
pelos estados permitidos e compor corretamente o consumo da conta.

## Fase 4 — Caixa e recebimentos

Fluxo implementado e testado; validar as políticas reais de descontos/serviço e
estornos com o proprietário antes da operação.

### Back-end

- [x] Abrir turno, registrar suprimento e sangria e consultar os totais.
- [x] Implementar descontos, serviço e acréscimos conforme a política confirmada.
- [x] Registrar pagamentos parciais e combinados, troco e estornos autorizados.
- [x] Quitar conta e liberar mesa atomicamente; tratar reabertura sem ocupar
      uma mesa que já esteja em outro atendimento.
- [x] Implementar fechamento com conferência das seis formas previstas no banco
      e correção de fechamento preservando o histórico.
- [x] Validar concorrência, repetição de requisições e falhas antes do commit.

### Front-end

- [x] Criar abertura de caixa, recebimento, comprovante simples e fechamento.
- [x] Exibir consumo, ajustes, recebido, troco e saldo de forma compreensível.
- [x] Solicitar motivo e permissão nas operações que exigem justificativa.
- [x] Priorizar as operações de caixa no programa Java, usando a mesma API
      e as mesmas regras aplicadas ao atendimento dos celulares.

**Concluída quando:** for possível simular um turno completo, receber uma conta,
liberar a mesa e conferir o caixa, incluindo cenários de pagamento simultâneo.
O comprovante simples não representa emissão fiscal.

## Fase 5 — Conferência e uso piloto

- [x] Disponibilizar treinamento persistente em banco separado, com acesso próprio,
      identificação nas telas e dados fictícios para o exercício acompanhado.
- [x] Preparar roteiro do piloto, guia para Android e diagnóstico de rede somente
      de leitura. A validação física no aparelho continua pendente.
- [x] Criar consultas de vendas, pagamentos e fechamento com filtro de período
      e fuso do estabelecimento; manter cálculos coerentes com as views do banco.
- [x] Completar gestão de usuários, perfis e permissões necessária ao piloto.
- [x] Validar o fluxo completo no programa Java e no navegador do celular,
      incluindo a restauração de backup.
- [x] Consolidar inicialização, parada e diagnóstico em instruções para iniciantes.
- [ ] Conferir acesso por celular/tablet na rede local,
      incluindo configuração de rede e proteção da sessão.
- [ ] Desconectar somente a internet e simular login, pedido, produção,
      recebimento e fechamento; confirmar que nenhum recurso exige a nuvem.
- [ ] Testar reinício dos serviços e do computador sem internet, com imagens,
      runtime e arquivos da interface já instalados.
- [x] Simular perda do Wi-Fi durante um envio e reconexão; conferir que a tela
      distingue pedido confirmado de envio sem resultado conhecido, sem duplicação.
- [x] Conferir que fechar a janela Java mantém o serviço para os celulares.
- [ ] Fazer simulação com o proprietário antes de registrar operações reais.
- [ ] Definir onde o sistema ficará instalado, responsável pelos backups e
      como recuperar o serviço após desligamento ou falha.

**Concluída quando:** o proprietário conseguir operar o fluxo principal com
o guia, os cenários críticos estiverem validados e houver recuperação documentada.

## Duas entregas adicionais — preparação e recuperação

### Etapa 1 — Preparação do computador e acesso Android

- [x] Tela **Preparação**, restrita ao administrador, com contagem dos cadastros.
- [x] Endereço do Android e QR Code gerados localmente, sem serviço externo.
- [x] Preferir o endereço da interface com gateway na seleção inicial.
- [x] Disponibilizar somente o certificado público para transferência ao Android.
- [x] Preservar o perfil **Público** do Wi-Fi residencial no Windows; preparar
      regras HTTPS limitadas ao Java, à interface conferida e à sub-rede local.
- [ ] Aplicar as regras com a confirmação administrativa do Windows e conferir
      certificado, QR Code e login em um Android físico.

### Etapa 2 — Backup automático e recuperação

- [x] Cópia automática a cada 60 minutos enquanto o serviço de operação estiver
      em uso; fechar a janela mantém o serviço e os backups ativos.
- [x] Solicitação pela tela **Preparação** e exclusão mútua com o backup manual.
- [x] Conferir cada cópia por checksum e restauração em banco temporário,
      comparando as contagens de todas as tabelas com o snapshot original.
- [x] Seleção de segunda pasta por **ferramentas/Configurar Backups.cmd**, conferência
      de arquivo por **ferramentas/Conferir Backup.cmd** e pausa explícita dos automáticos.
- [x] Documentar recuperação sem substituir automaticamente o banco real em
      [BACKUP-E-RECUPERACAO.md](BACKUP-E-RECUPERACAO.md).
- [ ] Escolher um destino em outro disco, testar a segunda cópia e definir
      quem será responsável por conferir os backups.

O treinamento não solicita nem agenda cópias do banco de operação. Nenhum
backup antigo é excluído automaticamente. A escolha de um segundo destino
fica com o proprietário; a cópia local já está ativa.

## Entrega anterior — acabamento do front-end

- [x] Renovar o login com a identidade visual do Bar do Zico.
- [x] Organizar a navegação no computador e no celular, respeitando as permissões.
- [x] Acrescentar busca e filtros de mesas para facilitar o atendimento.
- [x] Apresentar um guia para orientar o preenchimento dos cadastros.
- [x] Aplicar estilos compartilhados às demais telas, com campos, ações,
      mensagens e estados mais claros.
- [x] Atualizar a barra da janela Java com identidade visual, botões de
      reconexão/certificado Android e indicação do estado da conexão.
- [x] Compilar os módulos integrados com Java 25, preservando os POMs existentes.
- [x] Criar atalhos **Bar do Zico** e **Bar do Zico - Treinamento** na Área de
      Trabalho e o utilitário **Criar Atalhos.cmd** para recriá-los.
- [x] Manter o último modo de cada ambiente ao abrir a janela, inclusive
      HTTPS local, sem exigir parada manual para entrar pelo computador.
- [x] Validar visual e funcionamento no computador e no Android emulado:
      17 testes de interface por modo, 13 testes da API no treinamento e
      renderização do executável Java 25/JavaFX 25.0.1 por HTTPS local.

Esta entrega preserva a API, as regras de operação e os cadastros existentes.
O acabamento da interface não substitui o piloto com o proprietário nem a
validação em Android físico. O preenchimento manual das mesas/cardápio e a
escolha de outro disco para backups continuam pendentes.

## Entrega atual — interface simples para o proprietário

- [x] Aumentar letras, campos, botões e contraste para facilitar a leitura.
- [x] Usar nomes diretos: Mesas, Cozinha e bar, Mesas e cardápio, Caixa,
      Vendas, Funcionários, Configurações e Meu acesso.
- [x] Orientar atendimento e caixa com passos curtos na própria tela.
- [x] Mostrar Editar e Excluir em cada mesa e na lista de cadastros.
- [x] Pedir confirmação para excluir, bloquear mesas com atendimento aberto
      e preservar o histórico de pedidos e pagamentos.
- [x] Permitir recuperar a mesma mesa em Mesas e cardápio → Mesas excluídas.
- [x] Cadastrar funcionários com nome, usuário, senha e função, sem pedir e-mail.
- [x] Aceitar senha de pelo menos 4 caracteres para funcionários e 6 para
      administrador; preservar o login anterior por e-mail e as senhas existentes.
- [x] Liberar as mesas atuais ao cadastrar um atendente, quando a opção estiver
      marcada; manter a escolha individual disponível para mesas novas.
- [x] Acrescentar a V12, sem modificar as migrações anteriores.

A exclusão retira a mesa do salão e permite recuperação. Nenhum histórico de
venda é apagado. Um número de mesa excluído deve ser reutilizado pela recuperação
do cadastro existente. A atualização preserva usuários, senhas e os dados
preenchidos manualmente pelo proprietário.

## Regras técnicas que não podem se perder

- Novas alterações do banco entram em novas migrações; preservar as 14 existentes.
- Usar `BigDecimal` no Java e valores monetários como strings no contrato HTTP.
- Manter UTC no armazenamento e fuso do estabelecimento na apresentação.
- Usar `READ COMMITTED` nas escritas financeiras, conforme o contrato do banco.
- Respeitar a ordem de bloqueio: sessão de caixa, conta, pagamento original.
- Confirmar sucesso ao cliente apenas depois do commit da transação.
- Coordenar conta, mesa, auditoria e outbox dentro da transação de negócio.
- Implementar idempotência completa: validar autorização e conteúdo da solicitação,
  guardar o resultado e devolver a resposta original quando ela for repetida.
- Filtrar leituras e validar escritas pela unidade autenticada. As chaves
  estrangeiras existentes não substituem a autorização da API.
- Não duplicar no Java totais com regras diferentes das views oficiais.
- Preservar registros financeiros e históricos; corrigir pelos fluxos de compensação.
- Testar operações financeiras com PostgreSQL real em banco descartável.
- Não publicar credenciais, imprimir senhas nem usar a senha de migração na API.

## Informações a confirmar no momento adequado

Não é necessário responder agora. Perguntar quando a informação for necessária
para a próxima entrega e continuar as tarefas independentes.

| Informação | Antes de |
|---|---|
| Quantidade e identificação das mesas; áreas do bar | Cadastro real na fase 2 |
| Cardápio, preços, complementos e destinos de preparo | Catálogo na fase 2 e produção na fase 3 |
| Funcionários e o que cada perfil pode fazer | Liberação de acesso a outros usuários |
| Política de serviço, desconto, cancelamento e estorno | Regras de atendimento e caixa |
| Terminais de caixa e formas usadas no dia a dia | Configuração da fase 4 |
| Computadores, celulares e tablets que usarão as telas | Validação de uso e rede |
| Android confirmado; modelo/versão e necessidade de aplicativo instalado ainda a conferir | Configuração do aparelho físico |
| Roteador e cobertura Wi-Fi no salão e na produção | Validação do atendimento local |
| Necessidade de impressora, emissão fiscal ou integrações | Planejamento dessas entregas adicionais |

## Fora da primeira entrega operacional

Estoque por produto foi implementado. Receitas, ingredientes e controle de
estoque de complementos permanecem fora desta entrega.
Compras, fornecedores, delivery, integração com pagamento, emissão
fiscal, operação do celular sem conexão com o computador e publicação na
internet exigem escopo próprio. Funcionar sem internet, mantendo a rede local,
faz parte do escopo principal.
Selecionar uma forma de pagamento na tela registra o recebimento; cobrar
automaticamente por Pix ou cartão dependerá de uma integração futura.

## Primeiro passo quando retomarmos

Avaliar a interface funcional com o proprietário, seguindo
[COMO-USAR-NOVO-FRONT.md](COMO-USAR-NOVO-FRONT.md). Instalar o APK no Android físico,
validar o acesso pela rede local e o uso com queda da internet externa. Preservar
os dados, a assinatura dos APKs e os certificados locais nas próximas alterações.

Para os exercícios com a versão atual:

Abrir **Iniciar Treinamento.cmd**, consultar **ferramentas/Acesso do Treinamento.cmd** e
seguir [PILOTO.md](PILOTO.md). Cadastrar mesas e cardápio reais manualmente
no ambiente de operação depois de praticar. Configurar os Android físicos e
confirmar rede, certificado e firewall. As partes implementadas das fases 2
a 4 estão registradas na validação; concluir a homologação exige revisar as
políticas reais de atendimento/caixa com o proprietário.

## Registro histórico de retomada — V13/V14 (superado pela V17 acima)

O registro abaixo conserva a entrega anterior. Para a retomada atual, usar a
direção V17 no início deste documento; seus números e fluxos antigos não
comprovam a validação das alterações atuais.

- **Última entrega:** novo front com cinco áreas, atendimento em tela própria,
  caixa organizado, funcionários sem credenciais, perfis/permissões e estoque
  por produto com histórico. Abertura sem login. APKs assinados de operação e
  treinamento. Migrações V13/V14 aplicadas sem modificar V1–V12.
- **Estado atual:** implementação entregue para avaliação e piloto acompanhado.
- **Próxima tarefa:** instalar o APK em Android físico, conferir firewall e Wi-Fi,
  validar a queda da internet externa, revisar o uso com o proprietário e escolher
  a segunda pasta de backup.
- **Verificações desta entrega:** 29 testes Java e 22 testes de tela aprovados
  nos formatos computador e Android; rodadas em operação e treinamento. Android
  compilado/assinado e lint aprovado; APK release aberto no emulador em HTTPS.
  Executável Java 25/JavaFX 25 renderizou Mesas sem login. Backup restaurado antes
  da atualização; hashes das colunas anteriores das 35 tabelas fora do Flyway
  comparados nos dois ambientes, preservando os registros anteriores.
- **Arquitetura Android:** Java/WebView, AGP 8.13.2, Gradle 8.13, SDK 35, Android 8+.
  JDK 21 só no processo de build Android; computador/API continuam Java 25.
  Chave de atualização privada em `.tools/android-signing`; APKs em `output/android`.
- **Histórico da entrega anterior:** 13 testes Java e dez de tela em
  cada modo (operação/treinamento); cinco testes de agendamento/integridade;
  backup real restaurado e comparado às 36 tabelas do snapshot; solicitação
  consumida pelo trabalhador automático. Histórico da base: 31 testes de banco.
  Veja [VALIDACAO-PILOTO.md](VALIDACAO-PILOTO.md).
- **Pendências do proprietário:** Wi-Fi residencial com perfil Público confirmado; regras locais do Windows aplicadas; testar no Android
  físico, preencher cardápio/mesas reais, revisar regras de
  negócio e definir destino externo dos backups.
