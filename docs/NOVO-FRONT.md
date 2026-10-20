# Novo front — referência do proprietário

Referência recebida em 20/10/2026. O proprietário considerou a interface atual
difícil de entender e trabalhar e decidiu pensar em uma nova apresentação visual.
Esta estrutura orientou a implementação autorizada pelo proprietário em
20/10/2026. O novo front e os APKs Android foram entregues. Consulte
[Como usar](COMO-USAR-NOVO-FRONT.md) e [Validação](ENTREGA-NOVO-FRONT.md).

## Abertura sem login

O proprietário solicitou retirar a tela de login do novo front. Ao abrir o
programa, entrar diretamente em **Mesas**, sem pedir usuário, e-mail ou senha.
Essa decisão substitui o fluxo de entrada da interface atual no planejamento
da próxima versão.

O cadastro de funcionários continua na estrutura solicitada. Ao implementar,
adequar a identificação dos responsáveis pelas operações, os perfis e as
permissões ao uso sem tela de login. A abertura direta está implementada;
a identificação voluntária do responsável fica em Configurações.

## Estrutura solicitada

```text
BAR DO ZICO
│
├── MESAS
│   │
│   ├── Lista de mesas
│   │   ├── Buscar mesa
│   │   ├── Filtrar por status
│   │   ├── Adicionar mesa
│   │   ├── Editar mesa
│   │   └── Excluir mesa
│   │
│   └── Atendimento da mesa
│       ├── Abrir atendimento
│       ├── Visualizar consumo
│       ├── Adicionar produto
│       ├── Enviar pedido
│       ├── Acompanhar pedido
│       └── Ir para o caixa
│
├── CAIXA
│   │
│   ├── Contas abertas
│   │   ├── Selecionar mesa
│   │   ├── Visualizar consumo
│   │   ├── Aplicar desconto ou acréscimo
│   │   └── Finalizar mesa
│   │
│   ├── Pagamento
│   │   ├── Dinheiro
│   │   ├── Pix
│   │   ├── Débito
│   │   ├── Crédito
│   │   ├── Voucher
│   │   ├── Outro
│   │   ├── Pagamento parcial
│   │   └── Troco
│   │
│   ├── Movimentações
│   │   ├── Entrada de dinheiro
│   │   ├── Retirada de dinheiro
│   │   └── Correção
│   │
│   └── Fechamento de caixa
│
├── FUNCIONÁRIOS
│   │
│   ├── Lista de funcionários
│   ├── Cadastrar funcionário
│   ├── Editar funcionário
│   ├── Ativar ou desativar funcionário
│   ├── Perfis de acesso
│   └── Permissões
│
├── ESTOQUE
│   │
│   ├── Lista de produtos
│   │   ├── Buscar produto
│   │   ├── Filtrar por categoria
│   │   ├── Filtrar por disponibilidade
│   │   └── Ordenar produtos
│   │
│   ├── Cadastrar produto
│   │   ├── Nome
│   │   ├── Categoria
│   │   ├── Quantidade
│   │   ├── Estoque mínimo
│   │   ├── Preço
│   │   └── Destino: bar, cozinha ou sem preparo
│   │
│   ├── Movimentações
│   │   ├── Entrada
│   │   ├── Saída
│   │   ├── Ajuste
│   │   └── Histórico
│   │
│   └── Alertas
│       ├── Estoque baixo
│       └── Produto sem estoque
│
└── CARDÁPIO
    │
    ├── Produtos disponíveis
    ├── Produtos indisponíveis
    ├── Busca por produto
    ├── Filtro por categoria
    ├── Alterar disponibilidade
    └── Visualizar preço e quantidade
```

## Direção para a retomada

- Usar os cinco grupos principais acima como referência da navegação.
- Abrir diretamente em Mesas, sem tela de login nem solicitação de usuário,
  e-mail ou senha.
- Separar a lista de mesas do atendimento: escolher uma mesa leva à tela
  daquele atendimento; “Ir para o caixa” leva a mesma conta para pagamento.
- Mostrar primeiro a tarefa escolhida, com títulos claros, ações por escrito
  e botões fáceis de encontrar. Evitar reunir todos os formulários na mesma tela.
- Projetar para um proprietário com pouca familiaridade com tecnologia:
  letras legíveis, instruções curtas, poucos passos e retorno visível das ações.
- Manter o cadastro de funcionários sem exigir e-mail ou credenciais de entrada.
- Reaproveitar banco, API e regras já implementadas, preservando dados e histórico.
- Manter Java no computador, Android pela rede local e operação sem internet
  externa quando computador e rede local estiverem funcionando.

## Ajuste solicitado após o uso — 20/10/2026

A decisão mais recente substitui as etapas de preparo/entrega da árvore acima:
**Mesa → Adicionar produto → Adicionar à mesa → Caixa**. O consumo é salvo
imediatamente; não há botão de envio ou acompanhamento da produção. A remoção
de lançamento mantém motivo, histórico e devolução aplicável ao estoque.

Em Estoque, **Excluir** aparece ao lado de Editar e retira o cadastro do estoque,
cardápio e seleção do atendimento, preservando suas vendas e movimentações.
Mesas têm exclusão permanente e número reutilizável, com nome histórico nas
contas antigas. A recuperação de mesas foi retirada.

## Prévias visuais para avaliação

Foram geradas propostas das cinco áreas no computador e das telas Mesas e
Atendimento da mesa no Android. As imagens usam dados de exemplo e estão em
[Prévias do novo front](../output/imagegen/front-proposta-v1/README.md).
Essas imagens são referências visuais. A versão funcional está implementada;
as capturas reais ficam em `output/interface/novo-front/`.

No celular, a proposta usa uma coluna, botões grandes e acesso às áreas pelo
botão Menu. A versão atual mostra o consumo e salva cada adição direto na mesa, com acesso
ao caixa. As imagens antigas podem mostrar etapas retiradas pelo proprietário.

## Planejamento e implementação

A árvore foi implementada usando a API e o catálogo existentes. O estoque
controla a quantidade do produto vendido, registra movimentações e oferece
alertas. Receitas, ingredientes e estoque de complementos exigem outra entrega.

Estoque e cardápio usam o mesmo produto. Adicionar à mesa dá baixa no estoque;
remover um lançamento novo devolve a quantidade. Itens antigos já preparados
conservam a regra de perda ou retorno físico. Produtos controlados sem saldo não podem
ser vendidos. Funcionários, edição, perfis e permissões estão implementados.

Ao retomar: avaliar a versão funcional com o proprietário, instalar o APK no
Android físico, conferir o acesso pelo Wi-Fi do bar e concluir o piloto.
Os resultados específicos desta entrega estão em ENTREGA-NOVO-FRONT.md.
