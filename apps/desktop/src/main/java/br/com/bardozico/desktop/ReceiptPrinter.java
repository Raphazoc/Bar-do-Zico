package br.com.bardozico.desktop;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.awt.EventQueue;
import java.awt.print.PrinterJob;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.scene.web.WebEngine;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Narrow bridge: the guide stays in memory; only preview and Windows printing. */
public final class ReceiptPrinter {
    private final Stage owner;private final WebEngine engine;private final URI origin;
    private boolean loading;
    private final boolean smoke;
    ReceiptPrinter(Stage owner,WebEngine engine,URI origin,boolean smoke){this.owner=owner;this.engine=engine;this.origin=origin;this.smoke=smoke;}
    private boolean trustedPage(){
        try {
            URI page=URI.create(engine.getLocation());
            return Objects.equals(page.getScheme(),origin.getScheme())&&Objects.equals(page.getHost(),origin.getHost())
                &&page.getPort()==origin.getPort()&&page.getRawUserInfo()==null;
        }catch(IllegalArgumentException e){return false;}
    }
    public void preview(String accountId) {
        if(!Platform.isFxApplicationThread()||!trustedPage()||loading)return;
        final UUID id;
        try {id=UUID.fromString(accountId);if(!id.toString().equals(accountId))throw new IllegalArgumentException();}
        catch(IllegalArgumentException e){error("Selecione uma mesa válida antes de imprimir.");return;}
        loading=true;
        var progress=new Stage();progress.initOwner(owner);progress.initModality(Modality.NONE);progress.setTitle("Guia da mesa");
        var wait=new Label("Preparando a guia da mesa…");wait.setStyle("-fx-padding: 32; -fx-font-size: 18;");progress.setScene(new Scene(new StackPane(wait),400,130));progress.show();
        Thread.startVirtualThread(()->{
            try {
                var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER);
                if(CookieHandler.getDefault()!=null)client.cookieHandler(CookieHandler.getDefault());
                var request=HttpRequest.newBuilder(origin.resolve("/api/accounts/"+id+"/guide.pdf")).timeout(Duration.ofSeconds(30))
                    .header("Accept","application/pdf").header("User-Agent","BarDoZicoDesktop/0.2.4").GET().build();
                var response=client.build().send(request,HttpResponse.BodyHandlers.ofInputStream());
                byte[] pdf;
                try(var body=response.body()) {
                    if(response.statusCode()!=200||!response.headers().firstValue("Content-Type").orElse("").startsWith("application/pdf"))throw new IOException("Não foi possível consultar a guia. Confira a conexão e as permissões no computador.");
                    pdf=body.readNBytes(20*1024*1024+1);
                    if(pdf.length>20*1024*1024)throw new IOException("A guia excedeu o tamanho permitido para prévia.");
                }
                var images=new ArrayList<Image>();int count;
                try(var doc=Loader.loadPDF(pdf)) {
                    count=doc.getNumberOfPages();var renderer=new PDFRenderer(doc);
                    for(int p=0;p<Math.min(count,10);p++) {
                        var bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(renderer.renderImageWithDPI(p,130),"png",bytes);
                        images.add(new Image(new ByteArrayInputStream(bytes.toByteArray())));
                    }
                }
                final int pages=count;
                Platform.runLater(()->{loading=false;progress.close();show(id,pdf,images,pages);});
            }catch(Exception failure){Platform.runLater(()->{loading=false;progress.close();error(failure instanceof IOException?failure.getMessage():"Não foi possível preparar a guia. Confira a conexão e tente novamente.");});}
        });
    }
    private void show(UUID id,byte[] pdf,List<Image> images,int pageCount) {
        var stage=new Stage();stage.initOwner(owner);stage.initModality(Modality.NONE);stage.setTitle("Guia da mesa · Papel de 80 mm");
        var pages=new VBox(16);pages.setAlignment(Pos.TOP_CENTER);pages.setStyle("-fx-padding: 20; -fx-background-color: #e3e8e6;");
        for(Image image:images){var view=new ImageView(image);view.setPreserveRatio(true);view.setFitWidth(340);pages.getChildren().add(view);}
        if(pageCount>images.size())pages.getChildren().add(new Label("Prévia das primeiras 10 páginas. A impressão inclui todas as "+pageCount+" páginas."));
        var scroll=new ScrollPane(pages);scroll.setFitToWidth(true);
        var heading=new Label("Confira a guia antes de imprimir");heading.setStyle("-fx-font-size: 22; -fx-font-weight: bold;");
        var help=new Label("Papel térmico de 80 mm. Confira os produtos e o total.\nClique em Imprimir e escolha a impressora instalada no Windows.");help.setWrapText(true);
        var top=new VBox(8,heading,help);top.setStyle("-fx-padding: 20;");
        var print=new Button("Imprimir");var close=new Button("Fechar");
        var actions=new HBox(12,print,close);actions.setAlignment(Pos.CENTER_RIGHT);actions.setStyle("-fx-padding: 16 20;");
        var root=new BorderPane(scroll,top,null,actions,null);root.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 15;");
        for(var button:List.of(print,close))button.setStyle("-fx-padding: 12 20; -fx-background-radius: 7; -fx-background-color: #173f35; -fx-text-fill: white; -fx-cursor: hand;");
        stage.setScene(new Scene(root,560,Math.min(780,Math.max(520,owner.getHeight()-30))));stage.setMinWidth(480);stage.setMinHeight(440);
        close.setCancelButton(true);close.setOnAction(e->stage.close());
        print.setOnAction(e->{
            actions.setDisable(true);stage.setOnCloseRequest(event->event.consume());
            EventQueue.invokeLater(()->{
                String result="Impressão cancelada. A mesa continua igual.";
                try(var document=Loader.loadPDF(pdf)) {
                    if(PrinterJob.lookupPrintServices().length==0)throw new IOException("Nenhuma impressora instalada.");
                    var job=PrinterJob.getPrinterJob();job.setJobName("Bar do Zico - Guia da mesa");job.setPageable(new PDFPageable(document));
                    if(job.printDialog()){job.print();result="Guia enviada à impressora. Confira a saída do papel.";}
                }catch(Exception failure){result="Não foi possível imprimir. Confira a impressora instalada no Windows e o papel de 80 mm.";}
                String message=result;Platform.runLater(()->{actions.setDisable(false);stage.setOnCloseRequest(null);help.setText(message);});
            });
        });
        stage.show();
        stage.setX(owner.getX()+12);
        if(smoke) {
            var delay=new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
            delay.setOnFinished(e->{
                try {
                    Files.write(Path.of(".tools/desktop-receipt-smoke.pdf"),pdf);
                    var snapshot=root.snapshot(null,null);int w=(int)snapshot.getWidth(),h=(int)snapshot.getHeight();int[] pixels=new int[w*h];
                    snapshot.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);
                    var capture=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);capture.setRGB(0,0,w,h,pixels,0,w);
                    javax.imageio.ImageIO.write(capture,"png",Path.of(".tools/desktop-receipt-smoke.png").toFile());
                    Files.writeString(Path.of(".tools/desktop-receipt-smoke.txt"),"OK: prévia nativa de 80 mm, "+pageCount+" página(s), PDF consultado pela mesma origem.");
                }catch(IOException failure){throw new UncheckedIOException(failure);}finally{Platform.exit();}
            });delay.play();
        }
    }
    private void error(String message){
        if(smoke){try{Files.writeString(Path.of(".tools/desktop-receipt-smoke.txt"),"FALHA: "+message);}catch(IOException ignored){}Platform.exit();return;}
        var alert=new Alert(Alert.AlertType.ERROR,message,ButtonType.OK);alert.initOwner(owner);alert.setHeaderText("Guia da mesa");alert.showAndWait();
    }
}
