package br.com.bardozico;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Regressões da fronteira entre o administrador local e o atendimento no celular. */
@SpringBootTest(properties={"bar.open-access=true","bar.device-access=true","server.forward-headers-strategy=none"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeviceAccessTest {
    private static final UUID UNIT=UUID.randomUUID();
    private static final String LOCAL="127.0.0.1",LAN="192.168.0.47";
    private static final Set<String> ATTENDANCE=Set.of("table.create","account.open","order.create");
    private static final String PASSWORD="Senha-de-teste-dispositivo-123!";
    private final UUID owner=UUID.randomUUID();
    private final String email=owner+"@device.example.test";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("bar.unit-id",()->UNIT.toString());}
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired Identity identity;
    @Autowired Argon2PasswordEncoder encoder;

    @BeforeAll void seedUnit(){
        assertTrue(db.queryForObject("SELECT current_database()",String.class).startsWith("bar_app_test_"),"Use somente o banco descartável dos testes.");
        db.update("INSERT INTO bar.establishments(id,name) VALUES(?,'Teste PC e celular')",UNIT);
        db.update("INSERT INTO bar.users(id,establishment_id,name,email,password_hash) VALUES(?,?,'Administrador do computador',?,?)",owner,UNIT,email,encoder.encode(PASSWORD));
        UUID role=UUID.randomUUID();
        db.update("INSERT INTO bar.roles(id,establishment_id,name) VALUES(?,?,'Administrador de dispositivos')",role,UNIT);
        db.update("INSERT INTO bar.user_roles(establishment_id,user_id,role_id) VALUES(?,?,?)",UNIT,owner,role);
        db.update("INSERT INTO bar.role_permissions(establishment_id,role_id,permission_id) SELECT ?,?,id FROM bar.permissions",UNIT,role);
    }

    private MockHttpServletRequestBuilder remote(MockHttpServletRequestBuilder request,String address){
        return request.with(r->{r.setRemoteAddr(address);return r;});
    }
    private JsonNode read(MockHttpServletRequestBuilder request)throws Exception{
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode me(String address)throws Exception{return read(remote(get("/api/me"),address));}
    private JsonNode state(String address)throws Exception{return read(remote(get("/api/state"),address));}
    private ResultActions command(String address,String operation,Map<String,Object> body,String key)throws Exception{
        return mvc.perform(remote(post("/api/commands/"+operation),address).header("X-Bar-Request","1").header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsString(body)));
    }
    private UUID create(String address,String operation,Map<String,Object> body)throws Exception{
        var response=command(address,operation,body,UUID.randomUUID().toString()).andExpect(status().isOk()).andReturn().getResponse();
        return UUID.fromString(json.readTree(response.getContentAsString()).path("id").asText());
    }
    private UUID create(String operation,Map<String,Object> body)throws Exception{return create(LOCAL,operation,body);}
    @Test void onlyTheAdministratorCanRequestAPrintableGuide()throws Exception {
        UUID area=create("area.save",Map.of("name","Guia "+UUID.randomUUID())),table=create("table.save",Map.of("number",UUID.randomUUID().toString().substring(0,10),"areaId",area));
        UUID account=create("account.open",Map.of("tableId",table));String path="/api/accounts/"+account+"/guide.pdf";
        mvc.perform(remote(get(path),LOCAL)).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        mvc.perform(remote(get(path),LAN).accept("application/pdf").header("Host","127.0.0.1").header("X-Forwarded-For",LOCAL)).andExpect(status().isForbidden());
        mvc.perform(remote(get(path),LOCAL).accept("application/pdf").header("User-Agent","BarDoZicoAndroid/0.2.3")).andExpect(status().isForbidden());
        var controller=new TableGuideController(db,false);
        ApiError forbidden=assertThrows(ApiError.class,()->controller.guide(new Actor(owner,UNIT,null,"Teste",null,null,Set.of("account.open")),account));assertEquals(403,forbidden.status);
        ApiError foreign=assertThrows(ApiError.class,()->controller.guide(new Actor(owner,UUID.randomUUID(),null,"Teste",null,null,Set.of("report.view")),account));assertEquals(404,foreign.status);
    }
    private Set<String> permissions(JsonNode user){
        var result=new HashSet<String>();user.path("permissions").forEach(p->result.add(p.asText()));return result;
    }
    private JsonNode row(JsonNode rows,UUID id){
        for(var row:rows)if(id.toString().equals(row.path("id").asText()))return row;
        fail("Registro esperado ausente: "+id);return null;
    }
    private boolean contains(JsonNode rows,UUID id){
        for(var row:rows)if(id.toString().equals(row.path("id").asText()))return true;return false;
    }
    private Map<String,UUID> fixture()throws Exception{
        UUID area=create("area.save",Map.of("name","Local "+UUID.randomUUID()));
        UUID table=create("table.save",Map.of("areaId",area,"number",UUID.randomUUID().toString().substring(0,12)));
        UUID category=create("category.save",Map.of("name","Bebidas "+UUID.randomUUID()));
        UUID product=create("product.save",Map.of("categoryId",category,"name","Produto "+UUID.randomUUID(),"price","10.00","destination","SEM_PREPARO","stockControlled",true,"stockQuantity","10","stockMinimum","2"));
        return Map.of("area",area,"table",table,"category",category,"product",product);
    }
    private Map<String,Object> order(UUID account,UUID product){
        return Map.of("accountId",account,"items",List.of(Map.of("productId",product,"quantity","1")));
    }

    @Test @Order(0) void mobileCanCreateTheFirstTableWithAnAtomicDefaultArea()throws Exception{
        assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.areas WHERE establishment_id=?",Integer.class,UNIT));
        String key=UUID.randomUUID().toString();var body=Map.<String,Object>of("number","Primeira");
        var first=command(LAN,"table.create",body,key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var replay=command(LAN,"table.create",body,key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(first),json.readTree(replay));
        UUID table=UUID.fromString(json.readTree(first).path("id").asText());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.areas WHERE establishment_id=? AND name='Salão' AND active",Integer.class,UNIT));
        assertEquals("LIVRE",row(state(LOCAL).path("tables"),table).path("status").asText());
        assertTrue(contains(state(LAN).path("tables"),table));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.audit_events WHERE establishment_id=? AND action='table.create' AND resource_id=?",Integer.class,UNIT,table));
        command(LAN,"table.create",Map.of("number","Outro número"),key).andExpect(status().isConflict());
    }

    @Test void mobileCreatesTableInAnExistingAreaAndCanAttendIt()throws Exception{
        var f=fixture();String number=UUID.randomUUID().toString().substring(0,12);
        UUID table=create(LAN,"table.create",Map.of("number",number,"areaId",f.get("area")));
        var desktop=row(state(LOCAL).path("tables"),table);
        assertEquals(number,desktop.path("number").asText());assertEquals(f.get("area").toString(),desktop.path("area_id").asText());
        UUID account=create(LAN,"account.open",Map.of("tableId",table));create(LAN,"order.create",order(account,f.get("product")));
        assertEquals("10.00",read(remote(get("/api/accounts/"+account),LAN)).path("account").path("total").asText());
    }

    @Test void createCannotBeUsedToEditOrDeleteTablesOrInjectManagementFields()throws Exception{
        var f=fixture();var original=db.queryForMap("SELECT * FROM bar.bar_tables WHERE id=?",f.get("table"));
        for(var extra:List.of(Map.<String,Object>of("id",f.get("table")),Map.<String,Object>of("id",UUID.randomUUID()),Map.<String,Object>of("active",false),Map.<String,Object>of("status","OCUPADA"),Map.<String,Object>of("establishmentId",UUID.randomUUID()))) {
            var body=new HashMap<String,Object>(Map.of("number","Alteração indevida","areaId",f.get("area")));body.putAll(extra);
            command(LAN,"table.create",body,UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        }
        command(LAN,"table.save",Map.of("id",f.get("table"),"number","Alteração indevida","areaId",f.get("area")),UUID.randomUUID().toString()).andExpect(status().isForbidden());
        command(LAN,"table.delete",Map.of("tableId",f.get("table")),UUID.randomUUID().toString()).andExpect(status().isForbidden());
        assertEquals(original,db.queryForMap("SELECT * FROM bar.bar_tables WHERE id=?",f.get("table")));
    }

    @Test void creationRejectsEmptyLongDuplicateAndInactiveAreaInputs()throws Exception{
        var f=fixture();
        for(String number:List.of("","  ","x".repeat(21)))command(LAN,"table.create",Map.of("number",number,"areaId",f.get("area")),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        String number=db.queryForObject("SELECT number FROM bar.bar_tables WHERE id=?",String.class,f.get("table"));
        command(LAN,"table.create",Map.of("number",number,"areaId",f.get("area")),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        UUID inactive=create("area.save",Map.of("name","Local inativo "+UUID.randomUUID(),"active",false));
        command(LAN,"table.create",Map.of("number","Mesa inválida","areaId",inactive),UUID.randomUUID().toString()).andExpect(status().is4xxClientError());
    }

    @Test void mobileAndComputerCannotCreateTheSameTableConcurrently()throws Exception{
        var f=fixture();String number=UUID.randomUUID().toString().substring(0,12);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var mobile=pool.submit(()->{start.await();return command(LAN,"table.create",Map.of("number",number,"areaId",f.get("area")),UUID.randomUUID().toString()).andReturn().getResponse().getStatus();});
            var desktop=pool.submit(()->{start.await();return command(LOCAL,"table.save",Map.of("number",number,"areaId",f.get("area")),UUID.randomUUID().toString()).andReturn().getResponse().getStatus();});
            start.countDown();assertEquals(Set.of(200,400),Set.of(mobile.get(20,TimeUnit.SECONDS),desktop.get(20,TimeUnit.SECONDS)));
        }
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.bar_tables WHERE establishment_id=? AND number=?",Integer.class,UNIT,number));
    }

    @Test void localComputerHasAdministrationAndLanGetsAttendanceOnly()throws Exception{
        for(String address:List.of(LOCAL,"::1")){
            var user=me(address);assertEquals(owner.toString(),user.path("id").asText());
            assertTrue(permissions(user).contains("user.manage"));assertTrue(user.path("deviceAccess").asBoolean());
            assertTrue(user.path("accessMode").isTextual());
        }
        for(String address:List.of(LAN,"2001:db8::47")){
            var user=me(address);assertNotEquals(owner.toString(),user.path("id").asText());
            assertEquals(ATTENDANCE,permissions(user));assertTrue(user.path("deviceAccess").asBoolean());
            assertNotEquals(me(LOCAL).path("accessMode").asText(),user.path("accessMode").asText());
        }
    }

    @Test void androidMarkerCanOnlyReduceEvenLoopbackAccess()throws Exception{
        var user=read(remote(get("/api/me"),LOCAL).header("User-Agent","Mozilla/5.0 BarDoZicoAndroid/0.2.2"));
        assertEquals(ATTENDANCE,permissions(user));assertNotEquals(owner.toString(),user.path("id").asText());
        mvc.perform(remote(post("/api/commands/cash.open"),LOCAL).header("User-Agent","BarDoZicoAndroid/0.2.2").header("X-Bar-Request","1").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test void adminCookieCannotElevateLanAndLoopbackIgnoresSelectedStaff()throws Exception{
        Cookie admin=new Cookie("BAR_SESSION",identity.login(email,PASSWORD,LOCAL));
        var external=read(remote(get("/api/me"),LAN).cookie(admin));assertEquals(ATTENDANCE,permissions(external));
        mvc.perform(remote(post("/api/commands/staff.save"),LAN).cookie(admin).header("X-Bar-Request","1").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        UUID staff=create("staff.save",Map.of("name","Funcionário selecionado anteriormente","role","ATENDIMENTO","allTables",true));
        Cookie selected=new Cookie("BAR_SESSION",identity.selectOperator(staff));
        var local=read(remote(get("/api/me"),LOCAL).cookie(selected));assertEquals(owner.toString(),local.path("id").asText());assertTrue(permissions(local).contains("user.manage"));
    }

    @Test void spoofedProxyHostAndDesktopHeadersNeverGrantAdministration()throws Exception{
        var user=read(remote(get("/api/me"),LAN)
            .header("Host","localhost").header("X-Forwarded-For","127.0.0.1")
            .header("X-Real-IP","::1").header("Forwarded","for=127.0.0.1;host=localhost;proto=https")
            .header("X-Bar-Role","ADMINISTRADOR").header("X-Bar-Device","desktop")
            .header("User-Agent","BarDoZicoDesktop/25"));
        assertEquals(ATTENDANCE,permissions(user));assertNotEquals(owner.toString(),user.path("id").asText());
    }

    @Test void deviceModeCannotSwitchOperatorLoginOrPasswordFromEitherDevice()throws Exception{
        for(String address:List.of(LOCAL,LAN))for(String endpoint:List.of("operator","login","password")){
            mvc.perform(remote(post("/api/"+endpoint),address).header("X-Bar-Request","1").contentType("application/json")
                .content(json.writeValueAsString(Map.of("userId",owner,"email",email,"password",PASSWORD,"currentPassword",PASSWORD,"newPassword","Outro-segredo-123!"))))
                .andExpect(status().isForbidden());
        }
    }

    @Test void mobileOpensTablesAndAddsOrdersWithPersistentIdempotency()throws Exception{
        var f=fixture();UUID technical=UUID.fromString(me(LAN).path("id").asText());
        String openingKey=UUID.randomUUID().toString();Map<String,Object> opening=Map.of("tableId",f.get("table"));
        var first=command(LAN,"account.open",opening,openingKey).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var replay=command(LAN,"account.open",opening,openingKey).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(first),json.readTree(replay));UUID account=UUID.fromString(json.readTree(first).path("id").asText());
        String orderKey=UUID.randomUUID().toString();var body=order(account,f.get("product"));
        first=command(LAN,"order.create",body,orderKey).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        replay=command(LAN,"order.create",body,orderKey).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertEquals(json.readTree(first),json.readTree(replay));
        assertEquals(technical,db.queryForObject("SELECT opened_by FROM bar.accounts WHERE id=?",UUID.class,account));
        assertEquals(technical,db.queryForObject("SELECT waiter_id FROM bar.orders WHERE account_id=?",UUID.class,account));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.orders WHERE account_id=?",Integer.class,account));
        assertEquals(0,new BigDecimal("9").compareTo(db.queryForObject("SELECT stock_quantity FROM bar.products WHERE id=?",BigDecimal.class,f.get("product"))));
        assertTrue(contains(state(LOCAL).path("accounts"),account));
        command(LAN,"order.create",Map.of("accountId",account,"items",List.of(Map.of("productId",f.get("product"),"quantity","2"))),orderKey).andExpect(status().isConflict());
    }

    @Test void attendanceIncludesNewTablesWithoutManualGrants()throws Exception{
        UUID technical=UUID.fromString(me(LAN).path("id").asText());var f=fixture();
        assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.table_authorizations WHERE establishment_id=? AND user_id=?",Integer.class,UNIT,technical));
        UUID account=create(LAN,"account.open",Map.of("tableId",f.get("table")));create(LAN,"order.create",order(account,f.get("product")));
        assertEquals(account.toString(),read(remote(get("/api/accounts/"+account),LAN)).path("account").path("id").asText());
    }

    @Test void attendanceCannotExecuteAdministrativeOrFinancialCommands()throws Exception{
        for(String operation:List.of("area.save","area.delete","table.save","table.delete","table.authorize","category.save","product.save","product.delete","product.availability","modifier-group.save","modifier.save","product.groups","stock.move","staff.save","staff.delete","user.create","user.active","role.save","terminal.save","cash.start","cash.open","cash.move","cash.close","cash.correct","payment.receive","payment.reverse","account.adjust","account.close","account.reopen","item.status","item.cancel"))
            command(LAN,operation,Map.of(),UUID.randomUUID().toString()).andExpect(status().isForbidden());
    }

    @Test void attendanceCannotReadReportsAuditSetupOrRequestBackup()throws Exception{
        for(String endpoint:List.of("/api/reports?from=2026-10-20&to=2026-10-20","/api/reports/sales?from=2026-10-20&to=2026-10-20","/api/audit","/api/setup","/api/setup/certificate","/api/setup/qr?address=https://127.0.0.1:8443"))
            mvc.perform(remote(get(endpoint),LAN)).andExpect(status().isForbidden());
        mvc.perform(remote(post("/api/setup/backup"),LAN).header("X-Bar-Request","1")).andExpect(status().isForbidden());
    }

    @Test void mobileStateContainsConsumptionCatalogWithoutManagementDataOrClosedAccounts()throws Exception{
        var f=fixture();UUID closed=create("account.open",Map.of("tableId",f.get("table")));create("account.close",Map.of("accountId",closed));
        UUID active=create(LAN,"account.open",Map.of("tableId",f.get("table")));create(LAN,"order.create",order(active,f.get("product")));
        var mobile=state(LAN);
        for(String field:List.of("users","roles","permissions","rolePermissions","stockMovements","terminals","sessions","cashTotals","cashSummaries","payments","closures","closureLines"))
            assertTrue(mobile.path(field).isMissingNode()||mobile.path(field).isEmpty(),"O celular recebeu dados de gestão: "+field);
        assertFalse(contains(mobile.path("accounts"),closed));assertTrue(contains(mobile.path("accounts"),active));
        JsonNode product=row(mobile.path("products"),f.get("product"));
        Set<String> allowed=Set.of("id","category_id","name","price","active","available","sellable");
        product.fieldNames().forEachRemaining(field->assertTrue(allowed.contains(field),"Campo administrativo no catálogo do celular: "+field));
        assertEquals(0,new BigDecimal("10").compareTo(new BigDecimal(product.path("price").asText())));assertTrue(product.path("sellable").asBoolean());
        assertTrue(row(state(LOCAL).path("products"),f.get("product")).has("stock_quantity"));
    }

    @Test void consumptionDetailOmitsPaymentsAdjustmentsAndClosedOrDetachedAccounts()throws Exception{
        var f=fixture();UUID account=create(LAN,"account.open",Map.of("tableId",f.get("table")));create(LAN,"order.create",order(account,f.get("product")));
        create("account.adjust",Map.of("accountId",account,"kind","DESCONTO","amount","1","reason","Desconto administrativo"));
        UUID terminal=create("terminal.save",Map.of("name","Detalhes "+UUID.randomUUID()));UUID cash=create("cash.open",Map.of("terminalId",terminal));
        create("payment.receive",Map.of("accountId",account,"sessionId",cash,"method","PIX","amount","5"));
        var detail=read(remote(get("/api/accounts/"+account),LAN));assertEquals(1,detail.path("items").size());
        assertTrue(detail.path("payments").isMissingNode()||detail.path("payments").isEmpty());assertTrue(detail.path("adjustments").isMissingNode()||detail.path("adjustments").isEmpty());
        create("payment.receive",Map.of("accountId",account,"sessionId",cash,"method","PIX","amount","4"));
        mvc.perform(remote(get("/api/accounts/"+account),LAN)).andExpect(status().is4xxClientError());
        create("account.reopen",Map.of("accountId",account,"reason","Conferência no computador"));
        mvc.perform(remote(get("/api/accounts/"+account),LAN)).andExpect(status().is4xxClientError());
        assertFalse(contains(state(LAN).path("accounts"),account));
        assertEquals(account.toString(),read(remote(get("/api/accounts/"+account),LOCAL)).path("account").path("id").asText());
    }

    @Test void technicalUserIsHiddenAndCannotBeManagedAsAnEmployee()throws Exception{
        UUID technical=UUID.fromString(me(LAN).path("id").asText());assertFalse(contains(state(LOCAL).path("users"),technical));
        assertTrue(db.queryForObject("SELECT system_attendant FROM bar.users WHERE establishment_id=? AND id=?",Boolean.class,UNIT,technical));
        command(LOCAL,"staff.delete",Map.of("userId",technical),UUID.randomUUID().toString()).andExpect(status().is4xxClientError());
        command(LOCAL,"user.active",Map.of("userId",technical,"active",false),UUID.randomUUID().toString()).andExpect(status().is4xxClientError());
        command(LOCAL,"staff.save",Map.of("id",technical,"name","Tentativa de alteração","role","ADMINISTRADOR"),UUID.randomUUID().toString()).andExpect(status().is4xxClientError());
        assertTrue(db.queryForObject("SELECT active FROM bar.users WHERE id=?",Boolean.class,technical));
        assertNull(db.queryForObject("SELECT deleted_at FROM bar.users WHERE id=?",java.sql.Timestamp.class,technical));
        assertEquals(ATTENDANCE,permissions(me(LAN)));
    }

    @Test void concurrentMobilesShareOneStableTechnicalIdentityWithoutRoleEscalation()throws Exception{
        var ids=new HashSet<String>();
        try(var pool=Executors.newFixedThreadPool(4)){
            var start=new CountDownLatch(1);var calls=new ArrayList<Future<String>>();
            for(int i=0;i<8;i++)calls.add(pool.submit(()->{start.await();var user=me(LAN);assertEquals(ATTENDANCE,permissions(user));return user.path("id").asText();}));
            start.countDown();for(var call:calls)ids.add(call.get(20,TimeUnit.SECONDS));
        }
        assertEquals(1,ids.size());UUID technical=UUID.fromString(ids.iterator().next());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.users WHERE establishment_id=? AND system_attendant",Integer.class,UNIT));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.user_roles WHERE establishment_id=? AND user_id=?",Integer.class,UNIT,technical));
    }

    @Test void attendanceCannotReadOrChangeAnotherUnit()throws Exception{
        UUID other=UUID.randomUUID(),staff=UUID.randomUUID(),area=UUID.randomUUID(),table=UUID.randomUUID(),account=UUID.randomUUID();
        db.update("INSERT INTO bar.establishments(id,name) VALUES(?,'Outra unidade')",other);
        db.update("INSERT INTO bar.users(id,establishment_id,name,password_hash) VALUES(?,?,'Responsável externo','hash-inacessivel-de-unidade-externa')",staff,other);
        db.update("INSERT INTO bar.areas(id,establishment_id,name) VALUES(?,?,'Local externo')",area,other);
        db.update("INSERT INTO bar.bar_tables(id,establishment_id,area_id,number) VALUES(?,?,?,'Outra unidade')",table,other,area);
        db.update("INSERT INTO bar.accounts(id,establishment_id,table_id,opened_by) VALUES(?,?,?,?)",account,other,table,staff);
        mvc.perform(remote(get("/api/accounts/"+account),LAN)).andExpect(status().is4xxClientError());
        command(LAN,"account.open",Map.of("tableId",table),UUID.randomUUID().toString()).andExpect(status().is4xxClientError());
        command(LAN,"table.create",Map.of("number","Outra unidade","areaId",area),UUID.randomUUID().toString()).andExpect(status().is4xxClientError());
        command(LAN,"order.create",Map.of("accountId",account,"items",List.of()),UUID.randomUUID().toString()).andExpect(status().is4xxClientError());
        var mobile=state(LAN);assertFalse(contains(mobile.path("areas"),area));assertFalse(contains(mobile.path("tables"),table));assertFalse(contains(mobile.path("accounts"),account));
    }

    @Test void cashDefaultsToZeroAndDuplicateOpeningHasAUsefulMessage()throws Exception{
        UUID terminal=create("terminal.save",Map.of("name","Caixa zero "+UUID.randomUUID()));String key=UUID.randomUUID().toString();var opening=Map.<String,Object>of("terminalId",terminal);
        var initial=command(LOCAL,"cash.open",opening,key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var repeated=command(LOCAL,"cash.open",opening,key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertEquals(json.readTree(initial),json.readTree(repeated));
        UUID cash=UUID.fromString(json.readTree(initial).path("id").asText());assertEquals(0,db.queryForObject("SELECT opening_amount FROM bar.cash_sessions WHERE id=?",BigDecimal.class,cash).signum());
        command(LOCAL,"cash.open",opening,UUID.randomUUID().toString()).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Este caixa já está aberto")));
        UUID other=create("terminal.save",Map.of("name","Troco inicial "+UUID.randomUUID()));UUID explicit=create("cash.open",Map.of("terminalId",other,"openingAmount","25.50"));
        assertEquals(0,new BigDecimal("25.50").compareTo(db.queryForObject("SELECT opening_amount FROM bar.cash_sessions WHERE id=?",BigDecimal.class,explicit)));
    }

    @Test void twoConcurrentCashOpeningsCreateOneSessionAndExplainTheConflict()throws Exception{
        UUID terminal=create("terminal.save",Map.of("name","Abertura simultânea "+UUID.randomUUID()));
        try(var pool=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);Callable<org.springframework.mock.web.MockHttpServletResponse> attempt=()->{start.await();return command(LOCAL,"cash.open",Map.of("terminalId",terminal),UUID.randomUUID().toString()).andReturn().getResponse();};
            var first=pool.submit(attempt);var second=pool.submit(attempt);start.countDown();var responses=List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
            assertEquals(Set.of(200,409),Set.of(responses.get(0).getStatus(),responses.get(1).getStatus()));
            for(var response:responses)if(response.getStatus()==409)assertTrue(json.readTree(response.getContentAsString()).path("error").asText().contains("Este caixa já está aberto"));
        }
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.cash_sessions WHERE establishment_id=? AND terminal_id=? AND status='ABERTA'",Integer.class,UNIT,terminal));
    }
}
