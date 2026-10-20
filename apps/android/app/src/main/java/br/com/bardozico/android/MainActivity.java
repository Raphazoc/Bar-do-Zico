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
import java.net.URI;

public class MainActivity extends Activity {
    private WebView web;
    private LinearLayout root,errorPanel;
    private TextView status,errorText;
    private String address;
    private boolean pageFailed;
    private android.content.SharedPreferences settings;
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private Button button(String title){Button b=new Button(this);b.setText(title);b.setTextSize(16);b.setAllCaps(false);b.setMinHeight(dp(48));return b;}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        settings=getSharedPreferences("connection",MODE_PRIVATE);
        address=settings.getString("address",BuildConfig.DEFAULT_URL);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((view,insets)->{root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(12),0,dp(12),0);
        status=new TextView(this);status.setText("Bar do Zico");status.setTextColor(Color.rgb(16,33,60));status.setTextSize(15);bar.addView(status,new LinearLayout.LayoutParams(0,dp(54),1));
        Button connection=button("Conexão");connection.setOnClickListener(v->configure());bar.addView(connection);root.addView(bar);
        errorPanel=new LinearLayout(this);errorPanel.setOrientation(LinearLayout.VERTICAL);errorPanel.setPadding(dp(24),dp(24),dp(24),dp(24));errorPanel.setVisibility(View.GONE);
        errorText=new TextView(this);errorText.setTextSize(20);errorText.setTextColor(Color.rgb(16,33,60));errorPanel.addView(errorText);
        Button retry=button("Tentar novamente");retry.setOnClickListener(v->load());errorPanel.addView(retry);
        Button change=button("Conferir endereço do computador");change.setOnClickListener(v->configure());errorPanel.addView(change);root.addView(errorPanel);
        web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings config=web.getSettings();config.setJavaScriptEnabled(true);config.setDomStorageEnabled(true);config.setAllowFileAccess(false);config.setAllowContentAccess(false);config.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);config.setSupportMultipleWindows(false);config.setJavaScriptCanOpenWindowsAutomatically(false);config.setTextZoom(100);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !sameOrigin(request.getUrl().toString());}
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){pageFailed=false;status.setText("Conectando ao bar…");}
            @Override public void onPageFinished(WebView view,String url){if(!pageFailed){status.setText(BuildConfig.FLAVOR.equals("treinamento")?"TREINAMENTO — Wi-Fi local":"Bar do Zico — Wi-Fi local");CookieManager.getInstance().flush();}}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())failure("Não conseguimos falar com o computador do bar. Confira o Wi-Fi e se o programa está ligado.");}
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){handler.cancel();failure("O certificado deste endereço não foi reconhecido. Confira o endereço e a configuração do computador. A conexão não foi liberada.");}
        });
        setContentView(root);
        if(validAddress(address))load();else configure();
    }
    private boolean validAddress(String candidate){
        try{URI uri=URI.create(candidate);return "https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null&&uri.getRawUserInfo()==null&&(uri.getPath()==null||uri.getPath().isEmpty()||uri.getPath().equals("/"))&&uri.getRawQuery()==null&&uri.getRawFragment()==null;}catch(Exception e){return false;}
    }
    private boolean sameOrigin(String target){try{URI a=URI.create(address),b=URI.create(target);return a.getScheme().equals(b.getScheme())&&a.getHost().equals(b.getHost())&&(a.getPort()==-1?443:a.getPort())==(b.getPort()==-1?443:b.getPort());}catch(Exception e){return false;}}
    private void load(){errorPanel.setVisibility(View.GONE);web.setVisibility(View.VISIBLE);web.loadUrl(address);}
    private void failure(String message){pageFailed=true;status.setText("Sem conexão com o bar");errorText.setText(message);errorPanel.setVisibility(View.VISIBLE);web.setVisibility(View.GONE);}
    private void configure(){
        EditText field=new EditText(this);field.setSingleLine(true);field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);field.setText(address);field.setTextSize(17);field.setSelectAllOnFocus(true);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(10),dp(20),dp(10));TextView help=new TextView(this);help.setText("No computador, abra Configurações e copie o endereço para Android. Os aparelhos precisam estar no mesmo Wi-Fi.");help.setTextSize(17);box.addView(help);box.addView(field);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Conectar ao computador do bar").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Salvar e conectar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{String value=field.getText().toString().trim();if(!validAddress(value)){field.setError("Use o endereço HTTPS mostrado no computador.");return;}address=value.replaceAll("/$","");settings.edit().putString("address",address).apply();dialog.dismiss();load();}));dialog.show();
    }
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else new AlertDialog.Builder(this).setMessage("Fechar o Bar do Zico? Os envios pendentes ficam guardados no aparelho.").setNegativeButton("Continuar",null).setPositiveButton("Fechar",(d,w)->finish()).show();}
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
