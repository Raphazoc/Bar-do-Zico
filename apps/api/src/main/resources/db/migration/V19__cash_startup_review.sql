SET search_path TO bar, pg_catalog;

-- Reopening the PC never invents a closing count or resumes an old cash shift.
ALTER TABLE cash_sessions ADD COLUMN review_required boolean NOT NULL DEFAULT false;
ALTER TABLE cash_sessions ADD CONSTRAINT closed_cash_without_pending_review
    CHECK (status='ABERTA' OR NOT review_required);

-- Keep the old opening/history protection while allowing only the review marker.
CREATE OR REPLACE FUNCTION protect_cash_session() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Sessões de caixa não podem ser apagadas' USING ERRCODE='23514';
    END IF;
    IF (OLD.status='FECHADA' AND NEW IS DISTINCT FROM OLD)
        OR (to_jsonb(NEW)-'status'-'closed_at'-'version'-'review_required')
           IS DISTINCT FROM (to_jsonb(OLD)-'status'-'closed_at'-'version'-'review_required') THEN
        RAISE EXCEPTION 'Dados de abertura/caixa encerrado são imutáveis' USING ERRCODE='23514';
    END IF;
    IF OLD.review_required AND NOT NEW.review_required AND NEW.status<>'FECHADA' THEN
        RAISE EXCEPTION 'A conferência pendente só termina com o fechamento' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION lock_open_cash_session(unit_id uuid,target_id uuid)
RETURNS void LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
DECLARE current_status text; pending boolean;
BEGIN
    SELECT status,review_required INTO current_status,pending FROM cash_sessions
        WHERE establishment_id=unit_id AND id=target_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Caixa inexistente nesta unidade' USING ERRCODE='23503';
    END IF;
    IF current_status<>'ABERTA' OR pending THEN
        RAISE EXCEPTION 'Caixa fechado ou com conferência pendente não aceita lançamentos' USING ERRCODE='23514';
    END IF;
END $$;

CREATE FUNCTION check_cash_opening_review() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.establishment_id::text||':cash-opening',0));
    IF NEW.status='ABERTA' AND EXISTS(SELECT 1 FROM cash_sessions WHERE establishment_id=NEW.establishment_id AND status='ABERTA' AND review_required) THEN
        RAISE EXCEPTION 'Confira e feche o caixa anterior antes da nova abertura' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER cash_opening_review BEFORE INSERT ON cash_sessions
    FOR EACH ROW EXECUTE FUNCTION check_cash_opening_review();
