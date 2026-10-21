package br.com.bardozico;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.web.servlet.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.math.BigDecimal;
import java.util.concurrent.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.*;

@SpringBootTest(properties="bar.android-urls=https://192.168.0.8:18443,https://192.168.0.8:8443,https://bar-api.example.azurecontainerapps.io")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="BAR_TEST_DATABASE",matches="true")
class ApplicationTest {
    private static Path setupDirectory;
    private static synchronized Path setupDirectory() {
        if(setupDirectory==null)try{setupDirectory=Files.createTempDirectory("bar-setup-");}catch(Exception e){throw new IllegalStateException(e);}
        return setupDirectory;
    }
    @DynamicPropertySource static void setupProperties(DynamicPropertyRegistry properties) {
        properties.add("bar.open-access",()->"false");
        properties.add("bar.backup-directory",()->setupDirectory().toString());
        properties.add("bar.public-certificate",()->setupDirectory().resolve("public.cer").toString());
    }
    @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;@Autowired Argon2PasswordEncoder encoder;@Autowired Identity identity;
    UUID unit,user;String email;Cookie cookie;
    private final String password="Senha-de-teste-123456!";
    @BeforeAll void protectDatabase() {
        assertTrue(db.queryForObject("SELECT current_database()",String.class).startsWith("bar_app_test_"),"Testes só podem usar banco descartável.");
        unit=identity.unit;
        db.update("INSERT INTO bar.establishments(id,name) VALUES(?,'Bar de teste da API')",unit);
        try {
            Files.writeString(setupDirectory().resolve("public.cer"),"TEST PUBLIC CERTIFICATE");
            Files.writeString(setupDirectory().resolve("config.json"),"{\"enabled\":true,\"intervalMinutes\":60}");
            Files.writeString(setupDirectory().resolve("status.json"),"{\"state\":\"OK\",\"verified\":true,\"secret\":\"do-not-expose\"}");
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    @BeforeEach void setup() throws Exception {
        db.update("DELETE FROM bar.login_attempts");
        user=UUID.randomUUID();email=user+"@example.test";
        db.update("INSERT INTO bar.users(id,establishment_id,name,email,password_hash) VALUES(?,?,'Teste',?,?)",user,unit,email,encoder.encode(password));
        UUID role=UUID.randomUUID();db.update("INSERT INTO bar.roles(id,establishment_id,name) VALUES(?,?,?)",role,unit,role.toString());
        db.update("INSERT INTO bar.role_permissions(establishment_id,role_id,permission_id) SELECT ?,?,id FROM bar.permissions",unit,role);
        db.update("INSERT INTO bar.user_roles(establishment_id,user_id,role_id) VALUES(?,?,?)",unit,user,role);
        cookie=login(email,password);
    }
    Cookie login(String email,String pass) throws Exception {
        return login(Map.of("email",email,"password",pass));
    }
    Cookie login(Map<String,String> body) throws Exception {
        var result=mvc.perform(post("/api/login").header("X-Bar-Request","1").contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn();
        String header=result.getResponse().getHeader("Set-Cookie");assertTrue(header.contains("HttpOnly"));assertTrue(header.contains("SameSite=Strict"));
        String pair=header.split(";",2)[0];int separator=pair.indexOf('=');
        return new Cookie(pair.substring(0,separator),pair.substring(separator+1));
    }
    ResultActions command(String op,Map<String,Object> body,String key) throws Exception {
        return mvc.perform(post("/api/commands/"+op).cookie(cookie).header("X-Bar-Request","1").header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsString(body)));
    }
    UUID create(String op,Map<String,Object> body) throws Exception {
        var r=command(op,body,UUID.randomUUID().toString()).andExpect(status().isOk()).andReturn();
        return UUID.fromString(json.readTree(r.getResponse().getContentAsString()).path("id").asText());
    }
    Map<String,UUID> fixture() throws Exception {
        UUID area=create("area.save",Map.of("name","Área "+UUID.randomUUID()));
        UUID table=create("table.save",Map.of("areaId",area,"number",UUID.randomUUID().toString().substring(0,12)));
        UUID cat=create("category.save",Map.of("name","Categoria "+UUID.randomUUID()));
        UUID product=create("product.save",Map.of("categoryId",cat,"name","Produto de teste","price","10.00","destination","BAR"));
        UUID account=create("account.open",Map.of("tableId",table));
        UUID terminal=create("terminal.save",Map.of("name","Caixa "+UUID.randomUUID()));
        UUID session=create("cash.open",Map.of("terminalId",terminal,"openingAmount","50.00"));
        return Map.of("table",table,"product",product,"account",account,"session",session,"category",cat);
    }
    UUID order(Map<String,UUID> f) throws Exception {return create("order.create",Map.of("accountId",f.get("account"),"items",List.of(Map.of("productId",f.get("product"),"quantity","2"))));}

    @Test void loginPermissionsAndCsrf() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value(Boolean.parseBoolean(System.getenv("BAR_TRAINING"))?"TREINAMENTO":"OPERACAO"));
        mvc.perform(get("/api/state")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/login").contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/logout").cookie(cookie).header("X-Bar-Request","1").header("Origin","https://outside.example")).andExpect(status().isForbidden());
        mvc.perform(get("/api/me").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        mvc.perform(post("/api/logout").cookie(cookie).header("X-Bar-Request","1")).andExpect(status().isOk());
        mvc.perform(get("/api/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void deniedPermissionAndTenant() throws Exception {
        UUID other=UUID.randomUUID(),area=UUID.randomUUID();db.update("INSERT INTO bar.establishments(id,name) VALUES(?,'Outra unidade')",other);
        db.update("INSERT INTO bar.areas(id,establishment_id,name) VALUES(?,?,'Área de outra unidade')",area,other);
        command("area.save",Map.of("id",area,"name","Inválido"),UUID.randomUUID().toString()).andExpect(status().isNotFound());
        var state=mvc.perform(get("/api/state").cookie(cookie)).andReturn().getResponse().getContentAsString();assertFalse(state.contains(area.toString()));
        db.update("DELETE FROM bar.user_roles WHERE establishment_id=? AND user_id=?",unit,user);
        command("area.save",Map.of("name","Proibido"),UUID.randomUUID().toString()).andExpect(status().isForbidden());
    }
    @Test void staffCanUseUsernameWithoutEmailAndChangeSimplePassword() throws Exception {
        String username="atendente_"+UUID.randomUUID().toString().substring(0,8);
        UUID employee=create("user.create",Map.of("name","Atendente de teste","username",username,"password","1234","role","ATENDIMENTO"));
        assertNull(db.queryForObject("SELECT email FROM bar.users WHERE id=?",String.class,employee));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.table_authorizations WHERE user_id=?",Integer.class,employee));
        Cookie employeeCookie=login(Map.of("username","  "+username.toUpperCase(Locale.ROOT)+"  ","password","1234"));
        mvc.perform(get("/api/me").cookie(employeeCookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(employee.toString())).andExpect(jsonPath("$.username").value(username))
            .andExpect(jsonPath("$.email").isEmpty());
        mvc.perform(post("/api/password").cookie(employeeCookie).header("X-Bar-Request","1").contentType("application/json")
            .content(json.writeValueAsString(Map.of("currentPassword","1234","newPassword","4321")))).andExpect(status().isOk());
        mvc.perform(get("/api/me").cookie(employeeCookie)).andExpect(status().isUnauthorized());
        login(Map.of("username",username,"password","4321"));
        command("user.create",Map.of("name","Curta demais","username","curta_"+UUID.randomUUID().toString().substring(0,8),"password","123","role","ATENDIMENTO"),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        command("user.create",Map.of("name","Administrador","username","admin_"+UUID.randomUUID().toString().substring(0,8),"password","12345","role","ADMINISTRADOR"),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
    }
    @Test void legacyEmailLoginAndEmailOnlyCreationRemainCompatible() throws Exception {
        String legacyEmail="legacy-"+UUID.randomUUID()+"@example.test";
        UUID employee=create("user.create",Map.of("name","Cadastro antigo","email",legacyEmail,"password",password,"role","ATENDIMENTO"));
        Cookie legacy=login(legacyEmail,password);
        mvc.perform(get("/api/me").cookie(legacy)).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(employee.toString())).andExpect(jsonPath("$.email").value(legacyEmail));
        Cookie unified=login(Map.of("username",legacyEmail.toUpperCase(Locale.ROOT),"password",password));
        mvc.perform(get("/api/me").cookie(unified)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(employee.toString()));
    }
    @Test void usernameUniquenessAndAuthenticationAreScopedToUnit() throws Exception {
        String username="equipe_"+UUID.randomUUID().toString().substring(0,8);
        UUID employee=create("user.create",Map.of("name","Equipe local","username",username,"password","1234","role","ATENDIMENTO"));
        command("user.create",Map.of("name","Duplicado","username",username.toUpperCase(Locale.ROOT),"password","1234","role","ATENDIMENTO"),UUID.randomUUID().toString()).andExpect(status().isConflict());
        UUID otherUnit=UUID.randomUUID(),otherEmployee=UUID.randomUUID();
        db.update("INSERT INTO bar.establishments(id,name) VALUES(?,'Outra unidade de acesso')",otherUnit);
        db.update("INSERT INTO bar.users(id,establishment_id,name,username,password_hash) VALUES(?,?,'Equipe externa',?,?)",otherEmployee,otherUnit,username,encoder.encode("9876"));
        mvc.perform(post("/api/login").header("X-Bar-Request","1").contentType("application/json")
            .content(json.writeValueAsString(Map.of("username",username,"password","9876")))).andExpect(status().isUnauthorized());
        Cookie local=login(Map.of("username",username,"password","1234"));
        mvc.perform(get("/api/me").cookie(local)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(employee.toString()));
        String state=mvc.perform(get("/api/state").cookie(cookie)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(state.contains(otherEmployee.toString()));
    }
    @Test void usernameAndEmailAliasesShareLoginAttemptLimit() throws Exception {
        String username="duplo_"+UUID.randomUUID().toString().substring(0,8),address=username+"@example.test";
        create("user.create",Map.of("name","Dois acessos","username",username,"email",address,"password","1234","role","ATENDIMENTO"));
        for(int i=0;i<8;i++) {
            var body=i%2==0?Map.of("username",username,"password","errada"):Map.of("email",address,"password","errada");
            mvc.perform(post("/api/login").header("X-Bar-Request","1").contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/login").header("X-Bar-Request","1").contentType("application/json")
            .content(json.writeValueAsString(Map.of("username",username,"password","1234")))).andExpect(status().isTooManyRequests());
    }
    @Test void initialWaiterAccessIncludesOnlyExistingActiveTables() throws Exception {
        UUID area=create("area.save",Map.of("name","Liberação inicial "+UUID.randomUUID()));
        UUID available=create("table.save",Map.of("areaId",area,"number",UUID.randomUUID().toString().substring(0,12)));
        UUID archived=create("table.save",Map.of("areaId",area,"number",UUID.randomUUID().toString().substring(0,12)));
        create("table.delete",Map.of("tableId",archived));
        UUID employee=create("user.create",Map.of("name","Atendente liberado","username","liberado_"+UUID.randomUUID().toString().substring(0,8),"password","1234","role","ATENDIMENTO","allTables",true));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.table_authorizations WHERE establishment_id=? AND user_id=? AND table_id=? AND revoked_at IS NULL",Integer.class,unit,employee,available));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.table_authorizations WHERE user_id=? AND table_id=?",Integer.class,employee,archived));
        UUID future=create("table.save",Map.of("areaId",area,"number",UUID.randomUUID().toString().substring(0,12)));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.table_authorizations WHERE user_id=? AND table_id=?",Integer.class,employee,future));
    }
    @Test void deletingFreeTablePreservesHistoryAndReplaysOnce() throws Exception {
        var f=fixture();UUID order=order(f);
        UUID area=db.queryForObject("SELECT area_id FROM bar.bar_tables WHERE id=?",UUID.class,f.get("table"));
        String number=db.queryForObject("SELECT number FROM bar.bar_tables WHERE id=?",String.class,f.get("table"));
        UUID payment=create("payment.receive",Map.of("accountId",f.get("account"),"sessionId",f.get("session"),"method","PIX","amount","20.00"));
        create("table.authorize",Map.of("tableId",f.get("table"),"userId",user,"authorized",true));
        String key=UUID.randomUUID().toString();var body=Map.<String,Object>of("tableId",f.get("table"));
        String first=command("table.delete",body,key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(first,command("table.delete",body,key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.bar_tables WHERE id=?",Integer.class,f.get("table")));
        assertNull(db.queryForObject("SELECT table_id FROM bar.accounts WHERE id=?",UUID.class,f.get("account")));
        assertEquals(number,db.queryForObject("SELECT table_number_snapshot FROM bar.accounts WHERE id=?",String.class,f.get("account")));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.orders WHERE id=?",Integer.class,order));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.payments WHERE id=?",Integer.class,payment));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.table_authorizations WHERE establishment_id=? AND user_id=? AND table_id IS NULL AND table_number_snapshot=? AND revoked_at IS NOT NULL",Integer.class,unit,user,number));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.audit_events WHERE action='table.delete' AND resource_id=?",Integer.class,f.get("table")));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.outbox_events WHERE event_type='table.delete' AND aggregate_id=?",Integer.class,f.get("table")));
        mvc.perform(get("/api/accounts/"+f.get("account")).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        var state=json.readTree(mvc.perform(get("/api/state").cookie(cookie)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for(var table:state.path("tables")) assertNotEquals(f.get("table").toString(),table.path("id").asText());
        mvc.perform(get("/api/accounts/"+f.get("account")).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.account.table_number_snapshot").value(number));
        command("account.open",Map.of("tableId",f.get("table")),UUID.randomUUID().toString()).andExpect(status().isNotFound());
        command("table.save",Map.of("id",f.get("table"),"areaId",area,"number",number,"active",true),UUID.randomUUID().toString()).andExpect(status().isNotFound());
        UUID replacement=create("table.save",Map.of("areaId",area,"number",number));
        assertNotEquals(f.get("table"),replacement);create("account.open",Map.of("tableId",replacement));
        assertEquals(number,db.queryForObject("SELECT table_number_snapshot FROM bar.accounts WHERE id=?",String.class,f.get("account")));
    }

    @Test void occupiedTableCannotBeDeletedEvenIfDisplayedAsFree() throws Exception {
        var f=fixture();
        for(String status:List.of("OCUPADA","LIVRE")) {
            db.update("UPDATE bar.bar_tables SET status=? WHERE id=?",status,f.get("table"));
            String key=UUID.randomUUID().toString();
            command("table.delete",Map.of("tableId",f.get("table")),key).andExpect(status().isBadRequest());
            assertEquals(true,db.queryForObject("SELECT active FROM bar.bar_tables WHERE id=?",Boolean.class,f.get("table")));
            assertEquals("ABERTA",db.queryForObject("SELECT status FROM bar.accounts WHERE id=?",String.class,f.get("account")));
            assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.idempotency_records WHERE key=? AND operation='table.delete'",Integer.class,key));
        }
        assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.audit_events WHERE action='table.delete' AND resource_id=?",Integer.class,f.get("table")));
    }
    @Test void deletingTablesRespectsPermissionsAndUnit() throws Exception {
        UUID area=create("area.save",Map.of("name","Excluir "+UUID.randomUUID()));
        UUID table=create("table.save",Map.of("areaId",area,"number",UUID.randomUUID().toString().substring(0,12)));
        UUID other=UUID.randomUUID(),otherArea=UUID.randomUUID(),otherTable=UUID.randomUUID();
        db.update("INSERT INTO bar.establishments(id,name) VALUES(?,'Outra unidade de mesas')",other);
        db.update("INSERT INTO bar.areas(id,establishment_id,name) VALUES(?,?,'Sala externa')",otherArea,other);
        db.update("INSERT INTO bar.bar_tables(id,establishment_id,area_id,number) VALUES(?,?,?,'Externa')",otherTable,other,otherArea);
        command("table.delete",Map.of("tableId",otherTable),UUID.randomUUID().toString()).andExpect(status().isNotFound());
        db.update("DELETE FROM bar.user_roles WHERE establishment_id=? AND user_id=?",unit,user);
        command("table.delete",Map.of("tableId",table),UUID.randomUUID().toString()).andExpect(status().isForbidden());
        assertEquals(true,db.queryForObject("SELECT active FROM bar.bar_tables WHERE id=?",Boolean.class,table));
        assertEquals(true,db.queryForObject("SELECT active FROM bar.bar_tables WHERE id=?",Boolean.class,otherTable));
    }
    @Test void openingAndDeletingSameTableAreSerialized() throws Exception {
        UUID area=create("area.save",Map.of("name","Disputa "+UUID.randomUUID()));
        UUID table=create("table.save",Map.of("areaId",area,"number",UUID.randomUUID().toString().substring(0,12)));
        List<Integer> statuses;
        try(var pool=Executors.newFixedThreadPool(2)) {
            var opened=pool.submit(()->command("account.open",Map.of("tableId",table),UUID.randomUUID().toString()).andReturn().getResponse().getStatus());
            var deleted=pool.submit(()->command("table.delete",Map.of("tableId",table),UUID.randomUUID().toString()).andReturn().getResponse().getStatus());
            statuses=List.of(opened.get(),deleted.get());
        }
        assertEquals(1,statuses.stream().filter(s->s==200).count());
        assertTrue(statuses.stream().allMatch(s->Set.of(200,400,404).contains(s)));
        boolean active=db.queryForObject("SELECT count(*) FROM bar.bar_tables WHERE id=?",Integer.class,table)==1;
        assertEquals(active?1:0,db.queryForObject("SELECT count(*) FROM bar.accounts WHERE table_id=? AND status='ABERTA'",Integer.class,table));
        if(active)assertEquals("OCUPADA",db.queryForObject("SELECT status FROM bar.bar_tables WHERE id=?",String.class,table));
    }
    @Test void orderPaymentAndReplay() throws Exception {
        var f=fixture();order(f);
        String key=UUID.randomUUID().toString();var body=Map.<String,Object>of("accountId",f.get("account"),"sessionId",f.get("session"),"method","DINHEIRO","amount","20.00","tendered","50.00");
        String first=command("payment.receive",body,key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(first,command("payment.receive",body,key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.payments WHERE account_id=?",Integer.class,f.get("account")));
        assertEquals("FECHADA",db.queryForObject("SELECT status FROM bar.accounts WHERE id=?",String.class,f.get("account")));
        assertEquals("LIVRE",db.queryForObject("SELECT status FROM bar.bar_tables WHERE id=?",String.class,f.get("table")));
        var different=new HashMap<>(body);different.put("amount","10.00");command("payment.receive",different,key).andExpect(status().isConflict());
        UUID item=db.queryForObject("SELECT i.id FROM bar.order_items i JOIN bar.orders o ON o.id=i.order_id WHERE o.account_id=?",UUID.class,f.get("account"));
        create("item.status",Map.of("itemId",item,"status","ACEITO"));
        assertEquals(new BigDecimal("30.00"),db.queryForObject("SELECT change_amount FROM bar.payments WHERE account_id=?",BigDecimal.class,f.get("account")));
    }
    @Test void catalogSnapshotAndModifiers() throws Exception {
        var f=fixture();UUID group=create("modifier-group.save",Map.of("name","Adicionais","min",1,"max",1));
        UUID mod=create("modifier.save",Map.of("groupId",group,"name","Extra","price","2.50"));
        create("product.groups",Map.of("productId",f.get("product"),"groupIds",List.of(group)));
        command("order.create",Map.of("accountId",f.get("account"),"items",List.of(Map.of("productId",f.get("product"),"quantity","2"))),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        create("order.create",Map.of("accountId",f.get("account"),"items",List.of(Map.of("productId",f.get("product"),"quantity","2","modifiers",List.of(Map.of("id",mod,"quantity","1"))))));
        create("product.save",Map.of("id",f.get("product"),"categoryId",f.get("category"),"name","Novo nome","price","99.00","destination","BAR"));
        assertEquals(0,new BigDecimal("25.00").compareTo(db.queryForObject("SELECT total FROM bar.account_totals WHERE account_id=?",BigDecimal.class,f.get("account"))));
    }
    @Test void cashCloseAndCorrection() throws Exception {
        var f=fixture();order(f);create("payment.receive",Map.of("accountId",f.get("account"),"sessionId",f.get("session"),"method","PIX","amount","20.00"));
        Map<String,String> counted=Map.of("DINHEIRO","50.00","PIX","20.00","DEBITO","0","CREDITO","0","VOUCHER","0","OUTRO","0");
        command("cash.close",Map.of("sessionId",f.get("session"),"counted",Map.of("DINHEIRO","50")),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        UUID closure=create("cash.close",Map.of("sessionId",f.get("session"),"counted",counted));
        create("cash.correct",Map.of("sessionId",f.get("session"),"correctsId",closure,"reason","Nova contagem","counted",counted));
        command("cash.move",Map.of("sessionId",f.get("session"),"kind","SUPRIMENTO","amount","1.00","reason","Fechado"),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
    }
    @Test void refundsDoNotReoccupyReusedTable() throws Exception {
        var f=fixture();order(f);UUID payment=create("payment.receive",Map.of("accountId",f.get("account"),"sessionId",f.get("session"),"method","PIX","amount","20.00"));
        UUID newAccount=create("account.open",Map.of("tableId",f.get("table")));
        create("account.reopen",Map.of("accountId",f.get("account"),"reason","Estorno"));
        create("payment.reverse",Map.of("paymentId",payment,"sessionId",f.get("session"),"amount","5.00","method","PIX","reason","Devolução parcial"));
        assertEquals("OCUPADA",db.queryForObject("SELECT status FROM bar.bar_tables WHERE id=?",String.class,f.get("table")));
        assertEquals(f.get("table"),db.queryForObject("SELECT table_id FROM bar.accounts WHERE id=?",UUID.class,newAccount));
        assertNull(db.queryForObject("SELECT table_id FROM bar.accounts WHERE id=?",UUID.class,f.get("account")));
    }
    @Test void passwordRevokesEverySession() throws Exception {
        Cookie other=login(email,password);
        mvc.perform(post("/api/password").cookie(cookie).header("X-Bar-Request","1").contentType("application/json").content(json.writeValueAsString(Map.of("currentPassword",password,"newPassword","Nova-senha-segura-123!")))).andExpect(status().isOk());
        mvc.perform(get("/api/me").cookie(other)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(cookie)).andExpect(status().isUnauthorized());
        login(email,"Nova-senha-segura-123!");
    }
    @Test void concurrentPaymentsAndAudit() throws Exception {
        var f=fixture();order(f);var body=Map.<String,Object>of("accountId",f.get("account"),"sessionId",f.get("session"),"method","PIX","amount","20.00");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->command("payment.receive",body,UUID.randomUUID().toString()).andReturn().getResponse().getStatus());
            var second=pool.submit(()->command("payment.receive",body,UUID.randomUUID().toString()).andReturn().getResponse().getStatus());
            var statuses=List.of(first.get(),second.get());assertEquals(1,statuses.stream().filter(s->s==200).count());assertTrue(statuses.stream().anyMatch(s->s==400||s==409));
        }
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.payments WHERE account_id=?",Integer.class,f.get("account")));
        assertTrue(db.queryForObject("SELECT count(*) FROM bar.audit_events WHERE actor_id=?",Integer.class,user)>0);
        assertTrue(db.queryForObject("SELECT count(*) FROM bar.outbox_events WHERE establishment_id=?",Integer.class,unit)>0);
    }
    @Test void reportsAndRateLimit() throws Exception {
        mvc.perform(get("/api/reports").cookie(cookie).param("from","2026-01-01").param("to","2026-12-31")).andExpect(status().isOk());
        for(int i=0;i<8;i++) mvc.perform(post("/api/login").header("X-Bar-Request","1").contentType("application/json").content(json.writeValueAsString(Map.of("email","invalido@example.test","password","incorreta")))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/login").header("X-Bar-Request","1").contentType("application/json").content(json.writeValueAsString(Map.of("email","invalido@example.test","password","incorreta")))).andExpect(status().isTooManyRequests());
    }
    @Test void waiterRequiresTableAuthorization() throws Exception {
        var f=fixture();db.update("DELETE FROM bar.role_permissions WHERE establishment_id=? AND role_id IN (SELECT role_id FROM bar.user_roles WHERE user_id=?) AND permission_id NOT IN(SELECT id FROM bar.permissions WHERE code IN ('account.open','order.create'))",unit,user);
        command("order.create",Map.of("accountId",f.get("account"),"items",List.of(Map.of("productId",f.get("product"),"quantity","1"))),UUID.randomUUID().toString()).andExpect(status().isForbidden());
        db.update("INSERT INTO bar.table_authorizations(establishment_id,user_id,table_id,granted_by) VALUES(?,?,?,?)",unit,user,f.get("table"),user);
        order(f);
    }
    @Test void preparationCountsAndPermission() throws Exception {
        var response=mvc.perform(get("/api/setup").cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.counts.products").isNumber()).andExpect(jsonPath("$.certificateAvailable").value(true)).andReturn();
        assertFalse(response.getResponse().getContentAsString().contains("do-not-expose"));
        db.update("DELETE FROM bar.user_roles WHERE establishment_id=? AND user_id=?",unit,user);
        mvc.perform(get("/api/setup").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(post("/api/setup/backup").cookie(cookie).header("X-Bar-Request","1")).andExpect(status().isForbidden());
    }
    @Test void qrAndCertificateAreRestrictedToConfiguredSources() throws Exception {
        mvc.perform(get("/api/setup/qr").cookie(cookie).param("address","https://192.168.0.8:8443")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("image/svg+xml"));
        mvc.perform(get("/api/setup/qr").cookie(cookie).param("address","https://192.168.0.8:18443")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("image/svg+xml"));
        mvc.perform(get("/api/setup/qr").cookie(cookie).param("address","https://bar-api.example.azurecontainerapps.io")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("image/svg+xml"));
        mvc.perform(get("/api/setup/qr").cookie(cookie).param("address","https://outside.example")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/setup/certificate").cookie(cookie)).andExpect(status().isOk()).andExpect(content().string("TEST PUBLIC CERTIFICATE"));
    }
    @Test void backupQueueDoesNotRunInsideTraining() throws Exception {
        boolean training=Boolean.parseBoolean(System.getenv("BAR_TRAINING"));
        mvc.perform(post("/api/setup/backup").cookie(cookie).header("X-Bar-Request","1"))
            .andExpect(status().is(training?503:202));
        assertEquals(!training,Files.exists(setupDirectory().resolve("request.json")));
    }
    @AfterAll void cleanupSetupFiles() throws Exception {
        if(setupDirectory==null)return;
        Path base=setupDirectory.toAbsolutePath().normalize();
        assertTrue(base.getFileName().toString().startsWith("bar-setup-")&&base.getParent().equals(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()));
        try(var files=Files.list(base)) {for(Path file:files.toList()) {assertTrue(Files.isRegularFile(file));Files.delete(file);}}
        Files.delete(base);
    }
}
