package br.com.bardozico;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;

@Configuration
class SecurityConfig {
    @Bean Argon2PasswordEncoder encoder() { return new Argon2PasswordEncoder(16,32,1,65536,3); }
    @Bean SecurityFilterChain security(HttpSecurity http, Identity identity, ObjectMapper json,@Value("${bar.allowed-hosts}") String allowedHosts,@Value("${bar.training}") boolean training,@Value("${bar.open-access}") boolean openAccess) throws Exception {
        Set<String> hosts=new HashSet<>(Arrays.asList(allowedHosts.toLowerCase(Locale.ROOT).split(",")));
        // Todas as escritas exigem header não simples; nenhuma origem CORS é liberada.
        // A API usa sessão própria persistida, não a sessão de servlet.
        http.csrf(c -> c.disable()).cors(c -> c.disable())
            .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
            .headers(c -> c.contentSecurityPolicy(p -> p.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'")))
            .authorizeHttpRequests(c -> c.requestMatchers("/api/health","/api/login").permitAll()
                .requestMatchers("/api/**").authenticated().anyRequest().permitAll())
            .exceptionHandling(c -> c.authenticationEntryPoint((req,res,e) -> fail(res,401,"Entre no sistema para continuar.",json))
                .accessDeniedHandler((req,res,e) -> fail(res,403,"Acesso não permitido.",json)))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
                    if(!hosts.contains(req.getServerName().toLowerCase(Locale.ROOT))) { fail(res,403,"Endereço do serviço não permitido.",json);return; }
                    if (req.getRequestURI().startsWith("/api/")) {
                        res.setHeader("Cache-Control","no-store");
                        if (!Set.of("GET","HEAD","OPTIONS").contains(req.getMethod()) && !"1".equals(req.getHeader("X-Bar-Request"))) {
                            fail(res,403,"Solicitação sem proteção de origem.",json); return;
                        }
                        String origin=req.getHeader("Origin");
                        String expected=req.getScheme()+"://"+req.getHeader("Host");
                        if (origin!=null && !origin.equals(expected)) { fail(res,403,"Origem não permitida.",json); return; }
                        String token=null;
                        if (req.getCookies()!=null) for (var cookie:req.getCookies()) if (cookie.getName().equals(training?"BAR_TRAINING_SESSION":"BAR_SESSION")) token=cookie.getValue();
                        Actor actor=identity.authenticate(token);
                        if(actor==null && openAccess && !req.getRequestURI().equals("/api/login") && !req.getRequestURI().equals("/api/health")) {
                            try{actor=identity.localOperator();}catch(ApiError e){fail(res,e.status,e.getMessage(),json);return;}
                        }
                        if (actor!=null) {
                            var authorities=actor.permissions().stream().map(SimpleGrantedAuthority::new).toList();
                            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,authorities));
                        }
                    }
                    chain.doFilter(req,res);
                }
            }, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    private static void fail(HttpServletResponse res,int status,String error,ObjectMapper json) throws IOException {
        res.setStatus(status); res.setContentType("application/json;charset=UTF-8");
        json.writeValue(res.getOutputStream(),Map.of("error",error));
    }
}
