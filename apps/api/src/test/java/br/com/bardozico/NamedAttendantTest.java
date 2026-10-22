package br.com.bardozico;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"bar.open-access=true","bar.device-access=true","server.forward-headers-strategy=none"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NamedAttendantTest {
    private static final UUID UNIT=UUID.randomUUID();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("bar.unit-id",()->UNIT.toString());}
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    private final UUID owner=UUID.randomUUID();
    @BeforeAll void seed(){
        assertTrue(db.queryForObject("SELECT current_database()",String.class).startsWith("bar_app_test_"));
        db.update("INSERT INTO bar.establishments(id,name) VALUES(?,'Identificadores de atendimento')",UNIT);
        db.update("INSERT INTO bar.users(id,establishment_id,name,password_hash) VALUES(?,?,'Dono do bar',?)",owner,UNIT,"!sem-login-no-aparelho!"+UUID.randomUUID());
        UUID role=UUID.randomUUID();db.update("INSERT INTO bar.roles(id,establishment_id,name) VALUES(?,?,'Administrador')",role,UNIT);
        db.update("INSERT INTO bar.user_roles(establishment_id,user_id,role_id) VALUES(?,?,?)",UNIT,owner,role);
        db.update("INSERT INTO bar.role_permissions(establishment_id,role_id,permission_id) SELECT ?,?,id FROM bar.permissions",UNIT,role);
    }
    private MockHttpServletRequestBuilder phone(MockHttpServletRequestBuilder request){return request.with(r->{r.setRemoteAddr("192.168.0.40");return r;});}
    private Cookie name(String name)throws Exception{
        var response=mvc.perform(phone(post("/api/attendant")).header("X-Bar-Request","1").contentType("application/json").content(json.writeValueAsString(Map.of("name",name))))
            .andExpect(status().isOk()).andReturn().getResponse();
        String header=response.getHeader("Set-Cookie");assertNotNull(header);assertTrue(header.contains("HttpOnly"));assertTrue(header.contains("SameSite=Strict"));
        return new Cookie("BAR_ATTENDANT",header.split(";",2)[0].substring("BAR_ATTENDANT=".length()));
    }
    private JsonNode me(Cookie cookie)throws Exception{return json.readTree(mvc.perform(phone(get("/api/me")).cookie(cookie)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    private UUID create(String operation,Map<String,Object> body,Cookie cookie)throws Exception{
        var request=post("/api/commands/"+operation).header("X-Bar-Request","1").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(json.writeValueAsString(body));
        if(cookie!=null)request=phone(request).cookie(cookie);else request=request.with(r->{r.setRemoteAddr("127.0.0.1");return r;});
        return UUID.fromString(json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText());
    }
    @Test void eachPhoneKeepsItsNameAndChangingAttendantsPreservesPreviousOrders()throws Exception{
        Cookie ana=name("Ana "+UUID.randomUUID()),bruno=name("Bruno "+UUID.randomUUID());JsonNode a=me(ana),b=me(bruno);
        assertTrue(a.path("attendantIdentified").asBoolean());assertNotEquals(a.path("id"),b.path("id"));
        assertEquals(a,me(ana));assertEquals(b,me(bruno));
        UUID area=create("area.save",Map.of("name","Salão "+UUID.randomUUID()),null);
        UUID table=create("table.create",Map.of("number",UUID.randomUUID().toString().substring(0,10),"areaId",area),ana);
        assertEquals(a.path("id").asText(),db.queryForObject("SELECT created_by::text FROM bar.bar_tables WHERE id=?",String.class,table));
        assertEquals(a.path("name").asText(),db.queryForObject("SELECT created_by_name_snapshot FROM bar.bar_tables WHERE id=?",String.class,table));
        UUID category=create("category.save",Map.of("name","Bebidas "+UUID.randomUUID()),null);
        UUID product=create("product.save",Map.of("name","Água","categoryId",category,"price","5","destination","SEM_PREPARO"),null);
        UUID account=create("account.open",Map.of("tableId",table),ana);
        for(Cookie current:List.of(ana,bruno))create("order.create",Map.of("accountId",account,"items",List.of(Map.of("productId",product,"quantity","1"))),current);
        assertEquals(UUID.fromString(a.path("id").asText()),db.queryForObject("SELECT opened_by FROM bar.accounts WHERE id=?",UUID.class,account));
        var actors=db.queryForList("SELECT waiter_id FROM bar.orders WHERE account_id=? ORDER BY sent_at",UUID.class,account);
        assertEquals(List.of(UUID.fromString(a.path("id").asText()),UUID.fromString(b.path("id").asText())),actors);
        JsonNode details=json.readTree(mvc.perform(get("/api/accounts/"+account).with(r->{r.setRemoteAddr("127.0.0.1");return r;})).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var names=new HashSet<String>();details.path("items").forEach(item->names.add(item.path("attendant_name").asText()));
        assertEquals(Set.of(a.path("name").asText(),b.path("name").asText()),names);
        assertEquals(a.path("name").asText(),details.path("account").path("opened_by_name").asText());
        JsonNode phoneDetails=json.readTree(mvc.perform(phone(get("/api/accounts/"+account)).cookie(bruno)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var phoneNames=new HashSet<String>();phoneDetails.path("items").forEach(item->phoneNames.add(item.path("attendant_name").asText()));
        assertEquals(names,phoneNames);assertEquals(a.path("name").asText(),phoneDetails.path("account").path("opened_by_name").asText());
        assertFalse(phoneDetails.has("payments"));assertFalse(phoneDetails.has("adjustments"));
    }
    @Test void tableCreatorSurvivesEditingAndTransferAndCannotBeForged()throws Exception{
        Cookie ana=name("Criadora "+UUID.randomUUID());JsonNode user=me(ana);
        UUID source=create("area.save",Map.of("name","Origem "+UUID.randomUUID()),null),destination=create("area.save",Map.of("name","Destino "+UUID.randomUUID()),null);
        UUID table=create("table.create",Map.of("number",UUID.randomUUID().toString().substring(0,10),"areaId",source),ana);
        create("table.save",Map.of("id",table,"number","Editada-"+UUID.randomUUID().toString().substring(0,8),"areaId",source,"created_by",owner,"created_by_name_snapshot","Falso criador"),null);
        create("account.open",Map.of("tableId",table),ana);
        create("area.delete",Map.of("areaId",source,"destinationAreaId",destination),null);
        var row=db.queryForMap("SELECT created_by,created_by_name_snapshot,area_id,status FROM bar.bar_tables WHERE id=?",table);
        assertEquals(UUID.fromString(user.path("id").asText()),row.get("created_by"));assertEquals(user.path("name").asText(),row.get("created_by_name_snapshot"));
        assertEquals(destination,row.get("area_id"));assertEquals("OCUPADA",row.get("status"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("UPDATE bar.bar_tables SET created_by=?,created_by_name_snapshot='Outro' WHERE id=?",owner,table));
        mvc.perform(phone(post("/api/commands/table.create")).cookie(ana).header("X-Bar-Request","1").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(json.writeValueAsString(Map.of("number","Forjada","areaId",destination,"created_by",owner))))
            .andExpect(status().isBadRequest());
        UUID pcTable=create("table.save",Map.of("number",UUID.randomUUID().toString().substring(0,10),"areaId",destination),null);
        assertEquals(owner,db.queryForObject("SELECT created_by FROM bar.bar_tables WHERE id=?",UUID.class,pcTable));
    }
    @Test void identifyingANameNeverGrantsAdministrationAndLocalPcIgnoresAttendantCookie()throws Exception{
        Cookie cookie=name("Admin escrito no nome "+UUID.randomUUID());JsonNode user=me(cookie);
        var permissions=new HashSet<String>();user.path("permissions").forEach(p->permissions.add(p.asText()));
        assertEquals(Set.of("table.create","account.open","order.create"),permissions);
        mvc.perform(phone(post("/api/commands/cash.open")).cookie(cookie).header("X-Bar-Request","1").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/attendant").with(r->{r.setRemoteAddr("127.0.0.1");return r;}).header("X-Bar-Request","1").contentType("application/json").content("{\"name\":\"Teste\"}"))
            .andExpect(status().isForbidden());
        JsonNode pc=json.readTree(mvc.perform(get("/api/me").cookie(cookie).with(r->{r.setRemoteAddr("127.0.0.1");return r;})).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(owner.toString(),pc.path("id").asText());assertEquals("ADMINISTRADOR",pc.path("accessMode").asText());
        assertFalse(me(new Cookie("BAR_ATTENDANT","0".repeat(64))).path("attendantIdentified").asBoolean());
    }
    @Test void invalidNamesAreRejectedWithoutCreatingUsers()throws Exception{
        int before=db.queryForObject("SELECT count(*) FROM bar.users WHERE establishment_id=? AND named_attendant",Integer.class,UNIT);
        for(String invalid:List.of("","   ","x".repeat(81),"Ana\nMaria","Ana\u0000"))
            mvc.perform(phone(post("/api/attendant")).header("X-Bar-Request","1").contentType("application/json").content(json.writeValueAsString(Map.of("name",invalid)))).andExpect(status().isBadRequest());
        assertEquals(before,db.queryForObject("SELECT count(*) FROM bar.users WHERE establishment_id=? AND named_attendant",Integer.class,UNIT));
    }
    @Test void concurrentPhonesCanUseTheSameDeclaredNameWithoutDuplicatingTheIdentifier()throws Exception{
        String name="Nome compartilhado "+UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->name(name));var second=pool.submit(()->name(name));
            assertEquals(me(first.get()).path("id"),me(second.get()).path("id"));
        }
        assertEquals(1,db.queryForObject("SELECT count(*) FROM bar.users WHERE establishment_id=? AND named_attendant AND name=?",Integer.class,UNIT,name));
    }
}
