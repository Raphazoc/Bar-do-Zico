package br.com.bardozico;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import java.util.*;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="bar.open-access=true") @AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NewFrontTest {
    static final UUID UNIT=UUID.randomUUID();UUID owner;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("bar.unit-id",()->UNIT.toString());}
    @Autowired Operations operations;@Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
    @BeforeAll void setup(){
        assertTrue(db.queryForObject("SELECT current_database()",String.class).startsWith("bar_app_test_"));
        db.update("INSERT INTO bar.establishments(id,name) VALUES(?,'Nova interface de teste')",UNIT);
        owner=UUID.randomUUID();UUID role=UUID.randomUUID();
        db.update("INSERT INTO bar.users(id,establishment_id,name,password_hash) VALUES(?,?,'Responsável local','hash-inutilizado-para-operacao-local')",owner,UNIT);
        db.update("INSERT INTO bar.roles(id,establishment_id,name) VALUES(?,?,'Responsável')",role,UNIT);
        db.update("INSERT INTO bar.user_roles(establishment_id,user_id,role_id) VALUES(?,?,?)",UNIT,owner,role);
        db.update("INSERT INTO bar.role_permissions(establishment_id,role_id,permission_id) SELECT ?,?,id FROM bar.permissions",UNIT,role);
    }
    ResultActions command(String operation,Map<String,Object> body,String key)throws Exception{return mvc.perform(post("/api/commands/"+operation).header("X-Bar-Request","1").header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsString(body)));}
    UUID create(String op,Map<String,Object> b)throws Exception{return UUID.fromString(json.readTree(command(op,b,UUID.randomUUID().toString()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText());}
    Map<String,UUID> fixture(String quantity)throws Exception{
        UUID area=create("area.save",Map.of("name","Local "+UUID.randomUUID())),table=create("table.save",Map.of("areaId",area,"number",UUID.randomUUID().toString().substring(0,12))),category=create("category.save",Map.of("name","Bebidas "+UUID.randomUUID()));
        UUID product=create("product.save",Map.of("categoryId",category,"name","Produto "+UUID.randomUUID(),"price","10.00","destination","BAR","stockControlled",true,"stockQuantity",quantity,"stockMinimum","2"));
        UUID account=create("account.open",Map.of("tableId",table));return Map.of("product",product,"account",account,"table",table,"category",category);
    }
    BigDecimal balance(UUID product){return db.queryForObject("SELECT stock_quantity FROM bar.products WHERE establishment_id=? AND id=?",BigDecimal.class,UNIT,product);}
    Map<String,Object> order(Map<String,UUID> f,String quantity){return Map.of("accountId",f.get("account"),"items",List.of(Map.of("productId",f.get("product"),"quantity",quantity)));}
    @Test void opensWithoutLoginButRejectsForeignOrigin()throws Exception{
        mvc.perform(get("/api/me")).andExpect(status().isOk()).andExpect(jsonPath("$.openAccess").value(true)).andExpect(jsonPath("$.name").value("Responsável local"));
        mvc.perform(get("/api/state")).andExpect(status().isOk());
        mvc.perform(post("/api/commands/area.save").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/commands/area.save").header("X-Bar-Request","1").header("Origin","https://outro.example").contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
    @Test void staffWithoutCredentialsCanBeEditedAndProfileSelected()throws Exception{
        UUID staff=create("staff.save",Map.of("name","Atendente sem acesso","role","ATENDIMENTO","allTables",true));
        assertNull(db.queryForObject("SELECT email FROM bar.users WHERE id=?",String.class,staff));assertNull(db.queryForObject("SELECT username FROM bar.users WHERE id=?",String.class,staff));
        UUID role=create("role.save",Map.of("name","Consulta "+UUID.randomUUID(),"permissions",List.of("order.create")));
        create("staff.save",Map.of("id",staff,"name","Nome corrigido","roleId",role));
        assertEquals("Nome corrigido",db.queryForObject("SELECT name FROM bar.users WHERE id=?",String.class,staff));
        var selection=mvc.perform(post("/api/operator").header("X-Bar-Request","1").contentType("application/json").content(json.writeValueAsString(Map.of("userId",staff)))).andExpect(status().isOk()).andReturn().getResponse().getHeader("Set-Cookie");
        String pair=selection.split(";",2)[0];var cookie=new jakarta.servlet.http.Cookie(pair.split("=",2)[0],pair.split("=",2)[1]);
        mvc.perform(post("/api/commands/stock.move").cookie(cookie).header("X-Bar-Request","1").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        UUID foreign=UUID.randomUUID();command("role.save",Map.of("id",foreign,"name","Não existe","permissions",List.of()),UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }
    @Test void stockMovementReplayAdjustmentAndImmutableHistory()throws Exception{
        var f=fixture("10");String key=UUID.randomUUID().toString();var body=Map.<String,Object>of("productId",f.get("product"),"kind","ENTRADA","quantity","2.125","reason","Compra");
        command("stock.move",body,key).andExpect(status().isOk());command("stock.move",body,key).andExpect(status().isOk());assertEquals(0,balance(f.get("product")).compareTo(new BigDecimal("12.125")));
        create("stock.move",Map.of("productId",f.get("product"),"kind","AJUSTE","quantity","0","reason","Contagem zerada"));assertEquals(0,balance(f.get("product")).signum());
        command("product.availability",Map.of("productId",f.get("product"),"available",true),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        command("stock.move",Map.of("productId",f.get("product"),"kind","SAIDA","quantity","1","reason","Saída sem saldo"),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        assertThrows(Exception.class,()->db.update("UPDATE bar.products SET stock_quantity=10 WHERE id=?",f.get("product")));
        assertThrows(Exception.class,()->db.update("UPDATE bar.products SET stock_controlled=false WHERE id=?",f.get("product")));
        assertThrows(Exception.class,()->db.update("DELETE FROM bar.stock_movements WHERE product_id=?",f.get("product")));
    }
    @Test void ordersDebitOnceAndUnpreparedCancellationRestores()throws Exception{
        var f=fixture("4");String key=UUID.randomUUID().toString();command("order.create",order(f,"2"),key).andExpect(status().isOk());command("order.create",order(f,"2"),key).andExpect(status().isOk());assertEquals(0,balance(f.get("product")).compareTo(new BigDecimal("2")));
        UUID item=db.queryForObject("SELECT i.id FROM bar.order_items i JOIN bar.orders o ON o.id=i.order_id WHERE o.account_id=?",UUID.class,f.get("account"));
        String cancel=UUID.randomUUID().toString();var b=Map.<String,Object>of("itemId",item,"reason","Cliente desistiu");command("item.cancel",b,cancel).andExpect(status().isOk());command("item.cancel",b,cancel).andExpect(status().isOk());assertEquals(0,balance(f.get("product")).compareTo(new BigDecimal("4")));
    }
    @Test void preparedLossDoesNotRestoreButPhysicalReturnDoes()throws Exception{
        for(String disposition:List.of("PERDA","RETORNO_FISICO")){
            var f=fixture("3");UUID order=create("order.create",order(f,"1"));UUID item=db.queryForObject("SELECT id FROM bar.order_items WHERE order_id=?",UUID.class,order);
            create("item.status",Map.of("itemId",item,"status","ACEITO"));create("item.status",Map.of("itemId",item,"status","EM_PREPARO"));
            create("item.cancel",Map.of("itemId",item,"reason","Cancelamento testado","disposition",disposition));
            assertEquals(0,balance(f.get("product")).compareTo(new BigDecimal(disposition.equals("PERDA")?"2":"3")));
        }
    }
    @Test void concurrentOrdersCannotOversell()throws Exception{
        var f=fixture("1");UUID otherTable=create("table.save",Map.of("areaId",db.queryForObject("SELECT area_id FROM bar.bar_tables WHERE id=?",UUID.class,f.get("table")),"number",UUID.randomUUID().toString().substring(0,12)));
        UUID otherAccount=create("account.open",Map.of("tableId",otherTable));var g=Map.of("account",otherAccount,"product",f.get("product"));
        try(var pool=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);var first=pool.submit(()->{start.await();return command("order.create",order(f,"1"),UUID.randomUUID().toString()).andReturn().getResponse().getStatus();});
            var second=pool.submit(()->{start.await();return command("order.create",order(g,"1"),UUID.randomUUID().toString()).andReturn().getResponse().getStatus();});start.countDown();
            assertEquals(Set.of(200,400),Set.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS)));
        }
        assertEquals(0,balance(f.get("product")).signum());assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.stock_movements WHERE product_id=? AND kind='VENDA'",Integer.class,f.get("product")));
    }
    @Test void insufficientStockRollsBackWholeOrderAndCrossUnitIsDenied()throws Exception{
        var f=fixture("1");int orders=db.queryForObject("SELECT count(*) FROM bar.orders WHERE account_id=?",Integer.class,f.get("account"));
        command("order.create",order(f,"2"),UUID.randomUUID().toString()).andExpect(status().isBadRequest());assertEquals(orders,db.queryForObject("SELECT count(*) FROM bar.orders WHERE account_id=?",Integer.class,f.get("account")));assertEquals(0,balance(f.get("product")).compareTo(BigDecimal.ONE));
        command("stock.move",Map.of("productId",UUID.randomUUID(),"kind","ENTRADA","quantity","1","reason","Outro bar"),UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }
    @Test void deletingProductHidesItButPreservesSalesStockAndCancellation()throws Exception{
        var f=fixture("5");UUID order=create("order.create",order(f,"2"));
        UUID item=db.queryForObject("SELECT id FROM bar.order_items WHERE order_id=?",UUID.class,order);
        String name=db.queryForObject("SELECT name FROM bar.products WHERE id=?",String.class,f.get("product"));
        String key=UUID.randomUUID().toString();var body=Map.<String,Object>of("productId",f.get("product"));
        command("product.delete",body,key).andExpect(status().isOk());command("product.delete",body,key).andExpect(status().isOk());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.audit_events WHERE action='product.delete' AND resource_id=?",Integer.class,f.get("product")));
        assertNotNull(db.queryForObject("SELECT deleted_at FROM bar.products WHERE id=?",java.sql.Timestamp.class,f.get("product")));
        var state=json.readTree(mvc.perform(get("/api/state")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for(var p:state.path("products"))assertNotEquals(f.get("product").toString(),p.path("id").asText());
        mvc.perform(get("/api/accounts/"+f.get("account"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].description_snapshot").value(name));
        command("order.create",order(f,"1"),UUID.randomUUID().toString()).andExpect(status().isNotFound());
        command("stock.move",Map.of("productId",f.get("product"),"kind","ENTRADA","quantity","1","reason","Produto excluído"),UUID.randomUUID().toString()).andExpect(status().isNotFound());
        command("product.availability",Map.of("productId",f.get("product"),"available",true),UUID.randomUUID().toString()).andExpect(status().isNotFound());
        assertThrows(Exception.class,()->db.update("UPDATE bar.products SET deleted_at=NULL,active=true WHERE id=?",f.get("product")));
        assertEquals(0,balance(f.get("product")).compareTo(new BigDecimal("3")));
        create("item.cancel",Map.of("itemId",item,"reason","Correção após excluir cadastro"));
        assertEquals(0,balance(f.get("product")).compareTo(new BigDecimal("5")));
        assertEquals(3,db.queryForObject("SELECT count(*) FROM bar.stock_movements WHERE product_id=?",Integer.class,f.get("product")));
        create("product.save",Map.of("categoryId",f.get("category"),"name",name,"price","10","destination","SEM_PREPARO"));
    }
    @Test void productDeletionRequiresCatalogPermissionAndOwnUnit()throws Exception{
        var f=fixture("1");UUID other=UUID.randomUUID(),category=UUID.randomUUID(),product=UUID.randomUUID();
        db.update("INSERT INTO bar.establishments(id,name) VALUES(?,'Outro bar')",other);
        db.update("INSERT INTO bar.categories(id,establishment_id,name) VALUES(?,?,'Geral')",category,other);
        db.update("INSERT INTO bar.products(id,establishment_id,category_id,name,price,production_destination) VALUES(?,?,?,'Externo',10,'BAR')",product,other,category);
        command("product.delete",Map.of("productId",product),UUID.randomUUID().toString()).andExpect(status().isNotFound());
        UUID staff=create("staff.save",Map.of("name","Atendimento","role","ATENDIMENTO"));
        String header=mvc.perform(post("/api/operator").header("X-Bar-Request","1").contentType("application/json").content(json.writeValueAsString(Map.of("userId",staff)))).andExpect(status().isOk()).andReturn().getResponse().getHeader("Set-Cookie");
        String[] cookiePair=header.split(";",2)[0].split("=",2);
        mvc.perform(post("/api/commands/product.delete").cookie(new jakarta.servlet.http.Cookie(cookiePair[0],cookiePair[1])).header("X-Bar-Request","1").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(json.writeValueAsString(Map.of("productId",f.get("product"))))).andExpect(status().isForbidden());
        assertNull(db.queryForObject("SELECT deleted_at FROM bar.products WHERE id=?",java.sql.Timestamp.class,f.get("product")));
        assertNull(db.queryForObject("SELECT deleted_at FROM bar.products WHERE id=?",java.sql.Timestamp.class,product));
    }

    Actor actor(UUID id,String...permissions){return new Actor(id,UNIT,null,"Teste",null,null,Set.of(permissions));}
    int direct(Actor actor,String operation,Map<String,Object> body){try{operations.command(actor,operation,UUID.randomUUID().toString(),json.valueToTree(body));return 200;}catch(ApiError e){return e.status;}}
    UUID session()throws Exception{UUID terminal=create("terminal.save",Map.of("name","Caixa "+UUID.randomUUID()));return create("cash.open",Map.of("terminalId",terminal,"openingAmount","50"));}
    JsonNode state()throws Exception{return json.readTree(mvc.perform(get("/api/state")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    JsonNode summary(UUID session)throws Exception{for(var row:state().path("cashSummaries"))if(row.path("cash_session_id").asText().equals(session.toString()))return row;throw new AssertionError("Resumo ausente");}
    @Test void areaDeletionTransfersBusyTablesAndReplays()throws Exception{
        var f=fixture("5");create("order.create",order(f,"2"));UUID source=db.queryForObject("SELECT area_id FROM bar.bar_tables WHERE id=?",UUID.class,f.get("table"));
        UUID destination=create("area.save",Map.of("name","Destino "+UUID.randomUUID()));String key=UUID.randomUUID().toString();var body=Map.<String,Object>of("areaId",source,"destinationAreaId",destination);
        command("area.delete",Map.of("areaId",source),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        command("area.delete",Map.of("areaId",source,"destinationAreaId",source),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        command("area.delete",body,key).andExpect(status().isOk());command("area.delete",body,key).andExpect(status().isOk());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.areas WHERE id=?",Integer.class,source));
        assertEquals(destination,db.queryForObject("SELECT area_id FROM bar.bar_tables WHERE id=?",UUID.class,f.get("table")));
        assertEquals("OCUPADA",db.queryForObject("SELECT status FROM bar.bar_tables WHERE id=?",String.class,f.get("table")));
        assertEquals("ABERTA",db.queryForObject("SELECT status FROM bar.accounts WHERE id=?",String.class,f.get("account")));
        assertEquals(0,new BigDecimal("20").compareTo(db.queryForObject("SELECT total FROM bar.account_totals WHERE account_id=?",BigDecimal.class,f.get("account"))));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.audit_events WHERE action='area.delete' AND resource_id=?",Integer.class,source));
        UUID empty=create("area.save",Map.of("name","Vazio "+UUID.randomUUID()));create("area.delete",Map.of("areaId",empty));
    }
    @Test void areaDeletionRejectsForeignInactiveAndUnauthorizedDestinations()throws Exception{
        var f=fixture("1");UUID source=db.queryForObject("SELECT area_id FROM bar.bar_tables WHERE id=?",UUID.class,f.get("table"));
        UUID inactive=create("area.save",Map.of("name","Inativo "+UUID.randomUUID(),"active",false));
        for(UUID target:List.of(inactive,UUID.randomUUID()))command("area.delete",Map.of("areaId",source,"destinationAreaId",target),UUID.randomUUID().toString()).andExpect(status().isNotFound());
        assertEquals(403,direct(actor(owner,"order.create"),"area.delete",Map.of("areaId",source)));
        assertEquals(source,db.queryForObject("SELECT area_id FROM bar.bar_tables WHERE id=?",UUID.class,f.get("table")));
        UUID other=UUID.randomUUID(),external=UUID.randomUUID();db.update("INSERT INTO bar.establishments(id,name) VALUES(?,'Outro local')",other);db.update("INSERT INTO bar.areas(id,establishment_id,name) VALUES(?,?,'Externo')",external,other);
        command("area.delete",Map.of("areaId",external),UUID.randomUUID().toString()).andExpect(status().isNotFound());
        command("area.delete",Map.of("areaId",source,"destinationAreaId",external),UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }
    @Test void creatingTableAndDeletingAreaCannotLeaveOrphan()throws Exception{
        UUID source=create("area.save",Map.of("name","Origem concorrente "+UUID.randomUUID())),destination=create("area.save",Map.of("name","Destino concorrente "+UUID.randomUUID()));
        UUID table=create("table.save",Map.of("areaId",source,"number",UUID.randomUUID().toString().substring(0,12)));String number=UUID.randomUUID().toString().substring(0,12);
        Actor ownerActor=actor(owner,"table.manage");
        try(var pool=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);var save=pool.submit(()->{start.await();return direct(ownerActor,"table.save",Map.of("areaId",source,"number",number));});
            var delete=pool.submit(()->{start.await();return direct(ownerActor,"area.delete",Map.of("areaId",source,"destinationAreaId",destination));});start.countDown();
            assertEquals(200,delete.get(15,TimeUnit.SECONDS));assertTrue(Set.of(200,404).contains(save.get(15,TimeUnit.SECONDS)));
        }
        assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.bar_tables WHERE area_id=?",Integer.class,source));
        assertEquals(destination,db.queryForObject("SELECT area_id FROM bar.bar_tables WHERE id=?",UUID.class,table));
    }
    @Test void staffDeletionPreservesAuthorAndRevokesSelection()throws Exception{
        var f=fixture("3");UUID staff=create("staff.save",Map.of("name","Autor antigo","role","ATENDIMENTO","allTables",true));
        mvc.perform(post("/api/operator").header("X-Bar-Request","1").contentType("application/json").content(json.writeValueAsString(Map.of("userId",staff)))).andExpect(status().isOk());
        assertEquals(200,direct(actor(staff,"order.create"),"order.create",order(f,"1")));
        String key=UUID.randomUUID().toString();command("staff.delete",Map.of("userId",staff),key).andExpect(status().isOk());command("staff.delete",Map.of("userId",staff),key).andExpect(status().isOk());
        assertNotNull(db.queryForObject("SELECT deleted_at FROM bar.users WHERE id=?",java.sql.Timestamp.class,staff));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.orders WHERE waiter_id=?",Integer.class,staff));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.app_sessions WHERE user_id=? AND revoked_at IS NULL",Integer.class,staff));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.table_authorizations WHERE user_id=? AND revoked_at IS NULL",Integer.class,staff));
        for(var u:state().path("users"))assertNotEquals(staff.toString(),u.path("id").asText());
        command("user.active",Map.of("userId",staff,"active",true),UUID.randomUUID().toString()).andExpect(status().isNotFound());
        command("staff.save",Map.of("id",staff,"name","Restaurar","role","ATENDIMENTO"),UUID.randomUUID().toString()).andExpect(status().isNotFound());
        mvc.perform(post("/api/operator").header("X-Bar-Request","1").contentType("application/json").content(json.writeValueAsString(Map.of("userId",staff)))).andExpect(status().isBadRequest());
        command("staff.delete",Map.of("userId",owner),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        assertThrows(Exception.class,()->db.update("UPDATE bar.users SET active=true,deleted_at=NULL WHERE id=?",staff));
    }
    @Test void concurrentManagersCannotDeleteEachOtherAndLoseBoth()throws Exception{
        UUID first=create("staff.save",Map.of("name","Gestor A","role","ADMINISTRADOR")),second=create("staff.save",Map.of("name","Gestor B","role","ADMINISTRADOR"));
        try(var pool=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);var a=pool.submit(()->{start.await();return direct(actor(first,"user.manage"),"staff.delete",Map.of("userId",second));});
            var b=pool.submit(()->{start.await();return direct(actor(second,"user.manage"),"staff.delete",Map.of("userId",first));});start.countDown();
            assertEquals(Set.of(200,403),Set.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));
        }
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.users WHERE id IN (?,?) AND active",Integer.class,first,second));
    }
    @Test void cashSummaryIncludesCashChangeMovementsRefundsAndOtherMethods()throws Exception{
        var f=fixture("20");create("order.create",order(f,"10"));UUID cash=session();
        create("payment.receive",Map.of("accountId",f.get("account"),"sessionId",cash,"method","DINHEIRO","amount","30","tendered","50"));
        UUID pix=create("payment.receive",Map.of("accountId",f.get("account"),"sessionId",cash,"method","PIX","amount","20"));
        create("cash.move",Map.of("sessionId",cash,"kind","SUPRIMENTO","amount","10","reason","Entrada"));
        create("cash.move",Map.of("sessionId",cash,"kind","SANGRIA","amount","5","reason","Retirada"));
        create("payment.reverse",Map.of("paymentId",pix,"sessionId",cash,"method","PIX","amount","5","reason","Devolução"));
        JsonNode row=summary(cash);assertEquals(0,new BigDecimal("85").compareTo(new BigDecimal(row.path("cash_amount").asText())));assertEquals(0,new BigDecimal("45").compareTo(new BigDecimal(row.path("net_received_amount").asText())));
        create("payment.reverse",Map.of("paymentId",pix,"sessionId",cash,"method","DINHEIRO","amount","5","reason","Devolução em dinheiro"));
        row=summary(cash);assertEquals(0,new BigDecimal("80").compareTo(new BigDecimal(row.path("cash_amount").asText())));assertEquals(0,new BigDecimal("40").compareTo(new BigDecimal(row.path("net_received_amount").asText())));
    }
    @Test void salesListsSeparateUsesOfDeletedTableAndReopenedAccountOnlyOnce()throws Exception{
        var f=fixture("5");UUID cash=session();create("order.create",order(f,"2"));create("payment.receive",Map.of("accountId",f.get("account"),"sessionId",cash,"method","PIX","amount","5"));create("payment.receive",Map.of("accountId",f.get("account"),"sessionId",cash,"method","DINHEIRO","amount","15"));
        UUID second=create("account.open",Map.of("tableId",f.get("table")));create("order.create",Map.of("accountId",second,"items",List.of(Map.of("productId",f.get("product"),"quantity","1"))));create("payment.receive",Map.of("accountId",second,"sessionId",cash,"method","DEBITO","amount","10"));
        String label=db.queryForObject("SELECT table_number_snapshot FROM bar.accounts WHERE id=?",String.class,second);create("table.delete",Map.of("tableId",f.get("table")));
        String today=java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();String url="/api/reports/sales?from="+today+"&to="+today;
        JsonNode sales=json.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var matching=new ArrayList<JsonNode>();for(var row:sales.path("rows"))if(row.path("table_name").asText().equals(label))matching.add(row);
        assertEquals(2,matching.size());assertEquals(second.toString(),matching.getFirst().path("account_id").asText());assertEquals(0,new BigDecimal("10").compareTo(new BigDecimal(matching.getFirst().path("total").asText())));
        create("account.reopen",Map.of("accountId",second,"reason","Conferência"));
        sales=json.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());for(var row:sales.path("rows"))assertNotEquals(second.toString(),row.path("account_id").asText());
        create("account.close",Map.of("accountId",second));
        mvc.perform(get(url+"&page=-1")).andExpect(status().isBadRequest());mvc.perform(get("/api/reports/sales?from=erro&to="+today)).andExpect(status().isBadRequest());
    }
    @Test void salesPaginatesBeyondStateLimitAndUsesBarDayBoundary()throws Exception{
        UUID area=create("area.save",Map.of("name","Histórico "+UUID.randomUUID())),table=create("table.save",Map.of("areaId",area,"number",UUID.randomUUID().toString().substring(0,12)));
        for(int i=0;i<553;i++)db.update("INSERT INTO bar.accounts(establishment_id,table_id,opened_by,status,opened_at,closed_at) VALUES(?,?,?,'FECHADA','2020-01-01 10:00Z'::timestamptz,'2020-01-01 13:00Z'::timestamptz+?*interval '1 minute')",UNIT,table,owner,i);
        db.update("INSERT INTO bar.accounts(establishment_id,table_id,opened_by,status,opened_at,closed_at) VALUES(?,?,?,'FECHADA','2020-01-01 00:00Z','2020-01-02 02:59Z')",UNIT,table,owner);
        db.update("INSERT INTO bar.accounts(establishment_id,table_id,opened_by,status,opened_at,closed_at) VALUES(?,?,?,'FECHADA','2020-01-01 00:00Z','2020-01-02 03:00Z')",UNIT,table,owner);
        String url="/api/reports/sales?from=2020-01-01&to=2020-01-01";
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(50)).andExpect(jsonPath("$.totalElements").value(554));
        mvc.perform(get(url+"&page=11")).andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(4));
    }

    @Test void staffDeletionRejectsForeignUnitAndMissingPermission()throws Exception{
        UUID other=UUID.randomUUID(),external=UUID.randomUUID();db.update("INSERT INTO bar.establishments(id,name) VALUES(?,'Outro empregador')",other);
        db.update("INSERT INTO bar.users(id,establishment_id,name,password_hash) VALUES(?,?,'Externo','hash-inutilizado-para-teste-local')",external,other);
        command("staff.delete",Map.of("userId",external),UUID.randomUUID().toString()).andExpect(status().isNotFound());
        assertEquals(403,direct(actor(owner,"order.create"),"staff.delete",Map.of("userId",external)));
        assertNull(db.queryForObject("SELECT deleted_at FROM bar.users WHERE id=?",java.sql.Timestamp.class,external));
    }
    @Test void salesRequiresReportPermissionAndNeverIncludesOtherUnit()throws Exception{
        UUID other=UUID.randomUUID(),user=UUID.randomUUID();db.update("INSERT INTO bar.establishments(id,name) VALUES(?,'Outro relatório')",other);
        db.update("INSERT INTO bar.users(id,establishment_id,name,password_hash) VALUES(?,?,'Responsável externo','hash-inutilizado-para-teste-local')",user,other);
        db.update("INSERT INTO bar.accounts(establishment_id,opened_by,status,opened_at,closed_at) VALUES(?,?,'FECHADA','2021-01-01 10:00Z','2021-01-01 11:00Z')",other,user);
        mvc.perform(get("/api/reports/sales?from=2021-01-01&to=2021-01-01")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        UUID staff=create("staff.save",Map.of("name","Sem relatório","role","ATENDIMENTO"));
        String header=mvc.perform(post("/api/operator").header("X-Bar-Request","1").contentType("application/json").content(json.writeValueAsString(Map.of("userId",staff)))).andExpect(status().isOk()).andReturn().getResponse().getHeader("Set-Cookie");
        String[] pair=header.split(";",2)[0].split("=",2);var cookie=new jakarta.servlet.http.Cookie(pair[0],pair[1]);
        mvc.perform(get("/api/reports/sales?from=2021-01-01&to=2021-01-01").cookie(cookie)).andExpect(status().isForbidden());
        UUID role=create("role.save",Map.of("name","Somente relatório "+UUID.randomUUID(),"permissions",List.of("report.view")));
        create("staff.save",Map.of("id",staff,"name","Consulta de vendas","roleId",role));
        mvc.perform(get("/api/reports/sales?from=2021-01-01&to=2021-01-01").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/audit").cookie(cookie)).andExpect(status().isForbidden());
    }

    @Test void cashSummaryIncludesPaymentsBeyondTheOperationalListLimit()throws Exception{
        var f=fixture("700");create("order.create",order(f,"60"));UUID cash=session();
        db.update("INSERT INTO bar.payments(establishment_id,account_id,cash_session_id,operator_id,method,tendered_amount,applied_amount,change_amount) SELECT ?,?,?,?,'PIX',1,1,0 FROM generate_series(1,600)",UNIT,f.get("account"),cash,owner);
        JsonNode state=state();assertEquals(500,state.path("payments").size());JsonNode row=summary(cash);
        assertEquals(0,new BigDecimal("600").compareTo(new BigDecimal(row.path("net_received_amount").asText())));
        assertEquals(0,new BigDecimal("50").compareTo(new BigDecimal(row.path("cash_amount").asText())));
    }

}
