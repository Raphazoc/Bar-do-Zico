package br.com.bardozico;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import java.util.*;
import java.math.*;
import static br.com.bardozico.Operations.*;

@Service
class Management {
    private final JdbcTemplate db;private final Argon2PasswordEncoder encoder;
    Management(JdbcTemplate db,Argon2PasswordEncoder encoder){this.db=db;this.encoder=encoder;}
    void requireAdministrator(UUID unit){
        if(db.queryForObject("SELECT count(*) FROM bar.users u WHERE u.establishment_id=? AND u.active AND u.deleted_at IS NULL AND EXISTS (SELECT 1 FROM bar.user_roles ur JOIN bar.role_permissions rp ON (rp.establishment_id,rp.role_id)=(ur.establishment_id,ur.role_id) JOIN bar.permissions p ON p.id=rp.permission_id WHERE ur.establishment_id=u.establishment_id AND ur.user_id=u.id AND p.code='user.manage')",Integer.class,unit)==0)
            throw ApiError.bad("Mantenha pelo menos um administrador ativo no bar.");
    }
    private Map<String,Object> product(UUID unit,UUID id){
        var rows=db.queryForList("SELECT * FROM bar.products WHERE establishment_id=? AND id=? AND deleted_at IS NULL FOR UPDATE",unit,id);
        if(rows.isEmpty())throw new ApiError(404,"Produto não encontrado.");return rows.getFirst();
    }
    private BigDecimal stockNumber(JsonNode body,String field){
        try{var n=new BigDecimal(body.path(field).asText()).setScale(3,RoundingMode.UNNECESSARY);if(n.signum()<0||n.precision()>14)throw new ArithmeticException();return n;}
        catch(Exception e){throw ApiError.bad("Informe uma quantidade válida, com até três casas decimais.");}
    }
    void stockSettings(Actor actor,UUID id,JsonNode body,boolean fresh){
        var p=product(actor.unit(),id);
        boolean controlled=body.has("stockControlled")?body.path("stockControlled").asBoolean():Boolean.TRUE.equals(p.get("stock_controlled"));
        if(!controlled&&Boolean.TRUE.equals(p.get("stock_controlled")))throw ApiError.bad("Preserve o controle de um produto que já tem estoque. Use as movimentações para corrigir o saldo.");
        BigDecimal minimum=body.has("stockMinimum")?stockNumber(body,"stockMinimum"):(BigDecimal)p.get("stock_minimum");
        db.update("UPDATE bar.products SET stock_controlled=?,stock_minimum=? WHERE establishment_id=? AND id=?",controlled,minimum,actor.unit(),id);
        if(body.has("stockQuantity")){
            if(!fresh)throw ApiError.bad("Para alterar a quantidade de um produto existente, use Movimentações.");
            BigDecimal initial=stockNumber(body,"stockQuantity");
            if(initial.signum()>0){if(!controlled)throw ApiError.bad("Ative o controle de estoque para informar a quantidade.");movement(actor,id,"ENTRADA",initial,"Quantidade inicial",UUID.randomUUID());}
        }
    }
    private void movement(Actor actor,UUID product,String kind,BigDecimal delta,String reason,UUID id){
        db.update("INSERT INTO bar.stock_movements(id,establishment_id,product_id,actor_id,kind,quantity,balance_after,reason) VALUES(?,?,?,?,?,?,0,?)",id,actor.unit(),product,actor.id(),kind,delta,reason);
    }
    UUID execute(Actor actor,String operation,JsonNode body){
        UUID unit=actor.unit(),id=body.hasNonNull("id")?uuid(body,"id"):UUID.randomUUID();
        switch(operation){
            case "staff.delete" -> {
                id=uuid(body,"userId");
                if(id.equals(actor.id())) throw ApiError.bad("Você não pode excluir o responsável em uso neste aparelho.");
                if(db.update("UPDATE bar.users SET active=false,deleted_at=CURRENT_TIMESTAMP WHERE establishment_id=? AND id=? AND deleted_at IS NULL",unit,id)==0)
                    throw new ApiError(404,"Funcionário não encontrado.");
                db.update("UPDATE bar.app_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE establishment_id=? AND user_id=? AND revoked_at IS NULL",unit,id);
                db.update("UPDATE bar.device_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE establishment_id=? AND user_id=? AND revoked_at IS NULL",unit,id);
                db.update("UPDATE bar.table_authorizations SET revoked_at=CURRENT_TIMESTAMP WHERE establishment_id=? AND user_id=? AND revoked_at IS NULL",unit,id);
            }
            case "product.delete" -> {
                id=uuid(body,"productId");product(unit,id);
                db.update("UPDATE bar.products SET deleted_at=CURRENT_TIMESTAMP,active=false,available=false,version=version+1 WHERE establishment_id=? AND id=?",unit,id);
            }
            case "stock.move" -> {
                UUID target=uuid(body,"productId");var row=product(unit,target);
                if(!Boolean.TRUE.equals(row.get("stock_controlled")))db.update("UPDATE bar.products SET stock_controlled=true WHERE establishment_id=? AND id=?",unit,target);
                String kind=choice(body,"kind",Set.of("ENTRADA","SAIDA","AJUSTE"));
                BigDecimal quantity=stockNumber(body,"quantity"),current=(BigDecimal)row.get("stock_quantity");
                BigDecimal delta=kind.equals("AJUSTE")?quantity.subtract(current):kind.equals("SAIDA")?quantity.negate():quantity;
                if(delta.signum()==0)throw ApiError.bad("A quantidade informada não altera o estoque.");
                if(current.add(delta).signum()<0)throw ApiError.bad("Estoque insuficiente. Confira a quantidade disponível.");
                movement(actor,target,kind,delta,text(body,"reason",1000),id);
            }
            case "product.availability" -> {
                id=uuid(body,"productId");var p=product(unit,id);
                if(!body.path("available").isBoolean())throw ApiError.bad("Informe a disponibilidade do produto.");
                if(body.path("available").asBoolean()&&Boolean.TRUE.equals(p.get("stock_controlled"))&&((BigDecimal)p.get("stock_quantity")).signum()==0)throw ApiError.bad("Registre uma entrada de estoque antes de disponibilizar este produto.");
                db.update("UPDATE bar.products SET available=?,version=version+1 WHERE establishment_id=? AND id=?",body.path("available").asBoolean(),unit,id);
            }
            case "role.save" -> {
                String name=text(body,"name",80);var codes=new HashSet<String>();
                for(var code:array(body,"permissions",100)){
                    if(!code.isTextual()||db.queryForObject("SELECT count(*) FROM bar.permissions WHERE code=?",Integer.class,code.asText())==0)throw ApiError.bad("Permissão inválida.");codes.add(code.asText());
                }
                // O perfil do responsável pela operação aberta não pode perder a gestão.
                if(body.hasNonNull("id")&&db.queryForObject("SELECT count(*) FROM bar.user_roles WHERE establishment_id=? AND user_id=? AND role_id=?",Integer.class,unit,actor.id(),id)>0&&!codes.contains("user.manage"))throw ApiError.bad("Mantenha a permissão de gerenciar funcionários no seu perfil.");
                if(body.hasNonNull("id")){
                    if(db.update("UPDATE bar.roles SET name=? WHERE establishment_id=? AND id=?",name,unit,id)==0)throw new ApiError(404,"Perfil não encontrado.");
                    db.queryForList("SELECT id FROM bar.roles WHERE establishment_id=? AND id=? FOR UPDATE",unit,id);
                }else db.update("INSERT INTO bar.roles(id,establishment_id,name) VALUES(?,?,?)",id,unit,name);
                db.update("DELETE FROM bar.role_permissions WHERE establishment_id=? AND role_id=?",unit,id);
                for(String code:codes)db.update("INSERT INTO bar.role_permissions(establishment_id,role_id,permission_id) SELECT ?,?,id FROM bar.permissions WHERE code=?",unit,id,code);
            }
            case "staff.save" -> {
                String name=text(body,"name",150);UUID role=body.hasNonNull("roleId")?uuid(body,"roleId"):template(unit,choice(body,"role",Set.of("ATENDIMENTO","PRODUCAO","CAIXA","ADMINISTRADOR")));
                if(db.queryForObject("SELECT count(*) FROM bar.roles WHERE establishment_id=? AND id=?",Integer.class,unit,role)==0)throw ApiError.bad("Perfil não encontrado neste bar.");
                if(id.equals(actor.id())&&db.queryForObject("SELECT count(*) FROM bar.role_permissions rp JOIN bar.permissions p ON p.id=rp.permission_id WHERE rp.establishment_id=? AND rp.role_id=? AND p.code='user.manage'",Integer.class,unit,role)==0)throw ApiError.bad("Seu cadastro precisa manter um perfil de gestão.");
                if(body.hasNonNull("id")){
                    if(db.update("UPDATE bar.users SET name=? WHERE establishment_id=? AND id=? AND deleted_at IS NULL",name,unit,id)==0)throw new ApiError(404,"Funcionário não encontrado.");
                    db.queryForList("SELECT id FROM bar.users WHERE establishment_id=? AND id=? FOR UPDATE",unit,id);
                    db.update("DELETE FROM bar.user_roles WHERE establishment_id=? AND user_id=?",unit,id);
                }else db.update("INSERT INTO bar.users(id,establishment_id,name,password_hash) VALUES(?,?,?,?)",id,unit,name,encoder.encode(UUID.randomUUID().toString()));
                db.update("INSERT INTO bar.user_roles(establishment_id,user_id,role_id) VALUES(?,?,?)",unit,id,role);
                if(body.path("allTables").asBoolean(false)){
                    db.update("INSERT INTO bar.table_authorizations(establishment_id,user_id,table_id,granted_by) SELECT ?,?,t.id,? FROM bar.bar_tables t WHERE t.establishment_id=? AND t.active AND NOT EXISTS(SELECT 1 FROM bar.table_authorizations a WHERE a.establishment_id=t.establishment_id AND a.table_id=t.id AND a.user_id=? AND a.revoked_at IS NULL)",unit,id,actor.id(),unit,id);
                }
            }
            default -> throw new ApiError(404,"Operação não encontrada.");
        }
        return id;
    }
    private UUID template(UUID unit,String role){
        String name=switch(role){case "ATENDIMENTO"->"Atendimento";case "PRODUCAO"->"Cozinha e bar";case "CAIXA"->"Caixa";default->"Administrador";};
        db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",unit+":profile:"+role);
        var existing=db.queryForList("SELECT id FROM bar.roles WHERE establishment_id=? AND name=?",unit,name);
        if(!existing.isEmpty())return (UUID)existing.getFirst().get("id");
        UUID id=UUID.randomUUID();db.update("INSERT INTO bar.roles(id,establishment_id,name) VALUES(?,?,?)",id,unit,name);
        var codes=switch(role){case "ATENDIMENTO"->Set.of("account.open","order.create");case "PRODUCAO"->Set.of("production.update");case "CAIXA"->Set.of("account.close","account.adjust","payment.receive","cash.open","cash.close","cash.move","report.view");default->new HashSet<>(db.queryForList("SELECT code FROM bar.permissions",String.class));};
        for(String code:codes)db.update("INSERT INTO bar.role_permissions(establishment_id,role_id,permission_id) SELECT ?,?,id FROM bar.permissions WHERE code=?",unit,id,code);
        return id;
    }
}
