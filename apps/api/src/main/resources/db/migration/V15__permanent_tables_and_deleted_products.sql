SET search_path TO bar, pg_catalog;

-- O cadastro da mesa pode desaparecer; o nome usado no atendimento permanece.
ALTER TABLE accounts ADD COLUMN table_number_snapshot varchar(20);
ALTER TABLE table_authorizations ADD COLUMN table_number_snapshot varchar(20);

CREATE FUNCTION remember_table_number() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
BEGIN
    IF NEW.table_id IS NOT NULL THEN
        SELECT number INTO NEW.table_number_snapshot FROM bar_tables
         WHERE establishment_id=NEW.establishment_id AND id=NEW.table_id;
    ELSIF TG_OP='UPDATE' THEN
        NEW.table_number_snapshot := OLD.table_number_snapshot;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER remember_table_number BEFORE INSERT OR UPDATE OF table_id ON accounts
 FOR EACH ROW EXECUTE FUNCTION remember_table_number();
CREATE TRIGGER remember_table_number BEFORE INSERT OR UPDATE OF table_id ON table_authorizations
 FOR EACH ROW EXECUTE FUNCTION remember_table_number();

-- SET NULL somente no identificador: a unidade e os registros históricos ficam.
ALTER TABLE table_authorizations ALTER COLUMN table_id DROP NOT NULL;
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT conrelid::regclass AS relation,conname FROM pg_constraint
      WHERE contype='f' AND confrelid='bar.bar_tables'::regclass LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',r.relation,r.conname);
    END LOOP;
END $$;
ALTER TABLE accounts ADD CONSTRAINT fk_account_table
 FOREIGN KEY(establishment_id,table_id) REFERENCES bar_tables(establishment_id,id)
 ON DELETE SET NULL (table_id);
ALTER TABLE table_authorizations ADD CONSTRAINT fk_authorization_table
 FOREIGN KEY(establishment_id,table_id) REFERENCES bar_tables(establishment_id,id)
 ON DELETE SET NULL (table_id);

-- Preencher após o DDL: UPDATE em accounts agenda verificações diferidas,
-- que impediriam ALTER TABLE posterior na mesma transação.
UPDATE accounts a SET table_number_snapshot=t.number FROM bar_tables t
 WHERE (t.establishment_id,t.id)=(a.establishment_id,a.table_id);
UPDATE table_authorizations a SET table_number_snapshot=t.number FROM bar_tables t
 WHERE (t.establishment_id,t.id)=(a.establishment_id,a.table_id);


CREATE FUNCTION guard_table_deletion() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
BEGIN
    IF current_setting('transaction_isolation') <> 'read committed' THEN
        RAISE EXCEPTION 'Excluir mesa exige READ COMMITTED' USING ERRCODE='0A000';
    END IF;
    IF OLD.status NOT IN ('LIVRE','BLOQUEADA') OR EXISTS (
        SELECT 1 FROM accounts WHERE establishment_id=OLD.establishment_id
         AND table_id=OLD.id AND status IN ('ABERTA','EM_FECHAMENTO')
    ) THEN
        RAISE EXCEPTION 'Finalize o atendimento antes de excluir a mesa' USING ERRCODE='23514';
    END IF;
    UPDATE table_authorizations SET revoked_at=COALESCE(revoked_at,CURRENT_TIMESTAMP)
     WHERE establishment_id=OLD.establishment_id AND table_id=OLD.id;
    RETURN OLD;
END $$;
CREATE TRIGGER guard_table_deletion BEFORE DELETE ON bar_tables
 FOR EACH ROW EXECUTE FUNCTION guard_table_deletion();

-- Concluir as exclusões feitas pela versão que apenas arquivava mesas.
DELETE FROM bar_tables WHERE NOT active;

-- Produtos usados em vendas/estoque conservam o identificador histórico.
-- A exclusão é definitiva no cadastro e não pode ser desfeita por edição.
ALTER TABLE products ADD COLUMN deleted_at timestamptz;
ALTER TABLE products ADD CONSTRAINT ck_deleted_product_unavailable
 CHECK (deleted_at IS NULL OR (NOT active AND NOT available));
CREATE FUNCTION guard_deleted_product() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
BEGIN
    IF OLD.deleted_at IS NOT NULL AND
      (to_jsonb(NEW)-'stock_quantity'-'version') IS DISTINCT FROM
      (to_jsonb(OLD)-'stock_quantity'-'version') THEN
        RAISE EXCEPTION 'Produto excluído não pode ser editado ou restaurado' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_deleted_product BEFORE UPDATE ON products
 FOR EACH ROW EXECUTE FUNCTION guard_deleted_product();
