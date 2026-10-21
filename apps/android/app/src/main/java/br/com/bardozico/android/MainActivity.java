package br.com.bardozico.android;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class MainActivity extends Activity {
    private WebView web;
    private LinearLayout root,errorPanel;
    private TextView status,errorText;
    private String address;
    private boolean pageFailed;
    private android.content.SharedPreferences settings;
    private String attendanceTitle(){return BuildConfig.FLAVOR.equals("treinamento")?"TREINAMENTO — Atendimento":"Atendimento";}
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private Button button(String title){Button b=new Button(this);b.setText(title);b.setTextSize(16);b.setAllCaps(false);b.setMinHeight(dp(48));return b;}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        settings=getSharedPreferences("connection",MODE_PRIVATE);
        address=settings.getString("address",BuildConfig.DEFAULT_URL);
        if (ConnectionAddress.placeholder(address)) {
            address=BuildConfig.DEFAULT_URL;
            settings.edit().putString("address",address).apply();
        }
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((view,insets)->{root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(12),0,dp(12),0);
        status=new TextView(this);status.setText(attendanceTitle());status.setTextColor(Color.rgb(16,33,60));status.setTextSize(15);status.setGravity(Gravity.CENTER_VERTICAL);bar.addView(status,new LinearLayout.LayoutParams(-1,dp(48)));root.addView(bar);
        errorPanel=new LinearLayout(this);errorPanel.setOrientation(LinearLayout.VERTICAL);errorPanel.setPadding(dp(24),dp(24),dp(24),dp(24));errorPanel.setVisibility(View.GONE);
        errorText=new TextView(this);errorText.setTextSize(20);errorText.setTextColor(Color.rgb(16,33,60));errorPanel.addView(errorText);
        Button retry=button("Tentar novamente");retry.setOnClickListener(v->load());errorPanel.addView(retry);
        Button change=button("Configurações");change.setOnClickListener(v->showSettings());errorPanel.addView(change);root.addView(errorPanel);
        web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings config=web.getSettings();config.setJavaScriptEnabled(true);config.setDomStorageEnabled(true);config.setAllowFileAccess(false);config.setAllowContentAccess(false);config.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);config.setSupportMultipleWindows(false);config.setJavaScriptCanOpenWindowsAutomatically(false);config.setTextZoom(100);
        // The API uses this marker only to reduce access to attendance, never to grant access.
        // Set it before loading any page so navigation, fetch and resource requests share it.
        config.setUserAgentString(config.getUserAgentString()+" BarDoZicoAndroid/"+BuildConfig.VERSION_NAME);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(ConnectionAddress.settingsNavigation(address,view.getUrl(),request.getUrl().toString(),request.isForMainFrame(),request.hasGesture())) {
                    showSettings();return true;
                }
                return !sameOrigin(request.getUrl().toString());
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){pageFailed=false;status.setText("Conectando ao bar…");}
            @Override public void onPageFinished(WebView view,String url){if(!pageFailed){status.setText(attendanceTitle());CookieManager.getInstance().flush();}}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
                if(request.isForMainFrame()&&sameOrigin(request.getUrl().toString())) failure("Não conseguimos falar com o bar. Confira a conexão, o endereço e se o serviço está ligado.");
            }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){
                if(request.isForMainFrame()&&sameOrigin(request.getUrl().toString())) failure("O serviço do bar não conseguiu abrir a tela. Confira o endereço ou tente novamente em instantes.");
            }
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){
                handler.cancel();
                failure("O certificado deste endereço não foi reconhecido. Confira o endereço e a configuração do servidor. A conexão não foi liberada.");
            }
        });
        setContentView(root);
        if(validAddress(address))load();else configure();
    }
    private boolean validAddress(String candidate){
        return ConnectionAddress.valid(candidate) && !ConnectionAddress.placeholder(candidate);
    }
    private boolean sameOrigin(String target){return ConnectionAddress.sameOrigin(address,target);}
    private void load(){web.stopLoading();pageFailed=false;status.setText("Conectando ao bar…");errorPanel.setVisibility(View.GONE);web.setVisibility(View.VISIBLE);web.loadUrl(address);}
    private void failure(String message){pageFailed=true;status.setText("Sem conexão com o bar");errorText.setText(message);errorPanel.setVisibility(View.VISIBLE);web.setVisibility(View.GONE);}
    private void showSettings(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(10),dp(20),dp(10));
        TextView help=new TextView(this);help.setText("Configure a conexão deste celular com o bar.");help.setTextSize(17);box.addView(help);
        TextView server=new TextView(this);server.setText("Servidor atual\n"+address);server.setTextSize(16);server.setTextIsSelectable(true);server.setPadding(0,dp(16),0,dp(16));box.addView(server);
        Button connection=button("Conexão com o servidor");box.addView(connection);
        TextView version=new TextView(this);version.setText(attendanceTitle()+" · Versão "+BuildConfig.VERSION_NAME);version.setTextSize(14);version.setPadding(0,dp(16),0,0);box.addView(version);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Configurações do aplicativo").setView(box).setNegativeButton("Voltar",null).create();
        connection.setOnClickListener(v->{dialog.dismiss();configure();});dialog.show();
    }
    private void configure(){
        EditText field=new EditText(this);field.setSingleLine(true);field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);field.setText(address);field.setTextSize(17);field.setSelectAllOnFocus(true);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(10),dp(20),dp(10));TextView help=new TextView(this);help.setText("No computador, abra Configurações → Acesso pelo Android e copie o endereço HTTPS. O endereço da rede residencial funciona no mesmo roteador. Para usar 4G ou outra rede, é necessário o endereço de um servidor na nuvem já configurado.");help.setTextSize(17);box.addView(help);box.addView(field);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Conexão com o servidor").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Salvar e conectar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{String value=field.getText().toString().trim();if(!validAddress(value)){field.setError("Use o endereço HTTPS mostrado no computador.");return;}address=value.replaceAll("/$","");settings.edit().putString("address",address).apply();dialog.dismiss();load();}));dialog.show();
    }
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else new AlertDialog.Builder(this).setMessage("Fechar o Bar do Zico? Os envios pendentes ficam guardados no aparelho.").setNegativeButton("Continuar",null).setPositiveButton("Fechar",(d,w)->finish()).show();}
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
