package br.com.bardozico;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import java.util.*;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"bar.open-access=true","bar.device-access=true","server.forward-headers-strategy=none"})
@AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CashStartupTest {
    static final UUID UNIT=UUID.randomUUID();UUID owner;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("bar.unit-id",()->UNIT.toString());}
    @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
    @BeforeAll void seed(){
        assertTrue(db.queryForObject("SELECT current_database()",String.class).startsWith("bar_app_test_"));
        owner=UUID.randomUUID();UUID role=UUID.randomUUID();
        db.update("INSERT INTO bar.establishments(id,name) VALUES(?,'Caixa diário em teste')",UNIT);
        db.update("INSERT INTO bar.users(id,establishment_id,name,password_hash) VALUES(?,?,'Administrador','hash-inutilizado-para-operacao-local')",owner,UNIT);
        db.update("INSERT INTO bar.roles(id,establishment_id,name) VALUES(?,?,'Administrador')",role,UNIT);
        db.update("INSERT INTO bar.user_roles(establishment_id,user_id,role_id) VALUES(?,?,?)",UNIT,owner,role);
        db.update("INSERT INTO bar.role_permissions(establishment_id,role_id,permission_id) SELECT ?,?,id FROM bar.permissions",UNIT,role);
    }
    ResultActions cmd(String op,Map<String,Object> body,String key)throws Exception{
        return mvc.perform(post("/api/commands/"+op).with(r->{r.setRemoteAddr("127.0.0.1");return r;}).header("X-Bar-Request","1").header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsString(body)));
    }
    UUID create(String op,Map<String,Object> body)throws Exception{return UUID.fromString(json.readTree(cmd(op,body,UUID.randomUUID().toString()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText());}
    Map<String,Object> count(UUID session){var values=new HashMap<String,Object>();for(var row:db.queryForList("SELECT method,expected_amount FROM bar.cash_session_totals WHERE establishment_id=? AND cash_session_id=?",UNIT,session))values.put((String)row.get("method"),row.get("expected_amount").toString());return values;}
    @BeforeEach void finishPreviousTestShifts()throws Exception{
        for(UUID id:db.queryForList("SELECT id FROM bar.cash_sessions WHERE establishment_id=? AND status='ABERTA'",UUID.class,UNIT))create("cash.close",Map.of("sessionId",id,"counted",count(id)));
    }
    UUID terminal()throws Exception{return create("terminal.save",Map.of("name","Caixa "+UUID.randomUUID()));}
    @Test void systemClosingUsesAllPaymentsRefundsAndMovementsAndIsIdempotent()throws Exception{
        UUID area=create("area.save",Map.of("name","Automático "+UUID.randomUUID())),table=create("table.save",Map.of("areaId",area,"number",UUID.randomUUID().toString().substring(0,10))),category=create("category.save",Map.of("name","Automático "+UUID.randomUUID()));
        UUID product=create("product.save",Map.of("categoryId",category,"name","Água","price","10","destination","SEM_PREPARO","stockControlled",false)),account=create("account.open",Map.of("tableId",table));
        create("order.create",Map.of("accountId",account,"items",List.of(Map.of("productId",product,"quantity","1000"))));
        UUID cash=create("cash.open",Map.of("terminalId",terminal(),"openingAmount","30"));
        String[] methods={"DINHEIRO","PIX","DEBITO","CREDITO","VOUCHER","OUTRO"},values={"30","50","20","40","10","5"},refunds={"5","10","2","3","1","1"};
        UUID pix=null;
        for(int i=0;i<methods.length;i++){
            var payment=new HashMap<String,Object>(Map.of("accountId",account,"sessionId",cash,"method",methods[i],"amount",values[i]));if(i==0)payment.put("tendered","50");
            UUID id=create("payment.receive",payment);if(i==1)pix=id;
            create("payment.reverse",Map.of("paymentId",id,"sessionId",cash,"method",methods[i],"amount",refunds[i],"reason","Devolução de teste"));
        }
        db.update("INSERT INTO bar.payments(establishment_id,account_id,cash_session_id,operator_id,method,tendered_amount,applied_amount,change_amount) SELECT ?,?,?,?,'PIX',1,1,0 FROM generate_series(1,510)",UNIT,account,cash,owner);
        create("cash.move",Map.of("sessionId",cash,"kind","SUPRIMENTO","amount","7","reason","Entrada"));create("cash.move",Map.of("sessionId",cash,"kind","SANGRIA","amount","2","reason","Retirada"));
        assertTrue(db.queryForObject("SELECT count(*) FROM bar.payments WHERE cash_session_id=?",Integer.class,cash)>500);
        create("cash.start",Map.of());
        cmd("cash.close",Map.of("sessionId",cash,"useSystemTotals",true,"counted",count(cash)),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        cmd("cash.close",Map.of("sessionId",cash,"useSystemTotals","true"),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        String key=UUID.randomUUID().toString();var body=Map.<String,Object>of("sessionId",cash,"useSystemTotals",true);
        String first=cmd("cash.close",body,key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertEquals(first,cmd("cash.close",body,key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        UUID closure=UUID.fromString(json.readTree(first).path("id").asText());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.cash_closures WHERE cash_session_id=?",Integer.class,cash));
        var expected=Map.of("DINHEIRO","60","PIX","550","DEBITO","18","CREDITO","37","VOUCHER","9","OUTRO","4");
        var lines=db.queryForList("SELECT method,expected_amount,counted_amount,difference_amount FROM bar.cash_closure_lines WHERE closure_id=?",closure);assertEquals(6,lines.size());
        for(var line:lines){assertEquals(0,new BigDecimal(expected.get((String)line.get("method"))).compareTo((BigDecimal)line.get("counted_amount")));assertEquals(line.get("expected_amount"),line.get("counted_amount"));assertEquals(0,((BigDecimal)line.get("difference_amount")).signum());}
        assertTrue(db.queryForObject("SELECT reason FROM bar.cash_closures WHERE id=?",String.class,closure).contains("sistema"));
        assertFalse(db.queryForObject("SELECT review_required FROM bar.cash_sessions WHERE id=?",Boolean.class,cash));
        cmd("cash.correct",Map.of("sessionId",cash,"correctsId",closure,"useSystemTotals",true,"reason","Não sobrescrever"),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        UUID next=create("cash.open",Map.of("terminalId",terminal()));
        create("payment.reverse",Map.of("paymentId",pix,"sessionId",next,"method","PIX","amount","1","reason","Devolução em outro turno"));
        cmd("cash.close",Map.of("sessionId",next,"useSystemTotals",true),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        assertEquals("ABERTA",db.queryForObject("SELECT status FROM bar.cash_sessions WHERE id=?",String.class,next));
        create("payment.receive",Map.of("accountId",account,"sessionId",next,"method","PIX","amount","1"));create("cash.close",Map.of("sessionId",next,"useSystemTotals",true));
    }
    @Test void systemClosingAndMovementCannotLoseValues()throws Exception{
        UUID cash=create("cash.open",Map.of("terminalId",terminal(),"openingAmount","10"));int movement;
        try(var pool=Executors.newFixedThreadPool(2)){
            var gate=new CountDownLatch(1);
            var close=pool.submit(()->{gate.await();return cmd("cash.close",Map.of("sessionId",cash,"useSystemTotals",true),UUID.randomUUID().toString()).andReturn().getResponse().getStatus();});
            var move=pool.submit(()->{gate.await();return cmd("cash.move",Map.of("sessionId",cash,"kind","SUPRIMENTO","amount","7","reason","Concorrente"),UUID.randomUUID().toString()).andReturn().getResponse().getStatus();});
            gate.countDown();assertEquals(200,close.get(20,TimeUnit.SECONDS));movement=move.get(20,TimeUnit.SECONDS);assertTrue(Set.of(200,400).contains(movement));
        }
        BigDecimal value=db.queryForObject("SELECT l.counted_amount FROM bar.cash_closure_lines l JOIN bar.cash_closures c ON c.id=l.closure_id WHERE c.cash_session_id=? AND l.method='DINHEIRO'",BigDecimal.class,cash);
        assertEquals(0,new BigDecimal(movement==200?"17":"10").compareTo(value));
    }
    @Test void startingNeverCreatesOrOpensACashShift()throws Exception{
        long before=db.queryForObject("SELECT count(*) FROM bar.cash_sessions WHERE establishment_id=?",Long.class,UNIT);
        create("cash.start",Map.of());assertEquals(before,db.queryForObject("SELECT count(*) FROM bar.cash_sessions WHERE establishment_id=?",Long.class,UNIT));
        cmd("cash.start",Map.of("counted",Map.of()),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
    }
    @Test void previousCashMustBeCountedAndClosedBeforeAnyNewOpening()throws Exception{
        UUID terminal=terminal(),cash=create("cash.open",Map.of("terminalId",terminal,"openingAmount","17"));
        create("cash.move",Map.of("sessionId",cash,"kind","SUPRIMENTO","amount","3","reason","Troco"));
        String key=UUID.randomUUID().toString();cmd("cash.start",Map.of(),key).andExpect(status().isOk());
        var row=db.queryForMap("SELECT status,review_required,opening_amount FROM bar.cash_sessions WHERE id=?",cash);
        assertEquals("ABERTA",row.get("status"));assertEquals(true,row.get("review_required"));assertEquals(0,new BigDecimal("17").compareTo((BigDecimal)row.get("opening_amount")));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.cash_closures WHERE cash_session_id=?",Integer.class,cash));
        cmd("cash.open",Map.of("terminalId",terminal()),UUID.randomUUID().toString()).andExpect(status().isConflict());
        cmd("cash.move",Map.of("sessionId",cash,"kind","SUPRIMENTO","amount","1","reason","Não permitido"),UUID.randomUUID().toString()).andExpect(status().isConflict());
        cmd("cash.close",Map.of("sessionId",cash,"counted",Map.of("DINHEIRO","20")),UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        assertEquals("ABERTA",db.queryForObject("SELECT status FROM bar.cash_sessions WHERE id=?",String.class,cash));
        create("cash.close",Map.of("sessionId",cash,"counted",count(cash)));
        assertFalse(db.queryForObject("SELECT review_required FROM bar.cash_sessions WHERE id=?",Boolean.class,cash));
        UUID today=create("cash.open",Map.of("terminalId",terminal));assertEquals(0,db.queryForObject("SELECT opening_amount FROM bar.cash_sessions WHERE id=?",BigDecimal.class,today).signum());
        cmd("cash.start",Map.of(),key).andExpect(status().isOk());assertFalse(db.queryForObject("SELECT review_required FROM bar.cash_sessions WHERE id=?",Boolean.class,today));
        create("cash.start",Map.of());assertTrue(db.queryForObject("SELECT review_required FROM bar.cash_sessions WHERE id=?",Boolean.class,today));
    }
    @Test void startupAndOpeningAreSerialized()throws Exception{
        UUID old=create("cash.open",Map.of("terminalId",terminal())),other=terminal();
        try(var pool=Executors.newFixedThreadPool(2)){
            var gate=new CountDownLatch(1);
            var start=pool.submit(()->{gate.await();return cmd("cash.start",Map.of(),UUID.randomUUID().toString()).andReturn().getResponse().getStatus();});
            var opening=pool.submit(()->{gate.await();return cmd("cash.open",Map.of("terminalId",other),UUID.randomUUID().toString()).andReturn().getResponse().getStatus();});
            gate.countDown();assertEquals(200,start.get(20,TimeUnit.SECONDS));assertTrue(Set.of(200,409).contains(opening.get(20,TimeUnit.SECONDS)));
        }
        assertTrue(db.queryForObject("SELECT review_required FROM bar.cash_sessions WHERE id=?",Boolean.class,old));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.cash_sessions WHERE establishment_id=? AND status='ABERTA' AND NOT review_required",Integer.class,UNIT));
    }
    @Test void aPaymentCannotUseAmountsFromAnOutdatedGuide()throws Exception{
        UUID area=create("area.save",Map.of("name","Salão "+UUID.randomUUID())),table=create("table.save",Map.of("areaId",area,"number",UUID.randomUUID().toString().substring(0,10))),category=create("category.save",Map.of("name","Bebidas "+UUID.randomUUID()));
        UUID product=create("product.save",Map.of("categoryId",category,"name","Água","price","10","destination","SEM_PREPARO","stockControlled",false)),account=create("account.open",Map.of("tableId",table));
        create("order.create",Map.of("accountId",account,"items",List.of(Map.of("productId",product,"quantity","2"))));
        UUID cash=create("cash.open",Map.of("terminalId",terminal()));
        var body=new HashMap<String,Object>(Map.of("accountId",account,"sessionId",cash,"method","PIX","amount","10","expectedTotal","10","expectedBalance","10"));
        cmd("payment.receive",body,UUID.randomUUID().toString()).andExpect(status().isConflict());assertEquals(0,db.queryForObject("SELECT count(*) FROM bar.payments WHERE account_id=?",Integer.class,account));
        body.put("expectedTotal","20");body.put("expectedBalance","20");create("cash.start",Map.of());
        cmd("payment.receive",body,UUID.randomUUID().toString()).andExpect(status().isConflict());
        create("cash.close",Map.of("sessionId",cash,"counted",count(cash)));cash=create("cash.open",Map.of("terminalId",terminal()));body.put("sessionId",cash);
        create("payment.receive",body);body.put("amount","10");cmd("payment.receive",body,UUID.randomUUID().toString()).andExpect(status().isConflict());
        body.put("expectedBalance","10");create("payment.receive",body);assertEquals("FECHADA",db.queryForObject("SELECT status FROM bar.accounts WHERE id=?",String.class,account));
    }
}
