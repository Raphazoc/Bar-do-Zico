package br.com.bardozico;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import java.util.*;
import java.math.*;
import java.time.*;
import java.sql.Timestamp;

@Service
class Operations {
    private final JdbcTemplate db; private final Identity identity; private final ObjectMapper json;
    private final Argon2PasswordEncoder encoder;
    private final Management management;
    private static final Set<String> METHODS=Set.of("DINHEIRO","PIX","DEBITO","CREDITO","VOUCHER","OUTRO");
    private static final Map<String,String> PERMISSION=Map.ofEntries(
        Map.entry("area.save","table.manage"),Map.entry("area.delete","table.manage"),Map.entry("table.create","table.create"),Map.entry("table.save","table.manage"),Map.entry("table.delete","table.manage"),Map.entry("table.authorize","table.manage"),
        Map.entry("category.save","catalog.manage"),Map.entry("product.save","catalog.manage"),Map.entry("product.delete","catalog.manage"),
        Map.entry("modifier-group.save","catalog.manage"),Map.entry("modifier.save","catalog.manage"),Map.entry("product.groups","catalog.manage"),
        Map.entry("account.open","account.open"),Map.entry("account.close","account.close"),Map.entry("account.reopen","account.reopen"),
        Map.entry("account.adjust","account.adjust"),Map.entry("order.create","order.create"),Map.entry("item.status","production.update"),
        Map.entry("item.cancel","order.cancel"),Map.entry("terminal.save","cash.open"),Map.entry("cash.open","cash.open"),
        Map.entry("cash.start","cash.open"),Map.entry("cash.move","cash.move"),Map.entry("cash.close","cash.close"),Map.entry("cash.correct","cash.close"),
        Map.entry("payment.receive","payment.receive"),Map.entry("payment.reverse","payment.reverse"),Map.entry("user.create","user.manage"),
        Map.entry("user.active","user.manage"),Map.entry("staff.delete","user.manage"),Map.entry("staff.save","user.manage"),Map.entry("role.save","user.manage"),Map.entry("stock.move","stock.adjust"),Map.entry("product.availability","catalog.manage"));
    Operations(JdbcTemplate db,Identity identity,ObjectMapper json,Argon2PasswordEncoder encoder,Management management) { this.db=db;this.identity=identity;this.json=json;this.encoder=encoder;this.management=management; }

    @Transactional
    public JsonNode command(Actor a,String operation,String key,JsonNode b) {
        String permission=PERMISSION.get(operation);
        if(permission==null) throw new ApiError(404,"Operação inexistente.");
        a.require(permission);
        if(key==null || !key.matches("[A-Za-z0-9_-]{16,150}")) throw ApiError.bad("Envie uma chave de idempotência válida.");
        String digest=Identity.hash(canonical(b).toString());
        db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",a.unit()+":"+a.id()+":"+operation+":"+key);
        var previous=db.queryForList("SELECT request_hash,response_body::text AS response FROM bar.idempotency_records WHERE establishment_id=? AND user_id=? AND operation=? AND key=?",a.unit(),a.id(),operation,key);
        if(!previous.isEmpty()) {
            if(!digest.equals(previous.getFirst().get("request_hash"))) throw new ApiError(409,"Esta chave já foi usada com outros dados.");
            try { return json.readTree((String)previous.getFirst().get("response")); } catch(Exception e) { throw new IllegalStateException(e); }
        }
        db.update("INSERT INTO bar.idempotency_records(establishment_id,user_id,operation,key,request_hash) VALUES(?,?,?,?,?)",a.unit(),a.id(),operation,key,digest);
        boolean staffChange=Set.of("staff.delete","staff.save","user.active","user.create","role.save").contains(operation);
        if(staffChange) {
            db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",a.unit()+":staff-management");
            UUID target=Set.of("staff.delete","user.active").contains(operation)?uuid(b,"userId"):(operation.equals("staff.save")&&b.hasNonNull("id")?uuid(b,"id"):null);
            if(target!=null&&db.queryForObject("SELECT count(*) FROM bar.users WHERE establishment_id=? AND id=? AND system_attendant",Integer.class,a.unit(),target)>0)
                throw ApiError.bad("Este cadastro é interno do atendimento no celular e não pode ser alterado.");
            if(db.queryForObject("SELECT count(*) FROM bar.users u WHERE u.establishment_id=? AND u.id=? AND u.active AND u.deleted_at IS NULL AND EXISTS (SELECT 1 FROM bar.user_roles ur JOIN bar.role_permissions rp ON (rp.establishment_id,rp.role_id)=(ur.establishment_id,ur.role_id) JOIN bar.permissions p ON p.id=rp.permission_id WHERE ur.establishment_id=u.establishment_id AND ur.user_id=u.id AND p.code='user.manage')",Integer.class,a.unit(),a.id())==0)
                throw new ApiError(403,"Este responsável não está mais disponível. Reconecte o programa.");
        }
        if(Set.of("area.save","area.delete","table.save","table.create").contains(operation))
            db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",a.unit()+":floor-management");
        if(Set.of("cash.start","cash.open").contains(operation))
            db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",a.unit()+":cash-opening");
        UUID id=execute(a,operation,b);
        if(staffChange) management.requireAdministrator(a.unit());
        String result="{\"id\":\""+id+"\"}";
        db.update("UPDATE bar.idempotency_records SET status='CONCLUIDO',response_code=200,response_body=?::jsonb WHERE establishment_id=? AND user_id=? AND operation=? AND key=?",result,a.unit(),a.id(),operation,key);
        identity.event(a.id(),operation,operation.split("\\.")[0],id);
        return json.valueToTree(Map.of("id",id));
    }
    private JsonNode canonical(JsonNode node) {
        if(node.isObject()) { ObjectNode out=json.createObjectNode();var keys=new TreeSet<String>();node.fieldNames().forEachRemaining(keys::add);keys.forEach(k->out.set(k,canonical(node.get(k))));return out; }
        if(node.isArray()) { ArrayNode out=json.createArrayNode();node.forEach(n->out.add(canonical(n)));return out; }
        return node;
    }
    private UUID execute(Actor a,String op,JsonNode b) {
        UUID u=a.unit();UUID id=b.hasNonNull("id")?uuid(b,"id"):UUID.randomUUID();
        switch(op) {
            case "stock.move","product.availability","product.delete","staff.save","staff.delete","role.save" -> {return management.execute(a,op,b);}
            case "area.delete" -> {
                id=uuid(b,"areaId");one("SELECT id FROM bar.areas WHERE establishment_id=? AND id=? FOR UPDATE",u,id);
                var tables=db.queryForList("SELECT id FROM bar.bar_tables WHERE establishment_id=? AND area_id=? ORDER BY id FOR UPDATE",u,id);
                if(!tables.isEmpty()) {
                    if(!b.hasNonNull("destinationAreaId")) throw ApiError.bad("Escolha outro local para receber as mesas.");
                    UUID destination=uuid(b,"destinationAreaId");
                    if(id.equals(destination)) throw ApiError.bad("Escolha um local diferente do que será excluído.");
                    one("SELECT id FROM bar.areas WHERE establishment_id=? AND id=? AND active FOR UPDATE",u,destination);
                    db.update("UPDATE bar.bar_tables SET area_id=?,version=version+1 WHERE establishment_id=? AND area_id=?",destination,u,id);
                }
                db.update("DELETE FROM bar.areas WHERE establishment_id=? AND id=?",u,id);
            }
            case "area.save" -> save(b,"areas",id,u,Map.of("name",text(b,"name",80),"active",b.path("active").asBoolean(true)));
            case "table.save","table.create" -> {
                boolean creating=op.equals("table.create");
                if(creating) {
                    var fields=b.fieldNames();
                    while(fields.hasNext()) if(!Set.of("number","areaId").contains(fields.next()))
                        throw ApiError.bad("Para criar uma mesa, informe apenas o número ou nome e o local.");
                }
                String number=text(b,"number",20);
                UUID area;
                if(creating&&!b.hasNonNull("areaId")) {
                    var available=db.queryForList("SELECT id FROM bar.areas WHERE establishment_id=? AND active ORDER BY name,id LIMIT 1",UUID.class,u);
                    if(!available.isEmpty())area=available.getFirst();
                    else {
                        if(db.queryForObject("SELECT count(*) FROM bar.areas WHERE establishment_id=?",Integer.class,u)>0)
                            throw ApiError.bad("Peça ao administrador para ativar ou cadastrar um local no computador.");
                        area=UUID.randomUUID();
                        db.update("INSERT INTO bar.areas(id,establishment_id,name) VALUES(?,?,'Salão')",area,u);
                    }
                } else area=uuid(b,"areaId");
                one("SELECT id FROM bar.areas WHERE establishment_id=? AND id=? AND active FOR SHARE",u,area);
                boolean active=true;
                db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",u+":table-number:"+number);
                if(b.hasNonNull("active") && (!b.path("active").isBoolean() || !b.path("active").asBoolean())) throw ApiError.bad("Para retirar a mesa do salão, use Excluir mesa.");
                if(b.hasNonNull("id")) {
                    var table=one("SELECT * FROM bar.bar_tables WHERE establishment_id=? AND id=? FOR UPDATE",u,id);
                    if(!"LIVRE".equals(table.get("status")) && !"BLOQUEADA".equals(table.get("status"))) throw ApiError.bad("Edite apenas mesas livres ou bloqueadas.");
                    active=b.hasNonNull("active")?b.path("active").asBoolean():Boolean.TRUE.equals(table.get("active"));
                }
                var repeated=db.queryForList("SELECT active FROM bar.bar_tables WHERE establishment_id=? AND number=? AND id<>?",u,number,id);
                if(!repeated.isEmpty()) throw ApiError.bad("Já existe uma mesa com este número. Escolha outro número.");
                var values=new HashMap<String,Object>(Map.of("area_id",area,"number",number,"active",active));
                if(!b.hasNonNull("id")) {
                    values.put("created_by",a.id());
                    values.put("created_by_name_snapshot",a.name());
                }
                save(b,"bar_tables",id,u,values);
            }
            case "table.delete" -> {
                id=uuid(b,"tableId");
                var table=one("SELECT * FROM bar.bar_tables WHERE establishment_id=? AND id=? FOR UPDATE",u,id);
                // Só a linha da mesa é bloqueada; uma operação financeira pode
                // terminar normalmente sem inverter a ordem caixa → conta → mesa.
                boolean busy=!Set.of("LIVRE","BLOQUEADA").contains(table.get("status")) || db.queryForObject("SELECT count(*) FROM bar.accounts WHERE establishment_id=? AND table_id=? AND status IN ('ABERTA','EM_FECHAMENTO')",Integer.class,u,id)>0;
                if(busy) throw ApiError.bad("Esta mesa está em atendimento. Finalize a conta antes de excluir a mesa.");
                db.update("DELETE FROM bar.bar_tables WHERE establishment_id=? AND id=?",u,id);
            }
            case "table.authorize" -> {
                UUID user=uuid(b,"userId"),table=uuid(b,"tableId");
                one("SELECT id FROM bar.users WHERE establishment_id=? AND id=? AND deleted_at IS NULL AND NOT system_attendant FOR SHARE",u,user);
                db.update("UPDATE bar.table_authorizations SET revoked_at=CURRENT_TIMESTAMP WHERE establishment_id=? AND user_id=? AND table_id=? AND revoked_at IS NULL",u,user,table);
                if(b.path("authorized").asBoolean()) db.update("INSERT INTO bar.table_authorizations(id,establishment_id,user_id,table_id,granted_by) VALUES(?,?,?,?,?)",id,u,user,table,a.id());
            }
            case "category.save" -> save(b,"categories",id,u,Map.of("name",text(b,"name",100),"active",b.path("active").asBoolean(true)));
            case "product.save" -> {
                String destination=choice(b,"destination",Set.of("COZINHA","BAR","SEM_PREPARO"));
                save(b,"products",id,u,Map.of("category_id",uuid(b,"categoryId"),"name",text(b,"name",150),"price",amount(b,"price",false),"production_destination",destination,"active",b.path("active").asBoolean(true),"available",b.path("available").asBoolean(true)));
                management.stockSettings(a,id,b,!b.hasNonNull("id"));
            }
            case "modifier-group.save" -> {
                int min=b.path("min").asInt(-1),max=b.path("max").asInt(-1);
                if(min<0 || max<1 || max<min || max>100) throw ApiError.bad("Limites de complementos inválidos.");
                save(b,"modifier_groups",id,u,Map.of("name",text(b,"name",100),"min_choices",min,"max_choices",max));
            }
            case "modifier.save" -> save(b,"modifiers",id,u,Map.of("group_id",uuid(b,"groupId"),"name",text(b,"name",100),"price",amount(b,"price",false),"active",b.path("active").asBoolean(true)));
            case "product.groups" -> {
                id=uuid(b,"productId");one("SELECT id FROM bar.products WHERE establishment_id=? AND id=? FOR UPDATE",u,id);
                db.update("DELETE FROM bar.product_modifier_groups WHERE establishment_id=? AND product_id=?",u,id);
                for(JsonNode group:array(b,"groupIds",100)) db.update("INSERT INTO bar.product_modifier_groups(establishment_id,product_id,group_id) VALUES(?,?,?)",u,id,UUID.fromString(group.asText()));
            }
            case "account.open" -> {
                UUID table=uuid(b,"tableId");tableAccess(a,table);
                var row=one("SELECT t.status,t.active FROM bar.bar_tables t JOIN bar.areas ar ON (ar.establishment_id,ar.id)=(t.establishment_id,t.area_id) WHERE t.establishment_id=? AND t.id=? AND ar.active FOR UPDATE OF t",u,table);
                if(!Boolean.TRUE.equals(row.get("active"))) throw ApiError.bad("Esta mesa foi excluída do salão. Restaure a mesa antes de atender.");
                if(!"LIVRE".equals(row.get("status"))) throw ApiError.bad("Esta mesa não está livre.");
                db.update("INSERT INTO bar.accounts(id,establishment_id,table_id,opened_by) VALUES(?,?,?,?)",id,u,table,a.id());
                db.update("UPDATE bar.bar_tables SET status='OCUPADA',version=version+1 WHERE establishment_id=? AND id=?",u,table);
            }
            case "account.close" -> { id=uuid(b,"accountId");var account=account(a,id,true);requireZero(u,id);closeAccount(u,id,(UUID)account.get("table_id")); }
            case "account.reopen" -> {
                id=uuid(b,"accountId");text(b,"reason",1000);
                var row=one("SELECT * FROM bar.accounts WHERE establishment_id=? AND id=? FOR UPDATE",u,id);
                if(!"FECHADA".equals(row.get("status"))) throw ApiError.bad("Apenas contas fechadas podem ser reabertas.");
                // Desvincular a mesa antiga preserva um atendimento posterior.
                db.update("UPDATE bar.accounts SET status='ABERTA',closed_at=NULL,table_id=NULL,version=version+1 WHERE establishment_id=? AND id=?",u,id);
            }
            case "account.adjust" -> {
                UUID account=uuid(b,"accountId");account(a,account,true);
                String kind=choice(b,"kind",Set.of("DESCONTO","SERVICO","ACRESCIMO"));
                BigDecimal value;BigDecimal rate=null,base=null;
                UUID reverses=b.hasNonNull("reversesId")?uuid(b,"reversesId"):null;
                if(reverses!=null) {
                    var original=one("SELECT * FROM bar.account_adjustments WHERE establishment_id=? AND account_id=? AND id=?",u,account,reverses);
                    kind=(String)original.get("kind");value=(BigDecimal)original.get("amount");rate=(BigDecimal)original.get("rate");base=(BigDecimal)original.get("base_amount");
                } else if(kind.equals("SERVICO")) {
                    rate=amount(b,"rate",false);if(rate.compareTo(new BigDecimal("100"))>0) throw ApiError.bad("Taxa acima de 100%.");
                    base=(BigDecimal)one("SELECT subtotal FROM bar.account_totals WHERE establishment_id=? AND account_id=?",u,account).get("subtotal");
                    value=base.multiply(rate).divide(new BigDecimal("100")).setScale(2,RoundingMode.HALF_UP);
                } else value=amount(b,"amount",true);
                db.update("INSERT INTO bar.account_adjustments(id,establishment_id,account_id,actor_id,authorized_by,kind,amount,rate,base_amount,reason,reverses_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)",id,u,account,a.id(),a.id(),kind,value,rate,base,text(b,"reason",1000),reverses);
            }
            case "order.create" -> {
                UUID account=uuid(b,"accountId");var acc=account(a,account,true);
                if(!"ABERTA".equals(acc.get("status"))) throw ApiError.bad("Conta não aceita pedidos.");
                db.update("INSERT INTO bar.orders(id,establishment_id,account_id,waiter_id,notes) VALUES(?,?,?,?,?)",id,u,account,a.id(),optional(b,"notes",2000));
                JsonNode items=array(b,"items",100);if(items.isEmpty()) throw ApiError.bad("Inclua pelo menos um item.");
                var productIds=new TreeSet<UUID>();for(var item:items)productIds.add(uuid(item,"productId"));
                for(UUID productId:productIds)one("SELECT id FROM bar.products WHERE establishment_id=? AND id=? FOR UPDATE",u,productId);
                for(var item:items) addItem(a,id,item);
                // Não deixar taxa de serviço antiga quando a base de consumo muda.
                recalculateService(a,account);
            }
            case "item.status","item.cancel" -> {
                id=uuid(b,"itemId");
                var item=one("SELECT i.*,o.account_id FROM bar.order_items i JOIN bar.orders o ON (o.establishment_id,o.id)=(i.establishment_id,i.order_id) WHERE i.establishment_id=? AND i.id=?",u,id);
                UUID account=(UUID)item.get("account_id");
                one("SELECT id FROM bar.accounts WHERE establishment_id=? AND id=? FOR UPDATE",u,account);
                item=one("SELECT * FROM bar.order_items WHERE establishment_id=? AND id=? FOR UPDATE",u,id);
                String from=(String)item.get("status"),to=op.equals("item.cancel")?"CANCELADO":choice(b,"status",Set.of("ACEITO","EM_PREPARO","PRONTO","ENTREGUE"));
                String reason=to.equals("CANCELADO")?text(b,"reason",1000):null;
                String disposition=null;
                if(to.equals("CANCELADO")) {
                    account(a,account,true);
                    if(Set.of("EM_PREPARO","PRONTO","ENTREGUE").contains(from)) disposition=choice(b,"disposition",Set.of("PERDA","RETORNO_FISICO"));
                }
                db.update("UPDATE bar.order_items SET status=?,version=version+1 WHERE establishment_id=? AND id=?",to,u,id);
                db.update("INSERT INTO bar.production_events(establishment_id,order_item_id,actor_id,authorized_by,from_status,to_status,reason,stock_disposition) VALUES(?,?,?,?,?,?,?,?)",u,id,a.id(),to.equals("CANCELADO")?a.id():null,from,to,reason,disposition);
                if(to.equals("CANCELADO")) recalculateService(a,account);
            }
            case "terminal.save" -> save(b,"cash_terminals",id,u,Map.of("name",text(b,"name",100),"active",b.path("active").asBoolean(true)));
            case "cash.start" -> {
                if(!b.isObject()||!b.isEmpty())throw ApiError.bad("A inicialização do caixa não recebe valores ou contagens.");
                db.update("UPDATE bar.cash_sessions SET review_required=true,version=version+1 WHERE establishment_id=? AND status='ABERTA' AND NOT review_required",u);
            }
            case "cash.open" -> {
                if(db.queryForObject("SELECT count(*) FROM bar.cash_sessions WHERE establishment_id=? AND status='ABERTA' AND review_required",Integer.class,u)>0)
                    throw new ApiError(409,"Confira e feche o caixa anterior antes de fazer uma nova abertura.");
                UUID terminal=uuid(b,"terminalId");one("SELECT id FROM bar.cash_terminals WHERE establishment_id=? AND id=? AND active FOR UPDATE",u,terminal);
                if(db.queryForObject("SELECT count(*) FROM bar.cash_sessions WHERE establishment_id=? AND terminal_id=? AND status='ABERTA'",Integer.class,u,terminal)>0)
                    throw new ApiError(409,"Este caixa já está aberto. Selecione o caixa em uso para conferir os valores.");
                db.update("INSERT INTO bar.cash_sessions(id,establishment_id,terminal_id,operator_id,opening_amount) VALUES(?,?,?,?,?)",id,u,terminal,a.id(),b.hasNonNull("openingAmount")?amount(b,"openingAmount",false):BigDecimal.ZERO);
            }
            case "cash.move" -> {
                UUID session=uuid(b,"sessionId");cash(a,session);
                db.update("INSERT INTO bar.cash_movements(id,establishment_id,cash_session_id,operator_id,authorized_by,kind,amount,reason) VALUES(?,?,?,?,?,?,?,?)",id,u,session,a.id(),a.id(),choice(b,"kind",Set.of("SUPRIMENTO","SANGRIA")),amount(b,"amount",true),text(b,"reason",1000));
            }
            case "payment.receive" -> {
                UUID session=uuid(b,"sessionId"),account=uuid(b,"accountId");cash(a,session);var acc=account(a,account,true);
                if(b.has("expectedBalance")||b.has("expectedTotal")) {
                    var totals=one("SELECT total,balance FROM bar.account_totals WHERE establishment_id=? AND account_id=?",u,account);
                    if(amount(b,"expectedBalance",false).compareTo((BigDecimal)totals.get("balance"))!=0||amount(b,"expectedTotal",false).compareTo((BigDecimal)totals.get("total"))!=0)
                        throw new ApiError(409,"O consumo ou o valor a receber mudou. Confira novamente a guia antes de registrar o pagamento.");
                }
                BigDecimal applied=amount(b,"amount",true),tendered=b.hasNonNull("tendered")?amount(b,"tendered",true):applied;
                String method=choice(b,"method",METHODS);
                if(tendered.compareTo(applied)<0 || (!method.equals("DINHEIRO") && tendered.compareTo(applied)!=0)) throw ApiError.bad("Valor entregue/troco inválido para a forma escolhida.");
                db.update("INSERT INTO bar.payments(id,establishment_id,account_id,cash_session_id,operator_id,method,tendered_amount,applied_amount,change_amount) VALUES(?,?,?,?,?,?,?,?,?)",id,u,account,session,a.id(),method,tendered,applied,tendered.subtract(applied));
                BigDecimal balance=(BigDecimal)one("SELECT balance FROM bar.account_totals WHERE establishment_id=? AND account_id=?",u,account).get("balance");
                if(balance.signum()==0) { a.require("account.close");closeAccount(u,account,(UUID)acc.get("table_id")); }
            }
            case "payment.reverse" -> {
                UUID session=uuid(b,"sessionId"),payment=uuid(b,"paymentId");cash(a,session);
                var pay=one("SELECT * FROM bar.payments WHERE establishment_id=? AND id=?",u,payment);
                account(a,(UUID)pay.get("account_id"),true);
                one("SELECT id FROM bar.payments WHERE establishment_id=? AND id=? FOR UPDATE",u,payment);
                db.update("INSERT INTO bar.payment_reversals(id,establishment_id,payment_id,cash_session_id,operator_id,authorized_by,amount,refund_method,reason) VALUES(?,?,?,?,?,?,?,?,?)",id,u,payment,session,a.id(),a.id(),amount(b,"amount",true),choice(b,"method",METHODS),text(b,"reason",1000));
            }
            case "cash.close","cash.correct" -> {
                UUID session=uuid(b,"sessionId");
                if(op.equals("cash.close")) cash(a,session,true);else {
                    var cash=one("SELECT * FROM bar.cash_sessions WHERE establishment_id=? AND id=? FOR UPDATE",u,session);
                    if(!"FECHADA".equals(cash.get("status"))) throw ApiError.bad("Caixa ainda não fechado.");
                }
                UUID corrects=op.equals("cash.correct")?uuid(b,"correctsId"):null;
                boolean systemTotals=b.path("useSystemTotals").asBoolean(false);
                if(b.has("useSystemTotals")&&!b.path("useSystemTotals").isBoolean())throw ApiError.bad("Escolha uma forma válida de fechar o caixa.");
                if(systemTotals&&(!op.equals("cash.close")||b.has("counted")))throw ApiError.bad("O fechamento pelo sistema não recebe contagem manual.");
                var totals=db.queryForList("SELECT method,expected_amount FROM bar.cash_session_totals WHERE establishment_id=? AND cash_session_id=?",u,session);
                if(systemTotals&&totals.stream().anyMatch(row->((BigDecimal)row.get("expected_amount")).signum()<0))throw ApiError.bad("Há saldo negativo em uma forma de pagamento. Confira as movimentações antes de fechar o caixa.");
                db.update("INSERT INTO bar.cash_closures(id,establishment_id,cash_session_id,closed_by,corrects_id,reason) VALUES(?,?,?,?,?,?)",id,u,session,a.id(),corrects,systemTotals?"Fechamento com valores calculados pelo sistema.":corrects==null?null:text(b,"reason",1000));
                var counted=b.path("counted");if(!systemTotals&&(!counted.isObject() || counted.size()!=6)) throw ApiError.bad("Informe as seis formas de pagamento.");
                for(var row:totals) {
                    String method=(String)row.get("method");
                    db.update("INSERT INTO bar.cash_closure_lines(establishment_id,closure_id,method,expected_amount,counted_amount) VALUES(?,?,?,?,?)",u,id,method,row.get("expected_amount"),systemTotals?row.get("expected_amount"):amount(counted,method,false));
                }
                if(op.equals("cash.close")) db.update("UPDATE bar.cash_sessions SET status='FECHADA',review_required=false,closed_at=CURRENT_TIMESTAMP,version=version+1 WHERE establishment_id=? AND id=?",u,session);
            }
            case "user.create" -> {
                String name=text(b,"name",150),username=optional(b,"username",40),email=optional(b,"email",254);
                if(username!=null) username=username.toLowerCase(Locale.ROOT);
                if(email!=null) email=email.toLowerCase(Locale.ROOT);
                if(username==null && email==null) throw ApiError.bad("Escolha um nome de usuário para a pessoa entrar, por exemplo: joao.");
                if(username!=null && !username.matches("[a-z0-9][a-z0-9._-]{1,39}")) throw ApiError.bad("Use um nome de usuário de 2 a 40 caracteres, com letras sem acento, números, ponto, traço ou sublinhado, começando por letra ou número.");
                if(email!=null && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw ApiError.bad("Confira o e-mail informado ou deixe esse campo vazio.");
                String role=choice(b,"role",Set.of("ATENDIMENTO","PRODUCAO","CAIXA","ADMINISTRADOR"));
                int min=role.equals("ADMINISTRADOR")?6:4;
                String password=b.path("password").isTextual()?b.path("password").asText():"";
                if(password.isBlank() || password.length()<min || password.length()>128) throw ApiError.bad("Escolha uma senha de pelo menos "+min+" caracteres (até 128).");
                // Serializa cadastros com o mesmo acesso para mostrar uma mensagem
                // clara, inclusive quando dois computadores salvam ao mesmo tempo.
                var loginKeys=new ArrayList<String>();
                if(username!=null) loginKeys.add(u+":username:"+username);
                if(email!=null) loginKeys.add(u+":email:"+email);
                for(String loginKey:loginKeys.stream().sorted().toList()) db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",loginKey);
                if(username!=null && db.queryForObject("SELECT count(*) FROM bar.users WHERE establishment_id=? AND username=?",Integer.class,u,username)>0) throw new ApiError(409,"Este nome de usuário já está em uso. Escolha outro, por exemplo: joao2.");
                if(email!=null && db.queryForObject("SELECT count(*) FROM bar.users WHERE establishment_id=? AND email=?",Integer.class,u,email)>0) throw new ApiError(409,"Este e-mail já está cadastrado. Use outro acesso ou ative o cadastro existente.");
                Set<String> codes=switch(role) {
                    case "ATENDIMENTO" -> Set.of("table.create","account.open","order.create");
                    case "PRODUCAO" -> Set.of("production.update");
                    case "CAIXA" -> Set.of("account.close","payment.receive","cash.open","cash.close","report.view");
                    default -> new HashSet<>(db.queryForList("SELECT code FROM bar.permissions",String.class));
                };
                db.update("INSERT INTO bar.users(id,establishment_id,name,email,username,password_hash) VALUES(?,?,?,?,?,?)",id,u,name,email,username,encoder.encode(password));
                UUID roleId=db.queryForObject("INSERT INTO bar.roles(establishment_id,name) VALUES(?,?) ON CONFLICT(establishment_id,name) DO UPDATE SET name=EXCLUDED.name RETURNING id",UUID.class,u,"Sistema - "+role);
                for(String code:codes) db.update("INSERT INTO bar.role_permissions(establishment_id,role_id,permission_id) SELECT ?,?,id FROM bar.permissions WHERE code=? ON CONFLICT DO NOTHING",u,roleId,code);
                db.update("INSERT INTO bar.user_roles(establishment_id,user_id,role_id) VALUES(?,?,?)",u,id,roleId);
                if(b.hasNonNull("allTables") && !b.path("allTables").isBoolean()) throw ApiError.bad("Confira a opção de liberar as mesas.");
                if(role.equals("ATENDIMENTO") && b.path("allTables").asBoolean(false)) {
                    // Liberação inicial explícita, apenas para mesas já existentes.
                    // Não altera acessos de outros funcionários nem libera futuras mesas.
                    db.update("""
                        INSERT INTO bar.table_authorizations(establishment_id,user_id,table_id,granted_by)
                        SELECT ?,?,t.id,? FROM bar.bar_tables t
                        WHERE t.establishment_id=? AND t.active ORDER BY t.id FOR SHARE OF t
                        """,u,id,a.id(),u);
                }
            }
            case "user.active" -> {
                id=uuid(b,"userId");if(id.equals(a.id())) throw ApiError.bad("Você não pode desativar sua própria conta.");
                int count=db.update("UPDATE bar.users SET active=? WHERE establishment_id=? AND id=? AND deleted_at IS NULL",b.path("active").asBoolean(),u,id);
                if(count==0) throw new ApiError(404,"Usuário não encontrado.");
                db.update("UPDATE bar.app_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE establishment_id=? AND user_id=? AND revoked_at IS NULL",u,id);
            }
            default -> throw new ApiError(404,"Operação não encontrada.");
        }
        // Justificativas ficam no histórico sem copiar senha ou corpo da requisição.
        if(b.hasNonNull("reason")) db.update("INSERT INTO bar.audit_events(establishment_id,actor_id,action,resource_type,resource_id,reason,correlation_id) VALUES(?,?,?,?,?,?,?)",u,a.id(),op+".reason",op.split("\\.")[0],id,text(b,"reason",1000),UUID.randomUUID().toString());
        return id;
    }
    private void addItem(Actor a,UUID order,JsonNode item) {
        UUID u=a.unit(),product=uuid(item,"productId"),id=UUID.randomUUID();
        var p=one("SELECT p.* FROM bar.products p JOIN bar.categories c ON (c.establishment_id,c.id)=(p.establishment_id,p.category_id) WHERE p.establishment_id=? AND p.id=? AND p.active AND p.available AND c.active FOR SHARE OF p,c",u,product);
        BigDecimal qty=quantity(item,"quantity");
        if(Boolean.TRUE.equals(p.get("stock_controlled"))&&((BigDecimal)p.get("stock_quantity")).compareTo(qty)<0) throw ApiError.bad("Estoque insuficiente para "+p.get("name")+". Confira a quantidade disponível.");
        db.update("INSERT INTO bar.order_items(id,establishment_id,order_id,product_id,description_snapshot,unit_price,quantity,destination,notes) VALUES(?,?,?,?,?,?,?,?,?)",id,u,order,product,p.get("name"),p.get("price"),qty,p.get("production_destination"),optional(item,"notes",1000));
        var selected=new HashMap<UUID,BigDecimal>();
        JsonNode mods=item.path("modifiers");
        if(!mods.isMissingNode()) for(var mod:array(item,"modifiers",100)) {
            UUID modifier=uuid(mod,"id");if(selected.put(modifier,quantity(mod,"quantity"))!=null) throw ApiError.bad("Complemento repetido.");
        }
        var groups=db.queryForList("SELECT g.* FROM bar.modifier_groups g JOIN bar.product_modifier_groups pg ON (pg.establishment_id,pg.group_id)=(g.establishment_id,g.id) WHERE pg.establishment_id=? AND pg.product_id=? FOR SHARE OF g,pg",u,product);
        Set<UUID> consumed=new HashSet<>();
        for(var group:groups) {
            int count=0;
            for(var mod:db.queryForList("SELECT * FROM bar.modifiers WHERE establishment_id=? AND group_id=? AND active FOR SHARE",u,group.get("id"))) {
                UUID modifier=(UUID)mod.get("id");if(!selected.containsKey(modifier)) continue;
                BigDecimal quantity=selected.get(modifier);
                if(quantity.stripTrailingZeros().scale()>0) throw ApiError.bad("Quantidade de complemento deve ser inteira.");
                count+=quantity.intValueExact();consumed.add(modifier);
                db.update("INSERT INTO bar.order_item_modifiers(establishment_id,order_item_id,modifier_id,description_snapshot,unit_price,quantity) VALUES(?,?,?,?,?,?)",u,id,modifier,mod.get("name"),mod.get("price"),quantity);
            }
            if(count<((Number)group.get("min_choices")).intValue() || count>((Number)group.get("max_choices")).intValue()) throw ApiError.bad("Seleção fora dos limites do grupo "+group.get("name")+".");
        }
        if(consumed.size()!=selected.size()) throw ApiError.bad("Complemento não pertence ao produto ou está inativo.");
    }
    private void recalculateService(Actor a,UUID account) {
        var fees=db.queryForList("SELECT * FROM bar.account_adjustments j WHERE j.establishment_id=? AND j.account_id=? AND j.kind='SERVICO' AND j.reverses_id IS NULL AND NOT EXISTS(SELECT 1 FROM bar.account_adjustments r WHERE r.establishment_id=j.establishment_id AND r.reverses_id=j.id)",a.unit(),account);
        for(var fee:fees) {
            String reason="Recálculo por alteração de consumo";
            db.update("INSERT INTO bar.account_adjustments(establishment_id,account_id,actor_id,kind,amount,rate,base_amount,reason,reverses_id) VALUES(?,?,?,'SERVICO',?,?,?,?,?)",a.unit(),account,a.id(),fee.get("amount"),fee.get("rate"),fee.get("base_amount"),reason,fee.get("id"));
            BigDecimal base=(BigDecimal)one("SELECT subtotal FROM bar.account_totals WHERE establishment_id=? AND account_id=?",a.unit(),account).get("subtotal");
            BigDecimal amount=base.multiply((BigDecimal)fee.get("rate")).divide(new BigDecimal("100")).setScale(2,RoundingMode.HALF_UP);
            if(amount.signum()>0) db.update("INSERT INTO bar.account_adjustments(establishment_id,account_id,actor_id,kind,amount,rate,base_amount,reason) VALUES(?,?,?,'SERVICO',?,?,?,?)",a.unit(),account,a.id(),amount,fee.get("rate"),base,reason);
        }
    }
    private void save(JsonNode b,String table,UUID id,UUID u,Map<String,Object> values) {
        // Identificadores vêm somente das constantes acima; valores são parâmetros.
        var keys=new TreeSet<>(values.keySet());var args=new ArrayList<Object>();
        keys.forEach(k->args.add(values.get(k)));
        if(b.hasNonNull("id")) {
            args.add(u);args.add(id);
            String set=String.join(",",keys.stream().map(k->k+"=?").toList());
            if(db.update("UPDATE bar."+table+" SET "+set+" WHERE establishment_id=? AND id=?",args.toArray())==0) throw new ApiError(404,"Cadastro não encontrado.");
        } else {
            args.add(id);args.add(u);
            db.update("INSERT INTO bar."+table+" ("+String.join(",",keys)+",id,establishment_id) VALUES("+String.join(",",Collections.nCopies(keys.size()+2,"?"))+")",args.toArray());
        }
    }
    private Map<String,Object> account(Actor a,UUID id,boolean active) {
        var row=one("SELECT * FROM bar.accounts WHERE establishment_id=? AND id=? FOR UPDATE",a.unit(),id);
        if(active && !Set.of("ABERTA","EM_FECHAMENTO").contains(row.get("status"))) throw ApiError.bad("Conta encerrada. Reabra com autorização antes de alterar.");
        // Operadores de caixa e administradores têm acesso global da unidade.
        if(!a.permissions().contains("payment.receive") && !a.permissions().contains("table.manage")) tableAccess(a,(UUID)row.get("table_id"));
        return row;
    }
    private void tableAccess(Actor a,UUID table) {
        if(a.attendanceOnly()) {
            if(table==null||db.queryForObject("SELECT count(*) FROM bar.bar_tables WHERE establishment_id=? AND id=? AND active",Integer.class,a.unit(),table)==0)
                throw new ApiError(403,"Mesa indisponível para atendimento.");
            return;
        }
        if(a.permissions().contains("table.manage")) return;
        if(table==null || db.queryForObject("SELECT count(*) FROM bar.table_authorizations WHERE establishment_id=? AND table_id=? AND user_id=? AND revoked_at IS NULL",Integer.class,a.unit(),table,a.id())==0) throw new ApiError(403,"Você não está autorizado a atender esta mesa.");
    }
    private void cash(Actor a,UUID id) {
        cash(a,id,false);
    }
    private void cash(Actor a,UUID id,boolean closing) {
        var row=one("SELECT * FROM bar.cash_sessions WHERE establishment_id=? AND id=? FOR UPDATE",a.unit(),id);
        if(!"ABERTA".equals(row.get("status"))) throw ApiError.bad("Este caixa está fechado.");
        if(!closing&&Boolean.TRUE.equals(row.get("review_required")))throw new ApiError(409,"Este caixa tem fechamento pendente. Confira os valores e feche antes de abrir o caixa do dia.");
    }
    private void requireZero(UUID u,UUID account) {
        if(((BigDecimal)one("SELECT balance FROM bar.account_totals WHERE establishment_id=? AND account_id=?",u,account).get("balance")).signum()!=0) throw ApiError.bad("A conta precisa estar quitada.");
    }
    private void closeAccount(UUID u,UUID account,UUID table) {
        db.update("UPDATE bar.accounts SET status='FECHADA',closed_at=CURRENT_TIMESTAMP,version=version+1 WHERE establishment_id=? AND id=?",u,account);
        if(table!=null) db.update("UPDATE bar.bar_tables SET status='LIVRE',version=version+1 WHERE establishment_id=? AND id=?",u,table);
    }
    Map<String,Object> one(String sql,Object... args) {
        var rows=db.queryForList(sql,args);if(rows.isEmpty()) throw new ApiError(404,"Registro não encontrado nesta unidade.");return rows.getFirst();
    }
    static UUID uuid(JsonNode b,String key) { try { return UUID.fromString(b.path(key).asText()); } catch(Exception e) { throw ApiError.bad("Identificador inválido: "+key); } }
    static String text(JsonNode b,String key,int max) {
        JsonNode v=b.path(key);String value=v.isTextual()?v.asText().strip():"";
        if(value.isEmpty() || value.length()>max) throw ApiError.bad("Preencha "+fieldLabel(key)+" (até "+max+" caracteres).");return value;
    }
    private static String fieldLabel(String key) {
        return switch(key) {
            case "name" -> "o nome";case "username" -> "o usuário";case "password" -> "a senha";
            case "number" -> "o número ou nome da mesa";case "reason" -> "o motivo";
            case "price" -> "o preço";case "amount" -> "o valor";case "rate" -> "o percentual";
            case "openingAmount" -> "o dinheiro inicial";case "tendered" -> "o dinheiro entregue pelo cliente";
            case "destination" -> "o local de preparo";case "role" -> "a função do funcionário";
            case "kind" -> "o tipo";case "method" -> "a forma de pagamento";default -> "o campo informado";
        };
    }
    static String optional(JsonNode b,String key,int max) { return b.hasNonNull(key)&&!b.path(key).asText().isBlank()?text(b,key,max):null; }
    static String choice(JsonNode b,String key,Set<String> values) { String v=text(b,key,40);if(!values.contains(v)) throw ApiError.bad("Opção inválida: "+key);return v; }
    static JsonNode array(JsonNode b,String key,int max) { JsonNode v=b.path(key);if(!v.isArray() || v.size()>max) throw ApiError.bad("Lista inválida: "+key);return v; }
    static BigDecimal amount(JsonNode b,String key,boolean positive) {
        try { BigDecimal v=new BigDecimal(b.path(key).asText()).setScale(2,RoundingMode.UNNECESSARY);if(v.signum()<(positive?1:0)||v.precision()>14) throw new ArithmeticException();return v; }
        catch(Exception e) { throw ApiError.bad("Confira "+fieldLabel(key)+". Use um valor válido com até duas casas decimais."); }
    }
    static BigDecimal quantity(JsonNode b,String key) {
        try { BigDecimal v=new BigDecimal(b.path(key).asText()).setScale(3,RoundingMode.UNNECESSARY);if(v.signum()<=0 || v.compareTo(new BigDecimal("10000"))>0) throw new ArithmeticException();return v; }
        catch(Exception e) { throw ApiError.bad("Quantidade inválida."); }
    }
}
