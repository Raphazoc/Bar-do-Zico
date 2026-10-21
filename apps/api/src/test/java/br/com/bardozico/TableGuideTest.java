package br.com.bardozico;

import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TableGuideTest {
    static final BigDecimal ZERO=BigDecimal.ZERO;
    private static void sample(String name,byte[] pdf)throws Exception {
        if(!"true".equals(System.getenv("BAR_GUIDE_SAMPLE_OUTPUT")))return;
        var path=java.nio.file.Path.of("../../tmp/pdfs",name);java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.write(path,pdf);
    }
    static TableGuide.Data data(List<TableGuide.Item> items,boolean training){return new TableGuide.Data("BAR DO ZICO","Mesa 1","21/10/2026 14:00","","21/10/2026 14:54","Conta aberta",training,items,List.of(new TableGuide.Adjustment("Desconto",new BigDecimal("-5.00"))),new BigDecimal("40.00"),new BigDecimal("35.00"),new BigDecimal("10.00"),new BigDecimal("25.00"));}
    @Test void thermalWidthAccentsAndTotalsAreReadable()throws Exception {
        byte[] pdf=TableGuide.pdf(data(List.of(new TableGuide.Item("Porção de camarão e açaí",new BigDecimal("2.5"),new BigDecimal("16"),new BigDecimal("40"),List.of("+ Limão (1 por un.; R$ 1,00)"))),true));
        sample("guia-exemplo-80mm.pdf",pdf);
        try(var doc=Loader.loadPDF(pdf)) {
            assertEquals(1,doc.getNumberOfPages());assertEquals(80,doc.getPage(0).getMediaBox().getWidth()*25.4/72,0.01);
            String text=new PDFTextStripper().getText(doc);assertTrue(text.contains("TREINAMENTO"));assertTrue(text.contains("Porção de camarão e açaí"));assertTrue(text.contains("2,5 x R$ 16,00"));
            for(String expected:List.of("TOTAL R$ 35,00","Desconto -R$ 5,00","Já recebido (líquido) R$ 10,00","Falta receber R$ 25,00","Não fiscal","não confirma pagamento"))assertTrue(text.contains(expected),text);
        }
    }
    @Test void longReceiptsPaginateWithoutLosingItemsOrRepeatingTotals()throws Exception {
        var items=new ArrayList<TableGuide.Item>();for(int i=0;i<150;i++)items.add(new TableGuide.Item("Produto "+i+" nome muito comprido "+"X".repeat(90)+" 🍺",BigDecimal.ONE,BigDecimal.TEN,BigDecimal.TEN,List.of()));
        byte[] pdf=TableGuide.pdf(data(items,false));
        sample("guia-longa-verificacao.pdf",pdf);
        try(var doc=Loader.loadPDF(pdf)) {
            assertTrue(doc.getNumberOfPages()>4);String all=new PDFTextStripper().getText(doc);
            for(int i=0;i<150;i++)assertTrue(all.contains("Produto "+i+" nome"));assertEquals(1,all.split("TOTAL R\\$",-1).length-1);
            for(var page:doc.getPages()){assertEquals(80,page.getMediaBox().getWidth()*25.4/72,0.01);assertTrue(page.getMediaBox().getHeight()<850);}
        }
    }
    @Test void emptyAccountStillShowsAValidGuide()throws Exception {
        try(var doc=Loader.loadPDF(TableGuide.pdf(data(List.of(),false)))){assertTrue(new PDFTextStripper().getText(doc).contains("Nenhum produto nesta mesa."));}
    }
}
