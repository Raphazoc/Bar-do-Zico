package br.com.bardozico;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/** A confiança administrativa local vem da conexão ao próprio PC, nunca de headers. */
@Component
class DeviceAccess {
    private final boolean enabled;
    private static final IpAddressMatcher V4_LOOPBACK=new IpAddressMatcher("127.0.0.0/8");
    private static final IpAddressMatcher V6_LOOPBACK=new IpAddressMatcher("::1/128");

    DeviceAccess(@Value("${bar.open-access}") boolean openAccess,
                 @Value("${bar.device-access:true}") boolean deviceAccess,
                 @Value("${server.forward-headers-strategy:NONE}") String forwarding) {
        enabled=openAccess&&deviceAccess;
        if(enabled&&!"NONE".equalsIgnoreCase(forwarding))
            throw new IllegalStateException("Perfis locais por aparelho exigem server.forward-headers-strategy=NONE. Use acesso autenticado atrás de um proxy.");
    }

    boolean enabled(){return enabled;}
    boolean attendance(HttpServletRequest request) {
        // O marcador nativo apenas reduz acesso em testes/ADB; nunca concede gestão.
        String agent=request.getHeader("User-Agent");
        if(agent!=null&&agent.contains("BarDoZicoAndroid/"))return true;
        String source=request.getRemoteAddr();
        try{return source==null||!(V4_LOOPBACK.matches(source)||V6_LOOPBACK.matches(source));}
        catch(IllegalArgumentException invalid){return true;}
    }
}
