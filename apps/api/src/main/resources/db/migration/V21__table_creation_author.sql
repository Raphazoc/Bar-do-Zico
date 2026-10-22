SET search_path TO bar, pg_catalog;

ALTER TABLE bar_tables ADD COLUMN created_by uuid;
ALTER TABLE bar_tables ADD COLUMN created_by_name_snapshot text;
ALTER TABLE bar_tables ADD CONSTRAINT fk_table_creation_author
 FOREIGN KEY (establishment_id,created_by) REFERENCES users(establishment_id,id);
ALTER TABLE bar_tables ADD CONSTRAINT ck_table_creation_author_pair
 CHECK ((created_by IS NULL) = (created_by_name_snapshot IS NULL));

-- table.create sempre representa criacao. table.save antigo tambem podia ser
-- edicao; nesses casos nao inventar um autor com base no primeiro editor.
WITH creators AS (
 SELECT DISTINCT ON (t.establishment_id,t.id)
  t.establishment_id,t.id table_id,e.actor_id,u.name
 FROM bar_tables t
 JOIN audit_events e ON e.establishment_id=t.establishment_id
  AND e.resource_id=t.id AND e.resource_type='table' AND e.action='table.create'
 JOIN users u ON (u.establishment_id,u.id)=(e.establishment_id,e.actor_id)
 ORDER BY t.establishment_id,t.id,e.occurred_at,e.id
)
UPDATE bar_tables t SET created_by=c.actor_id,created_by_name_snapshot=c.name
 FROM creators c WHERE (t.establishment_id,t.id)=(c.establishment_id,c.table_id);

-- Concluir verificacoes diferidas de mesas antes de instalar o novo gatilho.
SET CONSTRAINTS ALL IMMEDIATE;
CREATE FUNCTION guard_table_creation_author() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
BEGIN
 IF ROW(NEW.created_by,NEW.created_by_name_snapshot)
    IS DISTINCT FROM ROW(OLD.created_by,OLD.created_by_name_snapshot) THEN
  RAISE EXCEPTION 'O criador original da mesa nao pode ser alterado'
   USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER guard_table_creation_author BEFORE UPDATE ON bar_tables
 FOR EACH ROW EXECUTE FUNCTION guard_table_creation_author();
