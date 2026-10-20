package br.com.bardozico;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@EnableScheduling
@RestController
class LocalEvents {
    private final JdbcTemplate db;private final List<Client> clients=new CopyOnWriteArrayList<>();
    record Client(Actor actor,SseEmitter emitter) { }
    LocalEvents(JdbcTemplate db) {this.db=db;}
    @GetMapping(value="/api/events",produces="text/event-stream") SseEmitter subscribe(@AuthenticationPrincipal Actor actor) {
        var emitter=new SseEmitter(60000L);var client=new Client(actor,emitter);clients.add(client);
        emitter.onCompletion(()->clients.remove(client));emitter.onTimeout(()->clients.remove(client));emitter.onError(e->clients.remove(client));return emitter;
    }
    @Scheduled(fixedDelay=2000) void deliver() {
        if(clients.isEmpty()) return;
        // Somente um publicador local. Evento é uma invalidação, sem dados de negócio.
        for(var client:clients) {
            int valid=client.actor.session()==null?db.queryForObject("SELECT count(*) FROM bar.users WHERE establishment_id=? AND id=? AND active",Integer.class,client.actor.unit(),client.actor.id()):db.queryForObject("SELECT count(*) FROM bar.app_sessions s JOIN bar.users u ON (u.establishment_id,u.id)=(s.establishment_id,s.user_id) WHERE s.id=? AND s.establishment_id=? AND s.revoked_at IS NULL AND s.expires_at>CURRENT_TIMESTAMP AND u.active",Integer.class,client.actor.session(),client.actor.unit());
            if(valid==0) {clients.remove(client);client.emitter.complete();}
        }
        Set<UUID> units=new HashSet<>();clients.forEach(c->units.add(c.actor.unit()));
        for(UUID unit:units) for(var event:db.queryForList("SELECT id FROM bar.outbox_events WHERE establishment_id=? AND published_at IS NULL AND (next_attempt_at IS NULL OR next_attempt_at<=CURRENT_TIMESTAMP) ORDER BY occurred_at LIMIT 100",unit)) {
            boolean delivered=false;
            for(var client:clients) if(client.actor.unit().equals(unit)) {
                try {client.emitter.send(SseEmitter.event().id(event.get("id").toString()).name("change").data(Map.of("id",event.get("id"))));delivered=true;}
                catch(Exception e) {clients.remove(client);client.emitter.complete();}
            }
            if(delivered) db.update("UPDATE bar.outbox_events SET published_at=CURRENT_TIMESTAMP,attempts=attempts+1 WHERE establishment_id=? AND id=?",unit,event.get("id"));
            else db.update("UPDATE bar.outbox_events SET attempts=attempts+1,next_attempt_at=CURRENT_TIMESTAMP+interval '5 seconds' WHERE establishment_id=? AND id=?",unit,event.get("id"));
        }
    }
}
