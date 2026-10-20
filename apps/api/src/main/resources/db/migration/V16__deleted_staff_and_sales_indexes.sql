SET search_path TO bar, pg_catalog;

-- Referências históricas continuam apontando ao funcionário original.
ALTER TABLE users ADD COLUMN deleted_at timestamptz;
ALTER TABLE users ADD CONSTRAINT ck_deleted_staff_inactive CHECK (deleted_at IS NULL OR NOT active);
CREATE FUNCTION guard_deleted_staff() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
BEGIN
    IF OLD.deleted_at IS NOT NULL AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Funcionário excluído não pode ser editado ou restaurado' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_deleted_staff BEFORE UPDATE ON users
 FOR EACH ROW EXECUTE FUNCTION guard_deleted_staff();

CREATE INDEX ix_accounts_closed_sales ON accounts(establishment_id,closed_at DESC,id DESC)
 WHERE status='FECHADA';
CREATE INDEX ix_payments_session ON payments(establishment_id,cash_session_id);
CREATE INDEX ix_reversals_session ON payment_reversals(establishment_id,cash_session_id);
