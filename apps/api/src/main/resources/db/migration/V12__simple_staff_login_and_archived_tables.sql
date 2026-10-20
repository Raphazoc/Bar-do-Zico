SET search_path TO bar, pg_catalog;

-- E-mails e senhas existentes permanecem válidos. O nome de acesso é opcional
-- para cadastros antigos e não exige um endereço de e-mail para novos atendentes.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ADD COLUMN username varchar(40)
    CHECK (username = lower(btrim(username)) AND username ~ '^[a-z0-9][a-z0-9._-]{1,39}$');
ALTER TABLE users ADD CONSTRAINT uq_users_unit_username UNIQUE (establishment_id, username);
ALTER TABLE users ADD CONSTRAINT ck_users_login_identifier CHECK (username IS NOT NULL OR email IS NOT NULL);

-- Retirar uma mesa do salão preserva contas, pedidos e nomes no histórico.
ALTER TABLE bar_tables ADD COLUMN active boolean NOT NULL DEFAULT true;

CREATE FUNCTION guard_archived_table() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
BEGIN
    IF NOT NEW.active AND current_setting('transaction_isolation') <> 'read committed' THEN
        RAISE EXCEPTION 'A retirada de mesas exige isolamento READ COMMITTED nesta versão'
            USING ERRCODE = '0A000';
    END IF;
    IF NOT NEW.active AND (NEW.status NOT IN ('LIVRE','BLOQUEADA') OR EXISTS (
        SELECT 1 FROM accounts WHERE establishment_id = NEW.establishment_id
            AND table_id = NEW.id AND status IN ('ABERTA','EM_FECHAMENTO')
    )) THEN
        RAISE EXCEPTION 'Finalize o atendimento antes de excluir esta mesa'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_archived_table BEFORE INSERT OR UPDATE ON bar_tables
    FOR EACH ROW EXECUTE FUNCTION guard_archived_table();

CREATE FUNCTION guard_active_account_table() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
DECLARE enabled boolean;
BEGIN
    IF NEW.table_id IS NOT NULL AND NEW.status IN ('ABERTA','EM_FECHAMENTO') THEN
        -- A abertura e a retirada da mesa se serializam pela mesma linha.
        -- Nenhuma conta é bloqueada pela retirada, preservando a ordem financeira.
        SELECT active INTO enabled FROM bar_tables
            WHERE establishment_id = NEW.establishment_id AND id = NEW.table_id FOR UPDATE;
        IF enabled IS FALSE THEN
            RAISE EXCEPTION 'Esta mesa foi excluída do salão. Restaure a mesa antes de atender'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_active_account_table BEFORE INSERT OR UPDATE OF table_id,status ON accounts
    FOR EACH ROW EXECUTE FUNCTION guard_active_account_table();
