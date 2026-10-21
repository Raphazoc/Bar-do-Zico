package br.com.bardozico;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

@RestController
@RequestMapping("/api")
class IdentityController {
    private final Identity identity; private final JdbcTemplate db; private final boolean secure; private final boolean training; private final boolean openAccess;private final DeviceAccess devices;
    IdentityController(Identity identity,JdbcTemplate db,DeviceAccess devices,@Value("${bar.secure-cookie}") boolean secure,@Value("${bar.training}") boolean training,@Value("${bar.open-access}") boolean openAccess) { this.identity=identity;this.db=db;this.devices=devices;this.secure=secure;this.training=training;this.openAccess=openAccess; }
    record Login(String username,String email,String password) { }
    record Password(String currentPassword,String newPassword) { }
    record Operator(UUID userId) { }
    @PostMapping("/operator") ResponseEntity<?> operator(@RequestBody Operator body) {
        if(!openAccess) throw new ApiError(403,"A seleção sem senha não está habilitada.");
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,body.userId()==null?cookie("",0):cookie(identity.selectOperator(body.userId()),28800)).body(Map.of("ok",true));
    }
    private String cookie(String token,long age) {
        return ResponseCookie.from(training?"BAR_TRAINING_SESSION":"BAR_SESSION",token).httpOnly(true).secure(secure).sameSite("Strict").path("/api").maxAge(age).build().toString();
    }
    @GetMapping("/health") Map<String,String> health() { db.queryForObject("SELECT 1",Integer.class);return Map.of("status","ok","application","bar-do-zico","mode",training?"TREINAMENTO":"OPERACAO"); }
    @PostMapping("/login") ResponseEntity<?> login(@RequestBody Login body,HttpServletRequest request) {
        String username=body.username()!=null&&!body.username().isBlank()?body.username():body.email();
        String token=identity.login(username,body.password(),request.getRemoteAddr());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,cookie(token,28800)).body(Map.of("ok",true));
    }
    @GetMapping("/me") Map<String,Object> me(@AuthenticationPrincipal Actor a) {
        var unit=db.queryForMap("SELECT name,timezone FROM bar.establishments WHERE id=?",a.unit());
        var out=new LinkedHashMap<String,Object>();
        out.put("id",a.id());out.put("name",a.name());out.put("email",a.email());out.put("username",a.username());
        out.put("permissions",a.permissions());out.put("unit",unit);out.put("training",training);out.put("openAccess",openAccess);
        out.put("deviceAccess",devices.enabled());out.put("accessMode",a.attendanceOnly()?"ATENDIMENTO":"ADMINISTRADOR");
        return out;
    }
    @PostMapping("/logout") ResponseEntity<?> logout(@AuthenticationPrincipal Actor a) {
        identity.logout(a);return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,cookie("",0)).body(Map.of("ok",true));
    }
    @PostMapping("/password") ResponseEntity<?> password(@AuthenticationPrincipal Actor a,@RequestBody Password body) {
        identity.password(a,body.currentPassword(),body.newPassword());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,cookie("",0)).body(Map.of("ok",true));
    }
}
