# Revisão de rede e Azure — 20/10/2026

## Direção confirmada pelo proprietário

- Windows em Java 25 e Android instalável continuam usando as mesmas telas.
- A conexão residencial mantém o perfil **Público** do Windows.
- O Android também deverá acessar por 4G ou outra rede, usando **Azure**.
- O atendimento local durante uma queda da internet continua no planejamento.
- A abertura local permanece sem login. A autorização dos aparelhos na nuvem
  precisa preservar essa simplicidade sem publicar os dados anonimamente.

Perfil Público do Windows e servidor público na internet são coisas diferentes.
As regras atuais liberam o Java somente na sub-rede residencial. Elas não
publicam o computador na internet e não são suficientes para acesso por 4G.

## Azure: estado consultado na conta

Consulta somente de leitura feita pela Azure CLI oficial, depois de autenticar:

| Item | Resultado |
|---|---|
| Assinatura | Azure subscription 1 |
| ID | `5da250f6-41bb-47db-bdda-7df7ad2fb10c` |
| Situação | Enabled |
| Grupos de recursos | 0 |
| Recursos | 0 |
| Endpoint do Bar do Zico | Ainda não criado |

O inventário está em `.tools/azure-inventory.json`. Essa consulta não criou,
publicou ou alterou recursos. O impedimento anterior de consultar a assinatura
não se repetiu após autenticar a CLI. O login do VS Code era uma sessão separada.

A CLI ZIP 2.91.0 está em `.tools/azure-cli-2.91.0`, sem instalação global.
**Verificar Azure.cmd** permite repetir o inventário. O arquivo é apenas de
consulta; não contém comandos de criação de infraestrutura.

## Correções entregues

1. **QR Code e HTTPS:** a API voltou a aceitar `8443` e `18443`, preservando
   DNS/HTTPS/443 para o Azure. A mudança anterior aceitava somente 443 e causava
   um erro real nos testes do QR local. URLs com credenciais, caminhos, consultas,
   fragmentos ou portas não previstas são rejeitadas.
2. **Android:** não troca um endereço Azure real pelo banco local depois de uma
   falha. Apenas o antigo endereço fictício exato é descartado. Mensagens deixam
   claro que 4G depende de um servidor remoto configurado. Erros HTTP não são
   apresentados como conexão bem-sucedida. Endereços e portas são validados;
   navegação externa, HTTP e falhas de certificado continuam bloqueados.
3. **Compilação Android:** valida o endereço antes de compilar, rejeita o hostname
   fictício e escapa o valor gerado no BuildConfig. APKs atualizados para 0.2.1,
   código 2, com a mesma chave de assinatura.
4. **Inicialização local:** os atalhos comuns já iniciam HTTPS para computador e
   celular. Mudança de versão/IP/modo reinicia somente a API deste projeto,
   conferindo a identidade do processo. Não é mais necessário parar manualmente
   o modo HTTP para abrir o modo de rede. O modo restrito ao computador permanece
   disponível com `-SomenteComputador` ou `--somente-computador`.
5. **Windows:** o executável usa HTTPS local por padrão, encontra a CA pública
   desta instalação e mostra orientação quando o serviço está desligado.
   Ícone de caneca aplicado ao programa e aos atalhos; treinamento tem marca T.
6. **Firewall:** a configuração identifica a conexão física residencial com
   gateway, preserva o perfil existente e mantém restrições de Java/interface/
   sub-rede. Não há mais um número/GUID de adaptador fixo no código. Foi conferida
   com `-WhatIf`; as regras existentes continuam no perfil Público.
7. **Diagnóstico:** confere também o ambiente operação/treinamento, tolera
   metadados inválidos e usa os endereços anunciados pela API ativa. Não anuncia
   somente os endereços de um certificado que pode ter sido atualizado antes
   do reinício do servidor.
8. **Testes Java:** o utilitário seleciona o JDK 25 do projeto, mesmo que o Java
   global do terminal seja 21. A configuração global do Windows é preservada.
9. **Interface:** falha de autorização no servidor explica o impedimento sem
   reintroduzir login. Sessão expirada retira dados e controles anteriores da
   tela; envios pendentes permanecem armazenados no aparelho.
10. **Bootstrap Azure:** exige UUID estável da unidade, serializa execuções
    simultâneas, mantém a senha existente e recusa reativar um administrador
    desativado ou excluído. Migrações continuam sendo uma etapa anterior separada.
11. **Contêiner:** compila frontend e API com Java 25, executa sem root e reconhece
    o HTTPS encaminhado pelo ingress confiável do Azure. Credenciais e chaves
    locais estão excluídas do contexto. Imagem local: `bar-do-zico:revisao-20261020`.

## Verificações

- Antes da correção: 42 testes Java executados, com uma falha no QR local.
- Depois: **58 testes Java/API/endereço aprovados**, sem falhas ou testes pulados.
- **40 testes de interface aprovados**, entre computador e viewport Android,
  incluindo perda de conexão, retomada, permissões, exclusões, caixa e vendas.
- Testes Java Android nas duas variantes: **8 execuções aprovadas**.
- **9 testes Python aprovados**, cobrindo backups, endereços de build e parada
  restrita ao processo do projeto.
- `validar_nuvem.py`: banco temporário real, bootstrap simultâneo e repetido,
  senha preservada, API pública fechada, cookies seguros e escrita autenticada
  atrás de proxy HTTPS aprovados. Esse teste roda localmente e não comprova Azure.
- Imagem Docker compilada com sucesso e runtime UID/GID `10001:10001` conferido.
  O mesmo teste de bootstrap/API/proxy passou também executando a imagem Linux.
- Executável empacotado com Java próprio; Mesas sem login renderizada no JavaFX.
- Operação e treinamento respondem em HTTPS. Certificado validado, sem ignorar TLS.
- Backups de operação e treinamento restaurados em bancos temporários antes da
  instalação. Hashes das **36 tabelas de dados em cada ambiente** permaneceram
  idênticos após a atualização; nenhuma migração existente foi editada.

Registros detalhados, sem publicar senhas, estão nos arquivos
`.tools/revisao-*.log`. Testes funcionais usam bancos descartáveis.

## Para usar agora na rede residencial

1. Abra **Bar do Zico** ou **Bar do Zico - Treinamento** na Área de Trabalho.
   Os atalhos iniciam banco e API e depois abrem a janela.
2. Conecte o celular ao Wi-Fi do mesmo roteador. O PC pode estar no cabo.
3. Instale o APK atualizado de `output/android` por cima do anterior.
4. No APK, toque em **Conexão** e use o endereço mostrado no PC em
   **Configurações → Acesso pelo Android**.

Endereços residenciais conferidos nesta máquina:

- Operação: `https://192.168.0.8:8443`
- Treinamento: `https://192.168.0.8:18443`

O IP pode mudar. O endereço `172.21.112.1` corresponde ao adaptador virtual desta
máquina e não deve ser a primeira escolha do celular. O teste em um Android
físico ainda precisa ser acompanhado.

## Próximas fases da nuvem

| Fase | Entrega | Estado |
|---|---|---|
| 1 — base local | HTTPS residencial, APK/Windows, dados e backups preservados | Validada localmente |
| 2 — piloto Azure | IaC, custos/quotas, Container Apps, Registry, PostgreSQL privado, segredos e URL HTTPS | Planejada; conta consultada, recursos ainda inexistentes |
| 3 — acesso dos aparelhos | Autorizar PC/Android no servidor de teste, revogação, expiração e teste real por 4G | Pendente |
| 4 — operação com queda de internet | Sincronização local/nuvem, reenvios, conflitos de estoque/caixa e recuperação | Pendente; não implementada |
| 5 — operação real | Homologação, backups/restauração Azure e ativação acompanhada | Depende das anteriores |

**Não trocar o banco real por um banco piloto vazio.** Hoje os bancos local e
nuvem não se sincronizam. Somente apontar PC e Android para uma API Azure
centralizada faria o atendimento depender da internet; a fase 4 precisa resolver
isso antes da troca da operação. O Docker local permanece necessário para o modo
residencial atual.

Não existe Bicep/Terraform/`azure.yaml` nem servidor publicado nesta revisão.
`Dockerfile` e planos escritos não criam infraestrutura por si. Custos e quotas
ainda precisam ser avaliados para a assinatura estudantil; nenhuma cobrança de
infraestrutura foi iniciada por este trabalho.

Referências oficiais usadas na revisão:
[Azure CLI para Windows](https://learn.microsoft.com/en-us/cli/azure/install-azure-cli-windows)
e [ingress do Container Apps](https://learn.microsoft.com/en-us/azure/container-apps/ingress).
