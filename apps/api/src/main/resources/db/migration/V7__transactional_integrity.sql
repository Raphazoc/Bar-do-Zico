SET search_path TO bar, pg_catalog;

CREATE FUNCTION lock_active_account(unit_id uuid, target_id uuid, order_creation boolean DEFAULT false)
RETURNS void LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
DECLARE current_status text;
BEGIN
    SELECT status INTO current_status FROM accounts
        WHERE establishment_id = unit_id AND id = target_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Conta inexistente nesta unidade' USING ERRCODE = '23503';
    END IF;
    IF current_status NOT IN ('ABERTA','EM_FECHAMENTO') OR (order_creation AND current_status <> 'ABERTA') THEN
        RAISE EXCEPTION 'Conta não aceita esta operação: %', current_status USING ERRCODE = '23514';
    END IF;
END $$;

CREATE FUNCTION lock_open_cash_session(unit_id uuid, target_id uuid)
RETURNS void LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
DECLARE current_status text;
BEGIN
    SELECT status INTO current_status FROM cash_sessions
        WHERE establishment_id = unit_id AND id = target_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Caixa inexistente nesta unidade' USING ERRCODE = '23503';
    END IF;
    IF current_status <> 'ABERTA' THEN
        RAISE EXCEPTION 'Caixa fechado não aceita lançamentos' USING ERRCODE = '23514';
    END IF;
END $$;

CREATE FUNCTION guard_account_content() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
DECLARE account_uuid uuid; original account_adjustments%ROWTYPE; linked_account uuid;
BEGIN
    CASE TG_TABLE_NAME
    WHEN 'orders' THEN account_uuid := NEW.account_id;
    WHEN 'order_items' THEN
        SELECT account_id INTO account_uuid FROM orders
            WHERE (establishment_id, id) = (NEW.establishment_id, NEW.order_id);
    WHEN 'order_item_modifiers' THEN
        SELECT o.account_id INTO account_uuid FROM order_items i JOIN orders o
            ON (o.establishment_id,o.id) = (i.establishment_id,i.order_id)
            WHERE (i.establishment_id,i.id) = (NEW.establishment_id,NEW.order_item_id);
    WHEN 'account_adjustments' THEN account_uuid := NEW.account_id;
    END CASE;
    PERFORM lock_active_account(NEW.establishment_id, account_uuid,
        TG_TABLE_NAME IN ('orders','order_item_modifiers') OR (TG_TABLE_NAME = 'order_items' AND TG_OP = 'INSERT'));

    IF TG_TABLE_NAME = 'order_items' AND TG_OP = 'INSERT' THEN
        PERFORM 1 FROM products WHERE (establishment_id,id) = (NEW.establishment_id,NEW.product_id)
            AND active AND available FOR SHARE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Produto inexistente ou indisponível' USING ERRCODE = '23514';
        END IF;
    ELSIF TG_TABLE_NAME = 'account_adjustments' THEN
        IF NEW.order_item_id IS NOT NULL THEN
            SELECT o.account_id INTO linked_account FROM order_items i JOIN orders o
                ON (o.establishment_id,o.id) = (i.establishment_id,i.order_id)
                WHERE (i.establishment_id,i.id) = (NEW.establishment_id,NEW.order_item_id);
            IF linked_account IS DISTINCT FROM NEW.account_id THEN
                RAISE EXCEPTION 'Item do ajuste pertence a outra conta' USING ERRCODE = '23514';
            END IF;
        END IF;
        IF NEW.reverses_id IS NOT NULL THEN
            SELECT * INTO original FROM account_adjustments
                WHERE (establishment_id,id) = (NEW.establishment_id,NEW.reverses_id);
            IF NOT FOUND OR original.account_id <> NEW.account_id OR original.reverses_id IS NOT NULL
                OR ROW(original.kind,original.amount,original.order_item_id,original.rate,original.base_amount)
                   IS DISTINCT FROM ROW(NEW.kind,NEW.amount,NEW.order_item_id,NEW.rate,NEW.base_amount) THEN
                RAISE EXCEPTION 'Compensação deve anular integralmente o ajuste original da conta' USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_content BEFORE INSERT ON orders FOR EACH ROW EXECUTE FUNCTION guard_account_content();
CREATE TRIGGER guard_content BEFORE INSERT OR UPDATE ON order_items FOR EACH ROW EXECUTE FUNCTION guard_account_content();
CREATE TRIGGER guard_content BEFORE INSERT ON order_item_modifiers FOR EACH ROW EXECUTE FUNCTION guard_account_content();
CREATE TRIGGER guard_content BEFORE INSERT ON account_adjustments FOR EACH ROW EXECUTE FUNCTION guard_account_content();

-- Ordem comum de locks para finanças: caixa, conta, pagamento original.
CREATE FUNCTION guard_payment() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
DECLARE outstanding numeric;
BEGIN
    PERFORM lock_open_cash_session(NEW.establishment_id, NEW.cash_session_id);
    PERFORM lock_active_account(NEW.establishment_id, NEW.account_id);
    SELECT balance INTO outstanding FROM account_totals
        WHERE (establishment_id,account_id) = (NEW.establishment_id,NEW.account_id);
    IF NEW.applied_amount > outstanding THEN
        RAISE EXCEPTION 'Recebimento ultrapassa saldo de %', outstanding USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_payment BEFORE INSERT ON payments FOR EACH ROW EXECUTE FUNCTION guard_payment();

CREATE FUNCTION guard_reversal() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
DECLARE original payments%ROWTYPE; refunded numeric;
BEGIN
    SELECT * INTO original FROM payments
        WHERE (establishment_id,id) = (NEW.establishment_id,NEW.payment_id);
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Pagamento inexistente nesta unidade' USING ERRCODE = '23503';
    END IF;
    PERFORM lock_open_cash_session(NEW.establishment_id, NEW.cash_session_id);
    PERFORM lock_active_account(NEW.establishment_id, original.account_id);
    PERFORM 1 FROM payments WHERE id = original.id FOR UPDATE;
    SELECT COALESCE(sum(amount),0) INTO refunded FROM payment_reversals
        WHERE (establishment_id,payment_id) = (NEW.establishment_id,NEW.payment_id);
    IF refunded + NEW.amount > original.applied_amount THEN
        RAISE EXCEPTION 'Estornos ultrapassam o valor original recebido' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_reversal BEFORE INSERT ON payment_reversals FOR EACH ROW EXECUTE FUNCTION guard_reversal();

CREATE FUNCTION guard_cash_movement() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
BEGIN
    PERFORM lock_open_cash_session(NEW.establishment_id, NEW.cash_session_id);
    RETURN NEW;
END $$;
CREATE TRIGGER guard_cash_movement BEFORE INSERT ON cash_movements
    FOR EACH ROW EXECUTE FUNCTION guard_cash_movement();

CREATE FUNCTION check_account_balance() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
DECLARE account_uuid uuid; totals record; current_status text;
BEGIN
    CASE TG_TABLE_NAME
    WHEN 'accounts' THEN account_uuid := NEW.id;
    WHEN 'orders','payments','account_adjustments' THEN account_uuid := NEW.account_id;
    WHEN 'order_items' THEN SELECT account_id INTO account_uuid FROM orders
        WHERE (establishment_id,id) = (NEW.establishment_id,NEW.order_id);
    WHEN 'order_item_modifiers' THEN SELECT o.account_id INTO account_uuid FROM orders o JOIN order_items i
        ON (o.establishment_id,o.id) = (i.establishment_id,i.order_id)
        WHERE (i.establishment_id,i.id) = (NEW.establishment_id,NEW.order_item_id);
    WHEN 'payment_reversals' THEN SELECT account_id INTO account_uuid FROM payments
        WHERE (establishment_id,id) = (NEW.establishment_id,NEW.payment_id);
    END CASE;
    SELECT * INTO totals FROM account_totals
        WHERE (establishment_id,account_id) = (NEW.establishment_id,account_uuid);
    SELECT status INTO current_status FROM accounts WHERE id = account_uuid;
    IF totals.total < 0 OR totals.balance < 0 THEN
        RAISE EXCEPTION 'Total/saldo negativo não permitido' USING ERRCODE = '23514';
    END IF;
    IF current_status = 'FECHADA' AND totals.balance <> 0 THEN
        RAISE EXCEPTION 'Conta fechada precisa estar quitada' USING ERRCODE = '23514';
    END IF;
    IF current_status = 'CANCELADA' AND (totals.total <> 0 OR totals.paid_amount <> 0) THEN
        RAISE EXCEPTION 'Conta cancelada não pode manter consumo ou recebimento líquido' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END $$;
DO $$
DECLARE table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['accounts','orders','order_items','order_item_modifiers',
        'account_adjustments','payments','payment_reversals']
    LOOP
        EXECUTE format('CREATE CONSTRAINT TRIGGER account_balance_consistent AFTER INSERT OR UPDATE ON bar.%I '
            'DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION bar.check_account_balance()',table_name);
    END LOOP;
END $$;

CREATE FUNCTION protect_cash_session() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Sessões de caixa não podem ser apagadas' USING ERRCODE = '23514';
    END IF;
    IF (OLD.status = 'FECHADA' AND NEW IS DISTINCT FROM OLD)
        OR (to_jsonb(NEW) - 'status' - 'closed_at' - 'version')
           IS DISTINCT FROM (to_jsonb(OLD) - 'status' - 'closed_at' - 'version') THEN
        RAISE EXCEPTION 'Dados de abertura/caixa encerrado são imutáveis' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_cash_session BEFORE UPDATE OR DELETE ON cash_sessions
    FOR EACH ROW EXECUTE FUNCTION protect_cash_session();

CREATE FUNCTION guard_closure() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
DECLARE original_session uuid;
BEGIN
    PERFORM 1 FROM cash_sessions
        WHERE (establishment_id,id) = (NEW.establishment_id,NEW.cash_session_id) FOR UPDATE;
    IF NEW.corrects_id IS NOT NULL THEN
        SELECT cash_session_id INTO original_session FROM cash_closures
            WHERE (establishment_id,id) = (NEW.establishment_id,NEW.corrects_id);
        IF original_session IS DISTINCT FROM NEW.cash_session_id THEN
            RAISE EXCEPTION 'Correção pertence a outro turno' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_closure BEFORE INSERT ON cash_closures FOR EACH ROW EXECUTE FUNCTION guard_closure();

CREATE FUNCTION check_cash_closure() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
DECLARE session_uuid uuid; session_status text; c record; line_count integer;
BEGIN
    IF TG_TABLE_NAME = 'cash_sessions' THEN session_uuid := NEW.id;
    ELSIF TG_TABLE_NAME = 'cash_closures' THEN session_uuid := NEW.cash_session_id;
    ELSE SELECT cash_session_id INTO session_uuid FROM cash_closures WHERE id = NEW.closure_id;
    END IF;
    SELECT status INTO session_status FROM cash_sessions WHERE id = session_uuid;
    IF session_status = 'FECHADA' AND NOT EXISTS (
        SELECT 1 FROM cash_closures WHERE cash_session_id = session_uuid AND corrects_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Fechar caixa exige conferência original na mesma transação' USING ERRCODE = '23514';
    END IF;
    FOR c IN SELECT id,establishment_id FROM cash_closures WHERE cash_session_id = session_uuid LOOP
        IF session_status <> 'FECHADA' THEN
            RAISE EXCEPTION 'Fechamento exige turno fechado' USING ERRCODE = '23514';
        END IF;
        SELECT count(*) INTO line_count FROM cash_closure_lines WHERE closure_id = c.id;
        IF line_count <> 6 OR EXISTS (
            SELECT 1 FROM cash_closure_lines l JOIN cash_session_totals t
            ON (t.establishment_id,t.cash_session_id,t.method) = (l.establishment_id,session_uuid,l.method)
            WHERE l.closure_id = c.id AND l.expected_amount <> t.expected_amount
        ) THEN
            RAISE EXCEPTION 'Conferir as seis formas com o esperado calculado pelo banco' USING ERRCODE = '23514';
        END IF;
    END LOOP;
    RETURN NULL;
END $$;
DO $$
DECLARE table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['cash_sessions','cash_closures','cash_closure_lines'] LOOP
        EXECUTE format('CREATE CONSTRAINT TRIGGER cash_closure_consistent AFTER INSERT OR UPDATE ON bar.%I '
            'DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION bar.check_cash_closure()',table_name);
    END LOOP;
END $$;

CREATE FUNCTION validate_establishment_settings() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
DECLARE online_seconds integer; offline_seconds integer;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_timezone_names WHERE name = NEW.timezone) THEN
        RAISE EXCEPTION 'Fuso horário inválido' USING ERRCODE = '23514';
    END IF;
    online_seconds := COALESCE((NEW.settings->>'presence_online_seconds')::integer,180);
    offline_seconds := COALESCE((NEW.settings->>'presence_offline_seconds')::integer,600);
    IF online_seconds <= 0 OR offline_seconds <= online_seconds THEN
        RAISE EXCEPTION 'Limites de presença inválidos' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_settings BEFORE INSERT OR UPDATE ON establishments
    FOR EACH ROW EXECUTE FUNCTION validate_establishment_settings();
