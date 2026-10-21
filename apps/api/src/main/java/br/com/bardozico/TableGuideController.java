package br.com.bardozico;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;

@RestController
class TableGuideController {
    private final JdbcTemplate db;private final boolean training;
    TableGuideController(JdbcTemplate db,@Value("${bar.training:false}") boolean training){this.db=db;this.training=training;}
    @GetMapping(value="/api/accounts/{id}/guide.pdf",produces=MediaType.APPLICATION_PDF_VALUE)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    ResponseEntity<byte[]> guide(@AuthenticationPrincipal Actor actor,@PathVariable UUID id)throws IOException {
        if(actor.attendanceOnly()||Collections.disjoint(actor.permissions(),Set.of("payment.receive","report.view")))throw new ApiError(403,"A impressão da guia é feita no computador administrador.");
        var accounts=db.queryForList("""
            SELECT a.*,tot.subtotal,tot.total,tot.paid_amount,tot.balance,
                COALESCE(a.table_number_snapshot,t.number,'Sem mesa identificada') table_name,e.name bar_name,e.timezone
            FROM bar.accounts a JOIN bar.account_totals tot ON (tot.establishment_id,tot.account_id)=(a.establishment_id,a.id)
            JOIN bar.establishments e ON e.id=a.establishment_id
            LEFT JOIN bar.bar_tables t ON (t.establishment_id,t.id)=(a.establishment_id,a.table_id)
            WHERE a.establishment_id=? AND a.id=?
            """,actor.unit(),id);
        if(accounts.isEmpty())throw new ApiError(404,"Atendimento não encontrado.");
        var a=accounts.getFirst();var zone=ZoneId.of((String)a.get("timezone"));
        var items=db.queryForList("""
            SELECT i.id,i.description_snapshot,i.quantity,i.unit_price,tot.line_total,
                COALESCE((SELECT sum(m.unit_price*m.quantity) FROM bar.order_item_modifiers m WHERE (m.establishment_id,m.order_item_id)=(i.establishment_id,i.id)),0) unit_modifiers_total
            FROM bar.order_items i JOIN bar.orders o ON (o.establishment_id,o.id)=(i.establishment_id,i.order_id)
            JOIN bar.order_item_totals tot ON (tot.establishment_id,tot.order_item_id)=(i.establishment_id,i.id)
            WHERE o.establishment_id=? AND o.account_id=? AND i.status<>'CANCELADO' ORDER BY o.sent_at,i.id
            """,actor.unit(),id);
        var modifiers=db.queryForList("""
            SELECT m.order_item_id,m.description_snapshot,m.quantity,m.unit_price FROM bar.order_item_modifiers m
            JOIN bar.order_items i ON (i.establishment_id,i.id)=(m.establishment_id,m.order_item_id)
            JOIN bar.orders o ON (o.establishment_id,o.id)=(i.establishment_id,i.order_id)
            WHERE o.establishment_id=? AND o.account_id=? ORDER BY m.id
            """,actor.unit(),id);
        var entries=new ArrayList<TableGuide.Item>();
        for(var item:items) {
            var extras=modifiers.stream().filter(m->m.get("order_item_id").equals(item.get("id"))).map(m->"+ "+m.get("description_snapshot")+" ("+TableGuide.quantity(number(m,"quantity"))+" por un.; "+TableGuide.money(number(m,"unit_price"))+")").toList();
            entries.add(new TableGuide.Item((String)item.get("description_snapshot"),number(item,"quantity"),number(item,"unit_price").add(number(item,"unit_modifiers_total")),number(item,"line_total"),extras));
        }
        var adjustments=db.queryForList("SELECT kind,sum(amount*CASE WHEN reverses_id IS NULL THEN 1 ELSE -1 END) amount FROM bar.account_adjustments WHERE establishment_id=? AND account_id=? GROUP BY kind ORDER BY kind",actor.unit(),id).stream()
            .filter(j->number(j,"amount").signum()!=0).map(j->new TableGuide.Adjustment(switch((String)j.get("kind")){case "DESCONTO"->"Desconto";case "SERVICO"->"Taxa de serviço";default->"Acréscimo";},number(j,"amount").multiply("DESCONTO".equals(j.get("kind"))?BigDecimal.valueOf(-1):BigDecimal.ONE))).toList();
        String table=(String)a.get("table_name");if(!table.toLowerCase(Locale.ROOT).matches("^mesa(?:\\s.*)?$"))table="Mesa "+table;
        var data=new TableGuide.Data((String)a.get("bar_name"),table,time(a.get("opened_at"),zone),time(a.get("closed_at"),zone),time(Instant.now(),zone),"FECHADA".equals(a.get("status"))?"Conta encerrada":"Conta aberta",training,entries,adjustments,number(a,"subtotal"),number(a,"total"),number(a,"paid_amount"),number(a,"balance"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Content-Disposition","inline; filename=\"guia-mesa-"+id+".pdf\"").header("X-Content-Type-Options","nosniff").body(TableGuide.pdf(data));
    }
    private static BigDecimal number(Map<String,Object> row,String key){return (BigDecimal)row.get(key);}
    private static String time(Object value,ZoneId zone) {
        if(value==null)return "";
        Instant instant=switch(value){case Timestamp t->t.toInstant();case OffsetDateTime t->t.toInstant();case Instant t->t;default->throw new IllegalArgumentException("Data inválida.");};
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone).format(instant);
    }
}
