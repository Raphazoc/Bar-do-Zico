from html import escape
from pathlib import Path
import re

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    HRFlowable,
    Image,
    ListFlowable,
    ListItem,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs" / "TUTORIAL-USO-BAR-DO-ZICO.md"
IMAGE_DIR = ROOT / "output" / "interface" / "novo-front"

FIGURES = {
    "3. Abrir o sistema no computador": (
        "windows-programa.png",
        "Figura 1 — Tela principal do programa no Windows.",
    ),
    "4. Configurar o Android": (
        "android-apk-mesas.png",
        "Figura 2 — Tela de mesas no aplicativo Android.",
    ),
    "5. Preparar mesas e locais": (
        "computador-mesas.png",
        "Figura 3 — Administração de mesas no computador.",
    ),
    "6. Abrir atendimento e lançar consumo": (
        "celular-atendimento.png",
        "Figura 4 — Atendimento e consumo no celular.",
    ),
    "7. Trabalhar com estoque e cardápio": (
        "computador-estoque.png",
        "Figura 5 — Controle de estoque no computador.",
    ),
    "8. Abrir e usar o caixa": (
        "computador-caixa.png",
        "Figura 6 — Operação do caixa no computador.",
    ),
    "9. Guia da mesa e impressão": (
        "computador-atendimento.png",
        "Figura 7 — Atendimento no computador e acesso à guia.",
    ),
}

DOCUMENTS = {
    "admin": {
        "destination": ROOT / "output" / "Bar-do-Zico-Tutorial-Administracao.pdf",
        "title": "Tutorial de administração",
        "subtitle": "Como administrar mesas, caixa, equipe, estoque e configurações.",
        "sections": {
            "1. Para que serve o sistema",
            "2. Regra mais importante: PC e celular têm funções diferentes",
            "3. Abrir o sistema no computador",
            "4. Configurar o Android",
            "5. Preparar mesas e locais",
            "7. Trabalhar com estoque e cardápio",
            "8. Abrir e usar o caixa",
            "9. Guia da mesa e impressão",
            "10. Consultar vendas e histórico",
            "11. Funcionários e permissões",
            "12. Rotina recomendada",
            "13. Limitações conhecidas",
            "14. Ajuda rápida",
            "15. Atalhos principais",
        },
    },
    "attendance": {
        "destination": ROOT / "output" / "Bar-do-Zico-Tutorial-Atendimento.pdf",
        "title": "Tutorial de atendimento",
        "subtitle": "Como conectar o celular, abrir mesas e lançar o consumo.",
        "sections": {
            "1. Para que serve o sistema",
            "2. Regra mais importante: PC e celular têm funções diferentes",
            "4. Configurar o Android",
            "5. Preparar mesas e locais",
            "6. Abrir atendimento e lançar consumo",
            "12. Rotina recomendada",
            "13. Limitações conhecidas",
            "14. Ajuda rápida",
        },
        "overrides": {
            "5. Preparar mesas e locais": [
                "O atendimento pode cadastrar uma mesa nova, mas não edita, exclui ou reorganiza mesas e locais. Essas tarefas ficam no computador administrador.",
                "Para cadastrar uma mesa, abra Mesas, toque em Adicionar mesa, informe o número ou nome, escolha o local quando disponível e toque em Salvar mesa.",
                "Se ainda não houver local cadastrado, o primeiro cadastro usa o local Salão. Se já houver locais, o sistema usa o primeiro local ativo quando nenhum for escolhido.",
                "Depois de salvar, a mesa aparece no celular e no computador. Se o número já existir, escolha outro.",
            ],
            "6. Abrir atendimento e lançar consumo": [
                "Em Mesas, localize uma mesa livre e toque em Abrir mesa. Em uma mesa já aberta, toque em Ver consumo.",
                "Para adicionar um produto, escolha a categoria, escolha o produto, informe a quantidade, selecione os complementos disponíveis e toque em Adicionar à mesa.",
                "O lançamento é enviado ao servidor e aparece no consumo da mesa. Não é necessário enviar para cozinha, preparar ou entregar: nesta versão o atendimento registra diretamente o consumo.",
                "O atendimento não recebe pagamentos, não aplica descontos, não fecha contas, não remove produtos e não altera estoque ou cardápio. Se houver um erro, solicite a correção ao administrador no computador.",
                "Se a conexão cair, não toque várias vezes em enviar. Reconecte e use Conferir envio para verificar se o lançamento foi registrado.",
            ],
            "12. Rotina recomendada": [
                "Antes de começar, confira se o aplicativo mostra Atendimento ou TREINAMENTO — Atendimento e se o endereço do servidor está correto.",
                "Durante o atendimento, abra a mesa correta, lance cada produto uma vez e confira o consumo exibido.",
                "Depois de qualquer falha de conexão, reconecte e use Conferir envio antes de repetir um lançamento.",
                "Para pagamentos, correções, estoque, cardápio, funcionários ou fechamento da conta, encaminhe a operação ao computador administrador.",
            ],
        },
    },
}


def inline_markup(text: str) -> str:
    text = escape(text, quote=False)
    text = re.sub(r"`([^`]+)`", r"<font name='Courier'>\1</font>", text)
    text = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", text)
    return text


def footer(canvas, document):
    canvas.saveState()
    canvas.setStrokeColor(colors.HexColor("#d8dee8"))
    canvas.line(18 * mm, 14 * mm, 192 * mm, 14 * mm)
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(colors.HexColor("#64748b"))
    canvas.drawString(18 * mm, 9 * mm, "Bar do Zico — Tutorial de operação")
    canvas.drawRightString(192 * mm, 9 * mm, f"Página {document.page}")
    canvas.restoreState()


def build_story(document):
    styles = getSampleStyleSheet()
    styles.add(
        ParagraphStyle(
            name="CoverTitle",
            parent=styles["Title"],
            fontName="Helvetica-Bold",
            fontSize=27,
            leading=33,
            alignment=TA_CENTER,
            textColor=colors.HexColor("#10213c"),
            spaceAfter=12,
        )
    )
    styles.add(
        ParagraphStyle(
            name="CoverSubtitle",
            parent=styles["Normal"],
            fontSize=13,
            leading=19,
            alignment=TA_CENTER,
            textColor=colors.HexColor("#475569"),
            spaceAfter=8,
        )
    )
    styles.add(
        ParagraphStyle(
            name="H1Custom",
            parent=styles["Heading1"],
            fontName="Helvetica-Bold",
            fontSize=18,
            leading=23,
            textColor=colors.HexColor("#10213c"),
            spaceBefore=13,
            spaceAfter=8,
        )
    )
    styles.add(
        ParagraphStyle(
            name="H2Custom",
            parent=styles["Heading2"],
            fontName="Helvetica-Bold",
            fontSize=13,
            leading=17,
            textColor=colors.HexColor("#1d4ed8"),
            spaceBefore=9,
            spaceAfter=5,
        )
    )
    styles.add(
        ParagraphStyle(
            name="BodyCustom",
            parent=styles["BodyText"],
            fontName="Helvetica",
            fontSize=9.8,
            leading=14,
            textColor=colors.HexColor("#1f2937"),
            spaceAfter=6,
        )
    )
    styles.add(
        ParagraphStyle(
            name="SmallCustom",
            parent=styles["BodyText"],
            fontSize=8.5,
            leading=11,
            textColor=colors.HexColor("#475569"),
        )
    )
    styles.add(
        ParagraphStyle(
            name="TableCustom",
            parent=styles["BodyText"],
            fontSize=8.2,
            leading=10,
            textColor=colors.HexColor("#1f2937"),
        )
    )
    styles.add(
        ParagraphStyle(
            name="CaptionCustom",
            parent=styles["BodyText"],
            fontSize=8,
            leading=10,
            alignment=TA_CENTER,
            textColor=colors.HexColor("#64748b"),
            spaceAfter=10,
        )
    )

    story = [
        Spacer(1, 32 * mm),
        Paragraph("Bar do Zico", styles["CoverTitle"]),
        Paragraph(document["title"], styles["CoverTitle"]),
        Spacer(1, 6 * mm),
        Paragraph(
            document["subtitle"],
            styles["CoverSubtitle"],
        ),
        Spacer(1, 16 * mm),
        HRFlowable(width="70%", thickness=1.5, color=colors.HexColor("#1d4ed8")),
        Spacer(1, 16 * mm),
        Paragraph(
            "Este manual descreve o funcionamento da versão local do sistema. "
            "Use o ambiente de treinamento para praticar antes de registrar operações reais.",
            styles["CoverSubtitle"],
        ),
        Spacer(1, 45 * mm),
        Paragraph("Manual de uso", styles["CoverSubtitle"]),
        PageBreak(),
    ]

    lines = SOURCE.read_text(encoding="utf-8").splitlines()
    i = 1
    active_section = None
    override_section = None
    while i < len(lines):
        line = lines[i].strip()
        if not line:
            i += 1
            continue
        if line.startswith("# "):
            i += 1
            continue
        if line.startswith("## "):
            heading = line[3:]
            active_section = heading
            override_section = None
            if heading in document["sections"]:
                story.append(Paragraph(inline_markup(heading), styles["H1Custom"]))
                if heading in document.get("overrides", {}):
                    for paragraph in document["overrides"][heading]:
                        story.append(Paragraph(inline_markup(paragraph), styles["BodyCustom"]))
                    override_section = heading
                else:
                    override_section = None
                figure = FIGURES.get(heading)
                if figure:
                    image_path = IMAGE_DIR / figure[0]
                    if image_path.exists():
                        image = Image(str(image_path))
                        max_width = 174 * mm
                        max_height = 112 * mm
                        scale = min(max_width / image.imageWidth, max_height / image.imageHeight)
                        image.drawWidth = image.imageWidth * scale
                        image.drawHeight = image.imageHeight * scale
                        image.hAlign = "CENTER"
                        story.extend([image, Paragraph(figure[1], styles["CaptionCustom"])])
            i += 1
            continue
        if override_section is not None:
            i += 1
            continue
        if active_section not in document["sections"]:
            i += 1
            continue
        if line.startswith("### "):
            story.append(Paragraph(inline_markup(line[4:]), styles["H2Custom"]))
            i += 1
            continue
        if line.startswith("|") and i + 1 < len(lines) and lines[i + 1].startswith("|---"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                cells = [cell.strip() for cell in lines[i].strip("|").split("|")]
                if not all(set(cell) <= {"-", ":"} for cell in cells):
                    rows.append([Paragraph(inline_markup(cell), styles["TableCustom"]) for cell in cells])
                i += 1
            table = Table(rows, repeatRows=1, colWidths=[45 * mm, 125 * mm])
            table.setStyle(
                TableStyle(
                    [
                        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#10213c")),
                        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                        ("BACKGROUND", (0, 1), (-1, -1), colors.HexColor("#f8fafc")),
                        ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#cbd5e1")),
                        ("VALIGN", (0, 0), (-1, -1), "TOP"),
                        ("LEFTPADDING", (0, 0), (-1, -1), 5),
                        ("RIGHTPADDING", (0, 0), (-1, -1), 5),
                        ("TOPPADDING", (0, 0), (-1, -1), 5),
                        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
                    ]
                )
            )
            story.extend([Spacer(1, 4), table, Spacer(1, 6)])
            continue
        if line.startswith("- "):
            items = []
            while i < len(lines) and lines[i].strip().startswith("- "):
                items.append(ListItem(Paragraph(inline_markup(lines[i].strip()[2:]), styles["BodyCustom"])))
                i += 1
            story.append(ListFlowable(items, bulletType="bullet", leftIndent=14))
            continue
        if re.match(r"^\d+\.\s", line):
            items = []
            while i < len(lines) and re.match(r"^\d+\.\s", lines[i].strip()):
                text = re.sub(r"^\d+\.\s", "", lines[i].strip())
                items.append(ListItem(Paragraph(inline_markup(text), styles["BodyCustom"])))
                i += 1
            story.append(ListFlowable(items, bulletType="1", start="1", leftIndent=18))
            continue
        paragraph = [line]
        i += 1
        while i < len(lines) and lines[i].strip() and not re.match(r"^(#|[-*] |\d+\.\s|\|)", lines[i].strip()):
            paragraph.append(lines[i].strip())
            i += 1
        story.append(Paragraph(inline_markup(" ".join(paragraph)), styles["BodyCustom"]))
    return story


def main():
    for document_config in DOCUMENTS.values():
        destination = document_config["destination"]
        destination.parent.mkdir(parents=True, exist_ok=True)
        document = SimpleDocTemplate(
            str(destination),
            pagesize=A4,
            rightMargin=18 * mm,
            leftMargin=18 * mm,
            topMargin=17 * mm,
            bottomMargin=19 * mm,
            title=f"Bar do Zico — {document_config['title']}",
            author="Bar do Zico",
        )
        document.build(build_story(document_config), onFirstPage=footer, onLaterPages=footer)
        print(destination)


if __name__ == "__main__":
    main()
