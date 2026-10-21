package br.com.bardozico;

import java.io.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;

/** The same 80 mm document is previewed, saved and printed. No business writes. */
final class TableGuide {
    static final float WIDTH=80*72/25.4f, MARGIN=12, BODY=WIDTH-2*MARGIN;
    record Item(String name,BigDecimal quantity,BigDecimal unitPrice,BigDecimal total,List<String> extras) {}
    record Adjustment(String label,BigDecimal amount) {}
    record Data(String bar,String table,String opened,String closed,String issued,String status,boolean training,
                List<Item> items,List<Adjustment> adjustments,BigDecimal subtotal,BigDecimal total,BigDecimal paid,BigDecimal balance) {}
    private record Line(String left,String right,boolean bold,float size,float height) {}
    private final PDFont regular,bold;
    private final List<Line> lines=new ArrayList<>();
    private final Set<Integer> blockStarts=new HashSet<>();
    private TableGuide(PDDocument doc)throws IOException {
        try(var r=TableGuide.class.getResourceAsStream("/fonts/NotoSans-Regular.ttf");var b=TableGuide.class.getResourceAsStream("/fonts/NotoSans-Bold.ttf")) {
            regular=PDType0Font.load(doc,Objects.requireNonNull(r));bold=PDType0Font.load(doc,Objects.requireNonNull(b));
        }
    }
    static byte[] pdf(Data data)throws IOException {
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()) {
            doc.getDocumentInformation().setTitle("Guia da mesa - "+data.table());
            var guide=new TableGuide(doc);guide.body(data);guide.pages(doc,data);doc.save(out);return out.toByteArray();
        }
    }
    static String money(BigDecimal n){return NumberFormat.getCurrencyInstance(Locale.of("pt","BR")).format(n).replace('\u00a0',' ');}
    static String quantity(BigDecimal n){return n.stripTrailingZeros().toPlainString().replace('.',',');}
    private String safe(String text,PDFont font)throws IOException {
        var result=new StringBuilder();
        for(int cp:Objects.toString(text,"").codePoints().toArray()) {
            String c=Character.isISOControl(cp)?" ":new String(Character.toChars(cp));
            try {font.encode(c);result.append(c);}catch(IllegalArgumentException unsupported){result.append('?');}
        }
        return result.toString();
    }
    private float width(String text,PDFont font,float size)throws IOException{return font.getStringWidth(text)/1000*size;}
    private void row(String left,String right,boolean strong,float size)throws IOException {
        PDFont font=strong?bold:regular;String text=safe(left,font),value=safe(right,font);
        float available=BODY-(value.isEmpty()?0:width(value,font,size)+8);
        if(available<40)throw new IOException("Valor fora do espaço da guia.");
        // Wrap by actual embedded-font width, including long words without spaces.
        var wrapped=new ArrayList<String>();var current=new StringBuilder();
        for(int cp:text.codePoints().toArray()) {
            String c=new String(Character.toChars(cp));
            if(width(current+c,font,size)>available&&!current.isEmpty()) {
                int space=current.lastIndexOf(" ");
                if(space>0){wrapped.add(current.substring(0,space));current=new StringBuilder(current.substring(space+1));}
                else {wrapped.add(current.toString());current.setLength(0);}
            }
            current.append(c);
        }
        wrapped.add(current.toString().strip());
        for(int i=0;i<wrapped.size();i++)lines.add(new Line(wrapped.get(i),i==wrapped.size()-1?value:"",strong,size,size+4));
    }
    private void text(String text,boolean strong)throws IOException{row(text,"",strong,9);}
    private void gap(){lines.add(new Line("","",false,9,7));}
    private void body(Data d)throws IOException {
        text("Abertura: "+d.opened(),false);
        if(!d.closed().isEmpty())text("Encerramento: "+d.closed(),false);
        text("Emitida em: "+d.issued(),false);text(d.status(),true);gap();
        row("PRODUTOS","VALOR",true,9);
        if(d.items().isEmpty())text("Nenhum produto nesta mesa.",false);
        for(Item i:d.items()) {
            blockStarts.add(lines.size());
            gap();text(i.name(),true);
            row(quantity(i.quantity())+" x "+money(i.unitPrice()),money(i.total()),false,9);
            for(String extra:i.extras())text(extra,false);
            if(!i.extras().isEmpty())text("Adicionais já incluídos no unitário.",false);
        }
        blockStarts.add(lines.size());
        gap();row("Subtotal",money(d.subtotal()),false,9);
        for(Adjustment a:d.adjustments())row(a.label(),money(a.amount()),false,9);
        gap();row("TOTAL",money(d.total()),true,12);
        if(d.paid().signum()!=0){row("Já recebido (líquido)",money(d.paid()),false,9);row("Falta receber",money(d.balance()),true,10);}
        gap();text("Conferência de consumo - Não fiscal",false);
        if(d.status().equals("Conta aberta"))text("Esta guia não confirma pagamento.",false);
        text("Obrigado pela preferência!",false);
    }
    private void pages(PDDocument doc,Data d)throws IOException {
        var chunks=new ArrayList<List<Line>>();var chunk=new ArrayList<Line>();float used=0;
        for(int index=0;index<lines.size();index++) {
            Line line=lines.get(index);
            if(blockStarts.contains(index)&&!chunk.isEmpty()) {
                float block=0;for(int end=index;end<lines.size()&&(end==index||!blockStarts.contains(end));end++)block+=lines.get(end).height();
                if(block<=620&&used+block>620){chunks.add(chunk);chunk=new ArrayList<>();used=0;}
            }
            if(used+line.height()>620&&!chunk.isEmpty()){chunks.add(chunk);chunk=new ArrayList<>();used=0;}
            chunk.add(line);used+=line.height();
        }
        if(!chunk.isEmpty())chunks.add(chunk);
        // Repeat the bar/table and training mark on every page; totals appear once.
        List<Line> body=new ArrayList<>(lines);lines.clear();
        if(d.training())text("TREINAMENTO - SEM VALOR REAL",true);
        text(d.bar(),true);text(d.table(),true);text("GUIA DA MESA - 80 mm",false);gap();
        List<Line> header=new ArrayList<>(lines);lines.clear();lines.addAll(body);
        for(int p=0;p<chunks.size();p++) {
            float height=Math.max(200,(float)(header.stream().mapToDouble(Line::height).sum()+chunks.get(p).stream().mapToDouble(Line::height).sum())+2*MARGIN+23);
            var page=new PDPage(new PDRectangle(WIDTH,height));doc.addPage(page);
            try(var stream=new PDPageContentStream(doc,page)) {
                float y=height-MARGIN;
                var all=new ArrayList<>(header);all.addAll(chunks.get(p));
                for(Line line:all) {
                    y-=line.height();PDFont font=line.bold()?bold:regular;
                    draw(stream,line.left(),MARGIN,y,font,line.size());
                    if(!line.right().isEmpty())draw(stream,line.right(),WIDTH-MARGIN-width(line.right(),font,line.size()),y,font,line.size());
                }
                draw(stream,"Página "+(p+1)+" de "+chunks.size(),MARGIN,10,regular,8);
            }
        }
    }
    private void draw(PDPageContentStream s,String value,float x,float y,PDFont font,float size)throws IOException {
        if(value.isEmpty())return;s.beginText();s.setFont(font,size);s.newLineAtOffset(x,y);s.showText(value);s.endText();
    }
}
