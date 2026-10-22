package br.com.bardozico;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.*;

@Service
class Identity {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final Argon2PasswordEncoder encoder;
    final UUID unit;
    private final String dummy;
    Identity(JdbcTemplate db,TransactionTemplate tx,Argon2PasswordEncoder encoder,@Value("${bar.unit-id}") UUID unit) {
        this.db=db; this.tx=tx; this.encoder=encoder; this.unit=unit;
        dummy=encoder.encode(UUID.randomUUID().toString());
    }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    Actor authenticate(String token) {
        if(token==null || token.length()!=64) return null;
        var rows=db.queryForList("""
            SELECT u.id,u.name,u.email,u.username,s.id session_id FROM bar.app_sessions s
            JOIN bar.users u ON (u.establishment_id,u.id)=(s.establishment_id,s.user_id)
            WHERE s.token_hash=? AND s.establishment_id=? AND s.revoked_at IS NULL
              AND s.expires_at>CURRENT_TIMESTAMP AND u.active AND NOT u.system_attendant
            """,hash(token),unit);
        if(rows.isEmpty()) return null;
        var row=rows.getFirst(); UUID id=(UUID)row.get("id");
        var permissions=new HashSet<>(db.queryForList("""
            SELECT DISTINCT p.code FROM bar.user_roles ur JOIN bar.role_permissions rp
              ON (rp.establishment_id,rp.role_id)=(ur.establishment_id,ur.role_id)
            JOIN bar.permissions p ON p.id=rp.permission_id WHERE ur.establishment_id=? AND ur.user_id=?
            """,String.class,unit,id));
        return new Actor(id,unit,(UUID)row.get("session_id"),(String)row.get("name"),(String)row.get("email"),(String)row.get("username"),permissions);
    }
    Actor localOperator() {
        var rows=db.queryForList("""
            SELECT DISTINCT u.id,u.name,u.email,u.username,u.created_at FROM bar.users u
            JOIN bar.user_roles ur ON (ur.establishment_id,ur.user_id)=(u.establishment_id,u.id)
            JOIN bar.role_permissions rp ON (rp.establishment_id,rp.role_id)=(ur.establishment_id,ur.role_id)
            JOIN bar.permissions p ON p.id=rp.permission_id
            WHERE u.establishment_id=? AND u.active AND NOT u.system_attendant AND p.code='user.manage' ORDER BY u.created_at,u.id LIMIT 1
            """,unit);
        if(rows.isEmpty()) throw new ApiError(503,"O responsável pelo bar precisa ser configurado no computador.");
        var row=rows.getFirst();UUID id=(UUID)row.get("id");
        return new Actor(id,unit,null,(String)row.get("name"),(String)row.get("email"),(String)row.get("username"),permissions(id));
    }
    private Set<String> permissions(UUID id) {
        return new HashSet<>(db.queryForList("SELECT DISTINCT p.code FROM bar.user_roles ur JOIN bar.role_permissions rp ON (rp.establishment_id,rp.role_id)=(ur.establishment_id,ur.role_id) JOIN bar.permissions p ON p.id=rp.permission_id WHERE ur.establishment_id=? AND ur.user_id=?",String.class,unit,id));
    }
    Actor mobileOperator() {
        return mobileOperator(null);
    }
    Actor mobileOperator(String token) {
        if(token!=null&&token.matches("[a-f0-9]{64}")) {
            var rows=db.queryForList("""
                SELECT u.id,u.name,s.id session_id FROM bar.app_sessions s
                JOIN bar.users u ON (u.establishment_id,u.id)=(s.establishment_id,s.user_id)
                WHERE s.establishment_id=? AND s.token_hash=? AND s.revoked_at IS NULL
                  AND s.expires_at>CURRENT_TIMESTAMP AND u.active AND u.deleted_at IS NULL
                  AND u.system_attendant AND u.named_attendant
                """,unit,hash(token));
            if(!rows.isEmpty()) {
                var row=rows.getFirst();
                return new Actor((UUID)row.get("id"),unit,(UUID)row.get("session_id"),(String)row.get("name"),null,null,Set.of("table.create","account.open","order.create"),true);
            }
        }
        return tx.execute(status->{
            db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",unit+":mobile-operator");
            if(db.queryForObject("SELECT count(*) FROM bar.establishments WHERE id=?",Integer.class,unit)==0)
                throw new ApiError(503,"O bar precisa ser configurado no computador.");
            var users=db.queryForList("SELECT id,active,deleted_at FROM bar.users WHERE establishment_id=? AND system_attendant AND NOT named_attendant FOR UPDATE",unit);
            UUID id;
            if(users.isEmpty()) {
                id=UUID.randomUUID();
                db.update("INSERT INTO bar.users(id,establishment_id,name,password_hash,system_attendant) VALUES(?,?,'Atendimento do celular',?,true)",id,unit,"!sem-login-no-aparelho!"+UUID.randomUUID());
            } else {
                var user=users.getFirst();
                if(!Boolean.TRUE.equals(user.get("active"))||user.get("deleted_at")!=null)
                    throw new ApiError(503,"O acesso de atendimento precisa ser conferido no computador.");
                id=(UUID)user.get("id");
            }
            return new Actor(id,unit,null,"Atendimento do celular",null,null,Set.of("table.create","account.open","order.create"),true);
        });
    }
    boolean namedAttendant(Actor actor) {
        return actor.attendanceOnly()&&Boolean.TRUE.equals(db.queryForObject("SELECT named_attendant FROM bar.users WHERE establishment_id=? AND id=?",Boolean.class,unit,actor.id()));
    }
    String identifyAttendant(String rawName) {
        if(rawName==null)throw ApiError.bad("Informe o nome do atendente.");
        String name=rawName.strip().replaceAll("\\s+"," ");
        if(name.isEmpty()||name.length()>80||rawName.codePoints().anyMatch(Character::isISOControl))
            throw ApiError.bad("Informe um nome de até 80 caracteres, sem quebras de linha.");
        return tx.execute(status->{
            db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",unit+":mobile-operator");
            var rows=db.queryForList("SELECT id,active,deleted_at FROM bar.users WHERE establishment_id=? AND system_attendant AND named_attendant AND lower(name)=lower(?) FOR UPDATE",unit,name);
            UUID id;
            if(rows.isEmpty()) {
                id=UUID.randomUUID();
                db.update("INSERT INTO bar.users(id,establishment_id,name,password_hash,system_attendant,named_attendant) VALUES(?,?,?,?,true,true)",id,unit,name,"!sem-login-no-aparelho!"+UUID.randomUUID());
            } else {
                var row=rows.getFirst();
                if(!Boolean.TRUE.equals(row.get("active"))||row.get("deleted_at")!=null)throw ApiError.bad("Este identificador de atendimento está indisponível.");
                id=(UUID)row.get("id");
            }
            byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);
            db.update("INSERT INTO bar.app_sessions(establishment_id,user_id,token_hash,expires_at) VALUES(?,?,?,CURRENT_TIMESTAMP+interval '365 days')",unit,id,hash(token));
            return token;
        });
    }
    String selectOperator(UUID user) {
        if(db.queryForObject("SELECT count(*) FROM bar.users WHERE establishment_id=? AND id=? AND active AND NOT system_attendant",Integer.class,unit,user)==0) throw ApiError.bad("Funcionário indisponível.");
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);
        tx.executeWithoutResult(status->{
            var current=db.queryForMap("SELECT active FROM bar.users WHERE establishment_id=? AND id=? FOR UPDATE",unit,user);
            if(!Boolean.TRUE.equals(current.get("active"))) throw ApiError.bad("Funcionário desativado.");
            db.update("INSERT INTO bar.app_sessions(establishment_id,user_id,token_hash,expires_at) VALUES(?,?,?,CURRENT_TIMESTAMP+interval '8 hours')",unit,user,hash(token));
            event(user,"operator.selected","user",user);
        });
        return token;
    }
    String login(String username,String password,String ip) {
        if(username==null || password==null || username.length()>254 || password.length()>256) throw new ApiError(401,"Usuário ou senha incorretos.");
        username=username.strip().toLowerCase(Locale.ROOT);
        var users=db.queryForList("SELECT id,password_hash,active FROM bar.users WHERE establishment_id=? AND NOT system_attendant AND (username=? OR email=?)",unit,username,username);
        // Os dois nomes de entrada da mesma pessoa compartilham o limite.
        String accountKey=hash(unit+":"+(users.isEmpty()?"login:"+username:"user:"+users.getFirst().get("id"))),sourceKey=hash("ip:"+ip);
        // Cada tentativa é reservada/confirmada antes de calcular Argon2, inclusive falhas.
        tx.executeWithoutResult(s -> {
            for(String key:List.of(accountKey,sourceKey).stream().sorted().toList()) {
                db.update("INSERT INTO bar.login_attempts(key_hash) VALUES(?) ON CONFLICT DO NOTHING",key);
                var r=db.queryForMap("SELECT attempts,window_started_at FROM bar.login_attempts WHERE key_hash=? FOR UPDATE",key);
                boolean expired=((java.sql.Timestamp)r.get("window_started_at")).toInstant().plusSeconds(900).isBefore(Instant.now());
                int limit=key.equals(sourceKey)?40:8;
                if(!expired && ((Number)r.get("attempts")).intValue()>=limit) throw new ApiError(429,"Muitas tentativas. Aguarde 15 minutos.");
                db.update("UPDATE bar.login_attempts SET attempts=?,window_started_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE window_started_at END WHERE key_hash=?",expired?1:((Number)r.get("attempts")).intValue()+1,expired,key);
            }
            db.update("DELETE FROM bar.login_attempts WHERE window_started_at<CURRENT_TIMESTAMP-interval '1 day'");
        });
        String encoded=users.isEmpty()?dummy:(String)users.getFirst().get("password_hash");
        boolean valid=false;
        try { valid=encoder.matches(password,encoded); } catch(IllegalArgumentException ignored) { }
        if(!valid || users.isEmpty() || !Boolean.TRUE.equals(users.getFirst().get("active"))) throw new ApiError(401,"Usuário ou senha incorretos.");
        UUID id=(UUID)users.getFirst().get("id");
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); String token=HexFormat.of().formatHex(bytes);
        tx.executeWithoutResult(s -> {
            // Serializa com troca de senha/desativação e impede login com hash antigo.
            var current=db.queryForMap("SELECT password_hash,active FROM bar.users WHERE establishment_id=? AND id=? FOR UPDATE",unit,id);
            if(!Boolean.TRUE.equals(current.get("active")) || !encoded.equals(current.get("password_hash"))) throw new ApiError(401,"Usuário ou senha incorretos.");
            db.update("INSERT INTO bar.app_sessions(establishment_id,user_id,token_hash,expires_at) VALUES(?,?,?,CURRENT_TIMESTAMP+interval '8 hours')",unit,id,hash(token));
            db.update("DELETE FROM bar.login_attempts WHERE key_hash=?",accountKey);
            event(id,"user.login","user",id);
        });
        return token;
    }
    void logout(Actor a) {
        tx.executeWithoutResult(s -> {
            db.update("UPDATE bar.app_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE establishment_id=? AND id=?",a.unit(),a.session());
            event(a.id(),"user.logout","user",a.id());
        });
    }
    void password(Actor a,String oldPassword,String next) {
        int min=a.permissions().contains("user.manage")?6:4;
        if(next==null || next.isBlank() || next.length()<min || next.length()>128 || next.equals(oldPassword)) throw ApiError.bad("Use uma senha nova com pelo menos "+min+" caracteres (até 128).");
        tx.executeWithoutResult(s -> {
            var row=db.queryForMap("SELECT password_hash FROM bar.users WHERE establishment_id=? AND id=? FOR UPDATE",a.unit(),a.id());
            if(oldPassword==null || !encoder.matches(oldPassword,(String)row.get("password_hash"))) throw new ApiError(401,"Senha atual incorreta.");
            db.update("UPDATE bar.users SET password_hash=? WHERE establishment_id=? AND id=?",encoder.encode(next),a.unit(),a.id());
            db.update("UPDATE bar.app_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE establishment_id=? AND user_id=? AND revoked_at IS NULL",a.unit(),a.id());
            event(a.id(),"user.password_changed","user",a.id());
        });
    }
    void event(UUID actor,String action,String type,UUID id) {
        db.update("INSERT INTO bar.audit_events(establishment_id,actor_id,action,resource_type,resource_id,correlation_id) VALUES(?,?,?,?,?,?)",unit,actor,action,type,id,UUID.randomUUID().toString());
        db.update("INSERT INTO bar.outbox_events(establishment_id,aggregate_type,aggregate_id,event_type,payload) VALUES(?,?,?,?,?::jsonb)",unit,type,id,action,"{\"id\":\""+id+"\"}");
    }
}
