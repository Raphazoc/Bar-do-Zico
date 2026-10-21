package br.com.bardozico.desktop;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import java.nio.file.*;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

public class BarDesktop extends Application {
    private ReceiptPrinter receiptPrinter;
    private static final String FOREST = "#173f35";
    private static final String CREAM = "#f5f4ee";
    private static final String GOLD = "#c8a76b";

    public static void main(String[] args) {launch(args);}
    @Override public void start(Stage stage) {
        Path project=projectDirectory();
        String configuredUrl=System.getenv().getOrDefault("BAR_DESKTOP_URL","https://127.0.0.1:8443").strip();
        String url=configuredUrl.endsWith("/")?configuredUrl.substring(0,configuredUrl.length()-1):configuredUrl;
        java.net.URI origin=java.net.URI.create(url);
        if(origin.getHost()==null||origin.getRawUserInfo()!=null||origin.getRawQuery()!=null||origin.getRawFragment()!=null
            ||origin.getPort()==0||origin.getPort()>65535
            ||(origin.getPath()!=null&&!origin.getPath().isEmpty()&&!origin.getPath().equals("/"))
            ||!("https".equals(origin.getScheme())||("http".equals(origin.getScheme())&&"127.0.0.1".equals(origin.getHost()))))
            throw new IllegalArgumentException("Use endereço local ou uma origem HTTPS válida.");
        Path trust=project.resolve(".tools/tls/trust.p12");
        if("https".equals(origin.getScheme())&&"127.0.0.1".equals(origin.getHost())&&Files.isRegularFile(trust)
            &&System.getProperty("javax.net.ssl.trustStore")==null) {
            System.setProperty("javax.net.ssl.trustStore",trust.toString());
            System.setProperty("javax.net.ssl.trustStorePassword","changeit");
        }
        var web=new WebView();web.setContextMenuEnabled(false);
        boolean receiptSmoke=getParameters().getRaw().stream().anyMatch(p->p.startsWith("--receipt-smoke-test="));
        receiptPrinter=new ReceiptPrinter(stage,web.getEngine(),origin,receiptSmoke);
        var message=new Label("Conectando ao serviço do bar…");
        message.setWrapText(true);
        message.setStyle("-fx-text-fill: #40544c; -fx-font-size: 12px;");
        var status=new Circle(4,Color.web(GOLD));
        var retry=new Button("Reconectar");retry.setOnAction(e->{
            status.setFill(Color.web(GOLD));message.setText("Conectando ao serviço do bar…");
            web.getEngine().load(url);
        });
        styleButton(retry,GOLD,"#dcc08d",FOREST,GOLD);
        retry.setTooltip(new Tooltip("Tentar conectar novamente ao serviço do bar"));
        var certificateButton=new Button("Certificado Android");certificateButton.setOnAction(e->{
            try {
                Path folder=project.resolve("output/android");
                if(!Files.isRegularFile(folder.resolve("bar-ca.cer"))) {message.setText("Inicie o modo de rede para preparar o certificado público.");return;}
                java.awt.Desktop.getDesktop().open(folder.toFile());
            }catch(Exception failure){message.setText("Abra a pasta output/android do projeto para copiar o certificado.");}
        });
        styleButton(certificateButton,FOREST,"#285246",CREAM,"#628074");
        certificateButton.setTooltip(new Tooltip("Abrir a pasta do certificado para usar o sistema no Android"));

        var mark=new Label("BZ");
        mark.setAlignment(Pos.CENTER);
        mark.setMinSize(36,36);
        mark.setStyle("-fx-background-color: "+GOLD+"; -fx-background-radius: 10; -fx-text-fill: "+FOREST+"; -fx-font-size: 15px; -fx-font-weight: bold;");
        var title=new Label("Bar do Zico");
        title.setStyle("-fx-text-fill: "+CREAM+"; -fx-font-size: 18px; -fx-font-weight: bold;");
        var subtitle=new Label("Administração do bar");
        subtitle.setStyle("-fx-text-fill: #c4d4cc; -fx-font-size: 11px;");
        var brand=new VBox(1,title,subtitle);
        var spacer=new Region();
        HBox.setHgrow(spacer,Priority.ALWAYS);
        var toolbar=new HBox(12,mark,brand,spacer,certificateButton,retry);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-padding: 12 20; -fx-background-color: "+FOREST+";");
        var statusbar=new HBox(8,status,message);
        statusbar.setAlignment(Pos.CENTER_LEFT);
        statusbar.setStyle("-fx-padding: 8 20; -fx-background-color: "+CREAM+"; -fx-border-color: #dedfd5; -fx-border-width: 1 0 0 0;");
        HBox.setHgrow(message,Priority.ALWAYS);
        var unavailable=new VBox(16);
        unavailable.setAlignment(Pos.CENTER);unavailable.setStyle("-fx-padding: 40;");
        var unavailableTitle=new Label("O serviço do bar está desligado");unavailableTitle.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: "+FOREST+";");
        var unavailableHelp=new Label("Feche esta janela e abra o atalho Bar do Zico na Área de Trabalho.\nEle inicia o banco e o serviço antes de abrir o programa.\nSe o endereço for da nuvem, confira se o servidor Azure está disponível.");
        unavailableHelp.setWrapText(true);unavailableHelp.setMaxWidth(600);unavailableHelp.setStyle("-fx-font-size: 17px; -fx-text-fill: #40544c;");
        unavailable.getChildren().addAll(unavailableTitle,unavailableHelp);unavailable.setVisible(false);
        var content=new StackPane(web,unavailable);
        var root=new BorderPane(content,toolbar,null,statusbar,null);
        root.setStyle("-fx-font-family: 'Segoe UI'; -fx-background-color: "+CREAM+";");
        stage.setTitle("Bar do Zico");stage.setScene(new Scene(root,1200,800));stage.setMinWidth(780);stage.setMinHeight(600);
        var icon=BarDesktop.class.getResourceAsStream("/icons/bar-do-zico.png");
        if(icon!=null) {try(icon){stage.getIcons().add(new Image(icon));}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}}
        web.getEngine().locationProperty().addListener((o,old,next)->{
            if(!next.equals(url) && !next.startsWith(url+"/") && !next.startsWith(url+"#")) {web.getEngine().getLoadWorker().cancel();status.setFill(Color.web("#a34b35"));message.setText("Navegação externa bloqueada.");}
        });
        web.getEngine().getLoadWorker().stateProperty().addListener((o,old,state)->{
            if(state==Worker.State.SUCCEEDED) {
                ((netscape.javascript.JSObject)web.getEngine().executeScript("window")).setMember("barReceiptPrinter",receiptPrinter);
                unavailable.setVisible(false);web.setVisible(true);
                status.setFill(Color.web("#398161"));
                message.setText("Conectado ao computador do bar. Fechar esta janela mantém o serviço ativo.");
                if(receiptSmoke) {
                    var delay=new PauseTransition(Duration.seconds(4));
                    delay.setOnFinished(e->{
                        String id=java.util.UUID.fromString(getParameters().getRaw().stream().filter(p->p.startsWith("--receipt-smoke-test=")).findFirst().orElseThrow().substring("--receipt-smoke-test=".length())).toString();
                        web.getEngine().executeScript("window.barReceiptPrinter.preview('"+id+"')");
                    });
                    delay.play();
                }
                if(getParameters().getRaw().contains("--smoke-test")) {
                    var delay=new PauseTransition(Duration.seconds(4));
                    delay.setOnFinished(e->{
                        try {
                            String rendered=String.valueOf(web.getEngine().executeScript("document.getElementById('root').innerText"));
                            boolean ok=rendered.contains("Mesas")&&rendered.contains("BAR DO ZICO")&&!rendered.contains("Digite sua senha");
                            Files.writeString(Path.of(".tools/desktop-smoke.txt"),ok?"OK: tela Mesas sem login renderizada no JavaFX WebView. Java "+System.getProperty("java.version")+" / JavaFX "+System.getProperty("javafx.runtime.version"):"FALHA: "+rendered);
                            var snapshot=root.snapshot(null,null);
                            int width=(int)snapshot.getWidth(),height=(int)snapshot.getHeight();
                            int[] pixels=new int[width*height];
                            snapshot.getPixelReader().getPixels(0,0,width,height,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,width);
                            var capture=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                            capture.setRGB(0,0,width,height,pixels,0,width);
                            javax.imageio.ImageIO.write(capture,"png",Path.of(".tools/desktop-smoke.png").toFile());
                        }
                        catch(Exception failure) {throw new RuntimeException(failure);}finally {Platform.exit();}
                    });
                    delay.play();
                }
            } else if(state==Worker.State.FAILED) {
                web.setVisible(false);unavailable.setVisible(true);
                status.setFill(Color.web("#a34b35"));
                message.setText("Serviço indisponível. Execute Iniciar Bar e clique em Reconectar.");
            }
        });
        stage.show();web.getEngine().load(url);
    }

    private static Path projectDirectory() {
        String configured=System.getenv("BAR_PROJECT_DIR");
        if(configured!=null&&!configured.isBlank())return Path.of(configured);
        Path current=ProcessHandle.current().info().command().map(Path::of).map(Path::getParent).orElse(Path.of("").toAbsolutePath());
        for(Path folder=current;folder!=null;folder=folder.getParent())if(Files.isRegularFile(folder.resolve("scripts/executar_bar.py")))return folder;
        return Path.of("").toAbsolutePath();
    }

    private static void styleButton(Button button,String background,String hover,String foreground,String border) {
        button.setMinHeight(36);
        button.setStyle(buttonStyle(background,foreground,border));
        Runnable refresh=()->button.setStyle(buttonStyle(button.isHover()?hover:background,foreground,button.isFocused()?CREAM:border));
        button.hoverProperty().addListener((o,old,value)->refresh.run());
        button.focusedProperty().addListener((o,old,value)->refresh.run());
    }

    private static String buttonStyle(String background,String foreground,String border) {
        return "-fx-background-color: "+background+"; -fx-text-fill: "+foreground+"; -fx-border-color: "+border+"; -fx-border-width: 1; -fx-border-radius: 7; -fx-background-radius: 7; -fx-padding: 7 13; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;";
    }
}
