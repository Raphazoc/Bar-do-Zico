package br.com.bardozico;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api")
class BarController {
    private final Operations ops;private final JdbcTemplate db;
    BarController(Operations ops,JdbcTemplate db) {this.ops=ops;this.db=db;}
    @PostMapping("/commands/{operation}") JsonNode command(@AuthenticationPrincipal Actor a,@PathVariable String operation,
            @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody JsonNode body) {
        return ops.command(a,operation,key,body);
    }
    @GetMapping("/state") @Transactional(readOnly=true)
    Map<String,Object> state(@AuthenticationPrincipal Actor a) {
        if(a.attendanceOnly())return attendanceState(a);
        UUID u=a.unit();var out=new LinkedHashMap<String,Object>();
        out.put("areas",db.queryForList("SELECT * FROM bar.areas WHERE establishment_id=? ORDER BY name",u));
        out.put("tables",db.queryForList("SELECT * FROM bar.bar_tables WHERE establishment_id=? ORDER BY length(number),number",u));
        out.put("categories",db.queryForList("SELECT * FROM bar.categories WHERE establishment_id=? ORDER BY sort_order,name",u));
        out.put("products",db.queryForList("SELECT p.*, (p.active AND p.available AND c.active AND (NOT p.stock_controlled OR p.stock_quantity>0)) sellable FROM bar.products p JOIN bar.categories c ON (c.establishment_id,c.id)=(p.establishment_id,p.category_id) WHERE p.establishment_id=? AND p.deleted_at IS NULL ORDER BY p.name",u));
        out.put("groups",db.queryForList("SELECT * FROM bar.modifier_groups WHERE establishment_id=? ORDER BY name",u));
        out.put("modifiers",db.queryForList("SELECT * FROM bar.modifiers WHERE establishment_id=? ORDER BY name",u));
        out.put("productGroups",db.queryForList("SELECT * FROM bar.product_modifier_groups WHERE establishment_id=?",u));
        boolean global=any(a,"table.manage","payment.receive","account.close"),production=any(a,"production.update");
        String access=global?"true":"EXISTS(SELECT 1 FROM bar.table_authorizations ta WHERE ta.establishment_id=a.establishment_id AND ta.table_id=a.table_id AND ta.user_id=? AND ta.revoked_at IS NULL)";
        Object[] args=global?new Object[]{u}:new Object[]{u,a.id()};
        out.put("accounts",db.queryForList("SELECT a.*,t.subtotal,t.adjustments_total,t.total,t.paid_amount,t.balance FROM bar.accounts a JOIN bar.account_totals t ON (t.establishment_id,t.account_id)=(a.establishment_id,a.id) WHERE a.establishment_id=? AND "+access+" ORDER BY a.opened_at DESC LIMIT 500",args));
        String itemAccess=production?"true":access;
        Object[] itemArgs=production||global?new Object[]{u}:new Object[]{u,a.id()};
        out.put("items",db.queryForList("""
            SELECT i.id,i.order_id,i.product_id,i.description_snapshot,i.quantity,i.destination,i.status,i.notes,
                   o.account_id,o.number,o.sent_at,COALESCE(t.number,a.table_number_snapshot) table_number
            FROM bar.order_items i JOIN bar.orders o ON (o.establishment_id,o.id)=(i.establishment_id,i.order_id)
            JOIN bar.accounts a ON (a.establishment_id,a.id)=(o.establishment_id,o.account_id)
            LEFT JOIN bar.bar_tables t ON (t.establishment_id,t.id)=(a.establishment_id,a.table_id)
            WHERE i.establishment_id=? AND
            """+itemAccess+" ORDER BY o.sent_at DESC LIMIT 1000",itemArgs));
        out.put("itemModifiers",db.queryForList("""
            SELECT m.order_item_id,m.description_snapshot,m.quantity
            FROM bar.order_item_modifiers m
            JOIN bar.order_items i ON (i.establishment_id,i.id)=(m.establishment_id,m.order_item_id)
            JOIN bar.orders o ON (o.establishment_id,o.id)=(i.establishment_id,i.order_id)
            JOIN bar.accounts a ON (a.establishment_id,a.id)=(o.establishment_id,o.account_id)
            WHERE m.establishment_id=? AND
            """+itemAccess+" ORDER BY o.sent_at DESC LIMIT 3000",itemArgs));
        out.put("authorizations",db.queryForList("SELECT user_id,table_id FROM bar.table_authorizations WHERE establishment_id=? AND revoked_at IS NULL"+(any(a,"table.manage")?"":" AND user_id=?"),any(a,"table.manage")?new Object[]{u}:new Object[]{u,a.id()}));
        if(any(a,"payment.receive","cash.open","cash.close","cash.move","report.view")) {
            out.put("terminals",db.queryForList("SELECT * FROM bar.cash_terminals WHERE establishment_id=? ORDER BY name",u));
            out.put("sessions",db.queryForList("SELECT * FROM bar.cash_sessions WHERE establishment_id=? ORDER BY opened_at DESC LIMIT 100",u));
            out.put("cashTotals",db.queryForList("SELECT * FROM bar.cash_session_totals WHERE establishment_id=?",u));
            out.put("cashSummaries",db.queryForList("""
                SELECT s.id cash_session_id,t.expected_amount cash_amount,
                  (COALESCE((SELECT sum(p.applied_amount) FROM bar.payments p WHERE p.establishment_id=s.establishment_id AND p.cash_session_id=s.id),0)
                   -COALESCE((SELECT sum(r.amount) FROM bar.payment_reversals r WHERE r.establishment_id=s.establishment_id AND r.cash_session_id=s.id),0)) net_received_amount
                FROM (SELECT * FROM bar.cash_sessions WHERE establishment_id=? ORDER BY opened_at DESC LIMIT 100) s
                JOIN bar.cash_session_totals t ON (t.establishment_id,t.cash_session_id)=(s.establishment_id,s.id) AND t.method='DINHEIRO'
                """,u));
            out.put("payments",db.queryForList("SELECT p.*,COALESCE((SELECT sum(r.amount) FROM bar.payment_reversals r WHERE r.establishment_id=p.establishment_id AND r.payment_id=p.id),0) reversed FROM bar.payments p WHERE p.establishment_id=? ORDER BY p.received_at DESC LIMIT 500",u));
            out.put("closures",db.queryForList("SELECT * FROM bar.cash_closures WHERE establishment_id=? ORDER BY closed_at DESC LIMIT 100",u));
            out.put("closureLines",db.queryForList("SELECT l.* FROM bar.cash_closure_lines l JOIN bar.cash_closures c ON (c.establishment_id,c.id)=(l.establishment_id,l.closure_id) WHERE l.establishment_id=? ORDER BY c.closed_at DESC LIMIT 600",u));
        }
        if(any(a,"stock.adjust")) out.put("stockMovements",db.queryForList("SELECT m.*,p.name product_name,u.name actor_name FROM bar.stock_movements m JOIN bar.products p ON (p.establishment_id,p.id)=(m.establishment_id,m.product_id) JOIN bar.users u ON (u.establishment_id,u.id)=(m.establishment_id,m.actor_id) WHERE m.establishment_id=? ORDER BY m.created_at DESC,m.id LIMIT 1000",u));
        if(any(a,"user.manage","table.manage")) out.put("users",db.queryForList("SELECT u.id,u.name,u.email,u.username,u.active,(SELECT r.id FROM bar.user_roles ur JOIN bar.roles r ON (r.establishment_id,r.id)=(ur.establishment_id,ur.role_id) WHERE ur.establishment_id=u.establishment_id AND ur.user_id=u.id ORDER BY r.name LIMIT 1) role_id,(SELECT string_agg(r.name,', ' ORDER BY r.name) FROM bar.user_roles ur JOIN bar.roles r ON (r.establishment_id,r.id)=(ur.establishment_id,ur.role_id) WHERE ur.establishment_id=u.establishment_id AND ur.user_id=u.id) role_name FROM bar.users u WHERE u.establishment_id=? AND u.deleted_at IS NULL AND NOT u.system_attendant ORDER BY u.name",u));
        if(any(a,"user.manage")){
            out.put("roles",db.queryForList("SELECT * FROM bar.roles WHERE establishment_id=? ORDER BY name",u));
            out.put("permissions",db.queryForList("SELECT code id,description name FROM bar.permissions ORDER BY description"));
            out.put("rolePermissions",db.queryForList("SELECT rp.role_id,p.code permission FROM bar.role_permissions rp JOIN bar.permissions p ON p.id=rp.permission_id WHERE rp.establishment_id=?",u));
        }
        return out;
    }
    @GetMapping("/accounts/{id}") Map<String,Object> account(@AuthenticationPrincipal Actor a,@PathVariable UUID id) {
        if(a.attendanceOnly())return attendanceAccount(a,id);
        UUID u=a.unit();
        var account=ops.one("SELECT a.*,t.* FROM bar.accounts a JOIN bar.account_totals t ON (t.establishment_id,t.account_id)=(a.establishment_id,a.id) WHERE a.establishment_id=? AND a.id=?",u,id);
        if(!any(a,"table.manage","payment.receive","account.close") && db.queryForObject("SELECT count(*) FROM bar.table_authorizations WHERE establishment_id=? AND table_id=? AND user_id=? AND revoked_at IS NULL",Integer.class,u,account.get("table_id"),a.id())==0) throw new ApiError(403,"Mesa não autorizada.");
        var out=new LinkedHashMap<String,Object>();out.put("account",account);
        out.put("items",db.queryForList("SELECT i.*,o.number,t.line_total FROM bar.order_items i JOIN bar.orders o ON (o.establishment_id,o.id)=(i.establishment_id,i.order_id) JOIN bar.order_item_totals t ON (t.establishment_id,t.order_item_id)=(i.establishment_id,i.id) WHERE o.establishment_id=? AND o.account_id=? ORDER BY o.sent_at,i.description_snapshot",u,id));
        out.put("modifiers",db.queryForList("SELECT m.* FROM bar.order_item_modifiers m JOIN bar.order_items i ON (i.establishment_id,i.id)=(m.establishment_id,m.order_item_id) JOIN bar.orders o ON (o.establishment_id,o.id)=(i.establishment_id,i.order_id) WHERE o.establishment_id=? AND o.account_id=?",u,id));
        out.put("adjustments",db.queryForList("SELECT * FROM bar.account_adjustments WHERE establishment_id=? AND account_id=? ORDER BY created_at",u,id));
        out.put("payments",db.queryForList("SELECT * FROM bar.payments WHERE establishment_id=? AND account_id=? ORDER BY received_at",u,id));
        return out;
    }
    private Map<String,Object> attendanceState(Actor a) {
        UUID u=a.unit();var out=new LinkedHashMap<String,Object>();
        out.put("areas",db.queryForList("SELECT id,name,active FROM bar.areas WHERE establishment_id=? ORDER BY name",u));
        var tables=db.queryForList("SELECT id,area_id,number,status,active FROM bar.bar_tables WHERE establishment_id=? AND active ORDER BY length(number),number",u);
        out.put("tables",tables);
        out.put("authorizations",tables.stream().map(table->Map.of("user_id",a.id(),"table_id",table.get("id"))).toList());
        out.put("categories",db.queryForList("SELECT id,name,active FROM bar.categories WHERE establishment_id=? ORDER BY sort_order,name",u));
        out.put("products",db.queryForList("""
            SELECT p.id,p.category_id,p.name,p.price,p.active,p.available,
                   (p.active AND p.available AND c.active AND (NOT p.stock_controlled OR p.stock_quantity>0)) sellable
            FROM bar.products p JOIN bar.categories c ON (c.establishment_id,c.id)=(p.establishment_id,p.category_id)
            WHERE p.establishment_id=? AND p.deleted_at IS NULL AND p.active ORDER BY p.name
            """,u));
        out.put("groups",db.queryForList("SELECT id,name,min_choices,max_choices FROM bar.modifier_groups WHERE establishment_id=? ORDER BY name",u));
        out.put("modifiers",db.queryForList("SELECT id,group_id,name,price,active FROM bar.modifiers WHERE establishment_id=? ORDER BY name",u));
        out.put("productGroups",db.queryForList("SELECT product_id,group_id FROM bar.product_modifier_groups WHERE establishment_id=?",u));
        out.put("accounts",db.queryForList("""
            SELECT a.id,a.table_id,a.status,a.opened_at,t.total FROM bar.accounts a
            JOIN bar.account_totals t ON (t.establishment_id,t.account_id)=(a.establishment_id,a.id)
            JOIN bar.bar_tables bt ON (bt.establishment_id,bt.id)=(a.establishment_id,a.table_id)
            WHERE a.establishment_id=? AND bt.active AND a.status IN ('ABERTA','EM_FECHAMENTO') ORDER BY a.opened_at DESC
            """,u));
        return out;
    }
    private Map<String,Object> attendanceAccount(Actor a,UUID id) {
        UUID u=a.unit();
        var rows=db.queryForList("""
            SELECT a.id,a.table_id,a.status,a.opened_at,t.total FROM bar.accounts a
            JOIN bar.account_totals t ON (t.establishment_id,t.account_id)=(a.establishment_id,a.id)
            JOIN bar.bar_tables bt ON (bt.establishment_id,bt.id)=(a.establishment_id,a.table_id)
            WHERE a.establishment_id=? AND a.id=? AND bt.active AND a.status IN ('ABERTA','EM_FECHAMENTO')
            """,u,id);
        if(rows.isEmpty())throw new ApiError(403,"Este atendimento não está disponível no celular.");
        var out=new LinkedHashMap<String,Object>();out.put("account",rows.getFirst());
        out.put("items",db.queryForList("""
            SELECT i.id,i.order_id,i.product_id,i.description_snapshot,i.quantity,i.notes,i.status,o.number,t.line_total
            FROM bar.order_items i JOIN bar.orders o ON (o.establishment_id,o.id)=(i.establishment_id,i.order_id)
            JOIN bar.order_item_totals t ON (t.establishment_id,t.order_item_id)=(i.establishment_id,i.id)
            WHERE o.establishment_id=? AND o.account_id=? ORDER BY o.sent_at,i.description_snapshot
            """,u,id));
        out.put("modifiers",db.queryForList("""
            SELECT m.order_item_id,m.description_snapshot,m.quantity FROM bar.order_item_modifiers m
            JOIN bar.order_items i ON (i.establishment_id,i.id)=(m.establishment_id,m.order_item_id)
            JOIN bar.orders o ON (o.establishment_id,o.id)=(i.establishment_id,i.order_id)
            WHERE o.establishment_id=? AND o.account_id=?
            """,u,id));
        return out;
    }
    @GetMapping("/reports") Map<String,Object> reports(@AuthenticationPrincipal Actor a,@RequestParam String from,@RequestParam String to) {
        a.require("report.view");LocalDate start=LocalDate.parse(from),end=LocalDate.parse(to);
        if(end.isBefore(start)||end.isAfter(start.plusYears(1))) throw ApiError.bad("Escolha um período de até um ano.");
        String zone=db.queryForObject("SELECT timezone FROM bar.establishments WHERE id=?",String.class,a.unit());
        var since=start.atStartOfDay(java.time.ZoneId.of(zone)).toOffsetDateTime();var until=end.plusDays(1).atStartOfDay(java.time.ZoneId.of(zone)).toOffsetDateTime();
        var out=new LinkedHashMap<String,Object>();
        out.put("closedSales",db.queryForMap("SELECT count(*) accounts,COALESCE(sum(t.total),0) total FROM bar.accounts a JOIN bar.account_totals t ON (t.establishment_id,t.account_id)=(a.establishment_id,a.id) WHERE a.establishment_id=? AND a.status='FECHADA' AND a.closed_at>=? AND a.closed_at<?",a.unit(),since,until));
        out.put("payments",db.queryForList("SELECT method,count(*) count,sum(applied_amount) amount FROM bar.payments WHERE establishment_id=? AND received_at>=? AND received_at<? GROUP BY method ORDER BY method",a.unit(),since,until));
        out.put("refunds",db.queryForList("SELECT refund_method method,count(*) count,sum(amount) amount FROM bar.payment_reversals WHERE establishment_id=? AND reversed_at>=? AND reversed_at<? GROUP BY refund_method ORDER BY refund_method",a.unit(),since,until));
        out.put("closures",db.queryForList("SELECT c.id,c.closed_at,l.method,l.expected_amount,l.counted_amount,l.difference_amount FROM bar.cash_closures c JOIN bar.cash_closure_lines l ON (l.establishment_id,l.closure_id)=(c.establishment_id,c.id) WHERE c.establishment_id=? AND c.closed_at>=? AND c.closed_at<? ORDER BY c.closed_at DESC,l.method",a.unit(),since,until));
        return out;
    }
    @GetMapping("/reports/sales") @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    Map<String,Object> sales(@AuthenticationPrincipal Actor a,@RequestParam String from,@RequestParam String to,@RequestParam(defaultValue="0") int page) {
        a.require("report.view");
        LocalDate start,end;
        try {start=LocalDate.parse(from);end=LocalDate.parse(to);}catch(java.time.format.DateTimeParseException e){throw ApiError.bad("Informe datas válidas.");}
        if(end.isBefore(start)||end.isAfter(start.plusYears(1))||page<0||page>1000000)throw ApiError.bad("Escolha um período de até um ano e uma página válida.");
        String zone=db.queryForObject("SELECT timezone FROM bar.establishments WHERE id=?",String.class,a.unit());
        var since=start.atStartOfDay(java.time.ZoneId.of(zone)).toOffsetDateTime();var until=end.plusDays(1).atStartOfDay(java.time.ZoneId.of(zone)).toOffsetDateTime();
        Long count=db.queryForObject("SELECT count(*) FROM bar.accounts WHERE establishment_id=? AND status='FECHADA' AND closed_at>=? AND closed_at<?",Long.class,a.unit(),since,until);
        var rows=db.queryForList("""
            SELECT a.id account_id,COALESCE(a.table_number_snapshot,t.number,'Sem mesa identificada') table_name,a.closed_at,tot.total
            FROM bar.accounts a JOIN bar.account_totals tot ON (tot.establishment_id,tot.account_id)=(a.establishment_id,a.id)
            LEFT JOIN bar.bar_tables t ON (t.establishment_id,t.id)=(a.establishment_id,a.table_id)
            WHERE a.establishment_id=? AND a.status='FECHADA' AND a.closed_at>=? AND a.closed_at<?
            ORDER BY a.closed_at DESC,a.id DESC LIMIT 50 OFFSET ?
            """,a.unit(),since,until,(long)page*50);
        return Map.of("rows",rows,"totalElements",count,"page",page,"pageSize",50,"timezone",zone);
    }
    @GetMapping("/audit") List<Map<String,Object>> audit(@AuthenticationPrincipal Actor a) {
        a.require("audit.view");return db.queryForList("SELECT action,resource_type,resource_id,reason,occurred_at FROM bar.audit_events WHERE establishment_id=? ORDER BY occurred_at DESC LIMIT 200",a.unit());
    }
    private static boolean any(Actor a,String... codes) { return Arrays.stream(codes).anyMatch(a.permissions()::contains); }
}
