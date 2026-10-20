SET search_path TO bar, pg_catalog;

-- Cada complemento tem quantidade POR UNIDADE do item pai.
-- Arredondamento uma vez por linha de consumo; impostos já estão no preço.
CREATE VIEW order_item_totals AS
SELECT i.establishment_id, i.id AS order_item_id, o.account_id, i.status,
       round(i.quantity * (i.unit_price + COALESCE(m.unit_modifiers_total, 0)), 2) AS line_total
FROM order_items i
JOIN orders o ON (o.establishment_id, o.id) = (i.establishment_id, i.order_id)
LEFT JOIN LATERAL (
    SELECT sum(im.unit_price * im.quantity) AS unit_modifiers_total
    FROM order_item_modifiers im
    WHERE (im.establishment_id, im.order_item_id) = (i.establishment_id, i.id)
) m ON true;

CREATE VIEW account_totals AS
SELECT a.establishment_id, a.id AS account_id,
       COALESCE(i.subtotal, 0)::numeric AS subtotal,
       COALESCE(j.adjustments_total, 0)::numeric AS adjustments_total,
       (COALESCE(i.subtotal, 0) + COALESCE(j.adjustments_total, 0))::numeric AS total,
       COALESCE(p.received_amount, 0)::numeric AS received_amount,
       COALESCE(r.reversed_amount, 0)::numeric AS reversed_amount,
       (COALESCE(p.received_amount, 0) - COALESCE(r.reversed_amount, 0))::numeric AS paid_amount,
       (COALESCE(i.subtotal, 0) + COALESCE(j.adjustments_total, 0)
           - COALESCE(p.received_amount, 0) + COALESCE(r.reversed_amount, 0))::numeric AS balance
FROM accounts a
LEFT JOIN LATERAL (
    SELECT sum(line_total) AS subtotal FROM order_item_totals t
    WHERE (t.establishment_id, t.account_id) = (a.establishment_id, a.id) AND status <> 'CANCELADO'
) i ON true
LEFT JOIN LATERAL (
    SELECT sum(amount * CASE WHEN kind = 'DESCONTO' THEN -1 ELSE 1 END
        * CASE WHEN reverses_id IS NULL THEN 1 ELSE -1 END) AS adjustments_total
    FROM account_adjustments j WHERE (j.establishment_id, j.account_id) = (a.establishment_id, a.id)
) j ON true
LEFT JOIN LATERAL (
    SELECT sum(applied_amount) AS received_amount FROM payments p
    WHERE (p.establishment_id, p.account_id) = (a.establishment_id, a.id)
) p ON true
LEFT JOIN LATERAL (
    SELECT sum(r.amount) AS reversed_amount FROM payment_reversals r
    JOIN payments p ON (p.establishment_id, p.id) = (r.establishment_id, r.payment_id)
    WHERE (p.establishment_id, p.account_id) = (a.establishment_id, a.id)
) r ON true;

-- Não unir pagamentos, estornos e movimentos em uma única junção N:N:
-- agregações separadas evitam multiplicar os valores por quantidade de linhas.
CREATE VIEW cash_session_totals AS
SELECT s.establishment_id, s.id AS cash_session_id, m.method,
       ((CASE WHEN m.method = 'DINHEIRO' THEN s.opening_amount ELSE 0 END)
           + COALESCE(p.received_amount, 0) - COALESCE(r.refunded_amount, 0)
           + CASE WHEN m.method = 'DINHEIRO' THEN COALESCE(c.manual_amount, 0) ELSE 0 END)::numeric AS expected_amount
FROM cash_sessions s
CROSS JOIN (VALUES ('DINHEIRO'),('PIX'),('DEBITO'),('CREDITO'),('VOUCHER'),('OUTRO')) m(method)
LEFT JOIN LATERAL (
    SELECT sum(p.applied_amount) AS received_amount FROM payments p
    WHERE (p.establishment_id, p.cash_session_id) = (s.establishment_id, s.id) AND p.method = m.method
) p ON true
LEFT JOIN LATERAL (
    SELECT sum(r.amount) AS refunded_amount FROM payment_reversals r
    WHERE (r.establishment_id, r.cash_session_id) = (s.establishment_id, s.id) AND r.refund_method = m.method
) r ON true
LEFT JOIN LATERAL (
    SELECT sum(c.amount * CASE WHEN c.kind = 'SUPRIMENTO' THEN 1 ELSE -1 END) AS manual_amount
    FROM cash_movements c WHERE (c.establishment_id, c.cash_session_id) = (s.establishment_id, s.id)
) c ON true;

CREATE VIEW device_presence AS
SELECT s.establishment_id, s.id AS session_id, s.device_id, s.user_id, s.last_seen_at,
    CASE WHEN d.blocked_at IS NOT NULL THEN 'BLOQUEADO'
         WHEN s.revoked_at IS NOT NULL OR s.expires_at <= CURRENT_TIMESTAMP THEN 'OFFLINE'
         WHEN s.last_seen_at >= CURRENT_TIMESTAMP
              - make_interval(secs => COALESCE((e.settings->>'presence_online_seconds')::integer, 180)) THEN 'ONLINE'
         WHEN s.last_seen_at >= CURRENT_TIMESTAMP
              - make_interval(secs => COALESCE((e.settings->>'presence_offline_seconds')::integer, 600)) THEN 'INSTAVEL'
         ELSE 'OFFLINE' END AS presence
FROM device_sessions s
JOIN devices d ON (d.establishment_id, d.id) = (s.establishment_id, s.device_id)
JOIN establishments e ON e.id = s.establishment_id;

CREATE INDEX ix_products_catalog ON products(establishment_id, category_id, name) WHERE active;
CREATE INDEX ix_accounts_open ON accounts(establishment_id, opened_at) WHERE status IN ('ABERTA','EM_FECHAMENTO');
CREATE INDEX ix_order_items_queue ON order_items(establishment_id, destination, status, order_id)
    WHERE status IN ('ENVIADO','ACEITO','EM_PREPARO','PRONTO');
CREATE INDEX ix_orders_account_sent ON orders(establishment_id, account_id, sent_at);
CREATE INDEX ix_audit_events_timeline ON audit_events(establishment_id, occurred_at DESC);
CREATE INDEX ix_outbox_pending ON outbox_events(next_attempt_at, occurred_at) WHERE published_at IS NULL;
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens(establishment_id, family_id);
CREATE INDEX ix_device_sessions_presence ON device_sessions(establishment_id, last_seen_at) WHERE revoked_at IS NULL;

-- Indexar todas as FKs sem duplicar índices que já possuem o mesmo prefixo.
DO $$
DECLARE fk record;
BEGIN
    FOR fk IN
        SELECT c.conrelid, c.conname, c.conkey,
            string_agg(quote_ident(a.attname), ', ' ORDER BY k.ord) AS columns
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY k(attnum, ord)
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
        WHERE c.contype = 'f' AND n.nspname = 'bar'
        GROUP BY c.conrelid, c.conname, c.conkey
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_index i WHERE i.indrelid = fk.conrelid AND i.indpred IS NULL
            AND i.indisvalid AND i.indkey::smallint[] @> fk.conkey
            AND (SELECT array_agg(v ORDER BY ord) FROM unnest(i.indkey::smallint[]) WITH ORDINALITY u(v,ord)
                 WHERE ord <= cardinality(fk.conkey)) = fk.conkey
        ) THEN
            EXECUTE format('CREATE INDEX %I ON %s (%s)',
                'ix_fk_' || substr(md5(fk.conrelid::regclass::text || fk.conname), 1, 20),
                fk.conrelid::regclass, fk.columns);
        END IF;
    END LOOP;
END $$;

CREATE FUNCTION reject_history_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
BEGIN
    RAISE EXCEPTION '%: histórico imutável; registre uma compensação', TG_TABLE_NAME USING ERRCODE = '23514';
END $$;

DO $$
DECLARE table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['orders','order_item_modifiers','production_events','account_adjustments',
        'payments','payment_reversals','cash_movements','cash_closures','cash_closure_lines','audit_events']
    LOOP
        EXECUTE format('CREATE TRIGGER protect_history BEFORE UPDATE OR DELETE ON bar.%I '
            'FOR EACH ROW EXECUTE FUNCTION bar.reject_history_change()', table_name);
        EXECUTE format('CREATE TRIGGER protect_truncate BEFORE TRUNCATE ON bar.%I '
            'FOR EACH STATEMENT EXECUTE FUNCTION bar.reject_history_change()', table_name);
    END LOOP;
END $$;

CREATE FUNCTION protect_item_snapshot() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
BEGIN
    IF TG_OP = 'DELETE' OR (to_jsonb(NEW) - 'status' - 'version')
                              IS DISTINCT FROM (to_jsonb(OLD) - 'status' - 'version') THEN
        RAISE EXCEPTION 'Item vendido não pode ser apagado ou reescrito' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_item_snapshot BEFORE UPDATE OR DELETE ON order_items
    FOR EACH ROW EXECUTE FUNCTION protect_item_snapshot();
CREATE TRIGGER protect_item_truncate BEFORE TRUNCATE ON order_items
    FOR EACH STATEMENT EXECUTE FUNCTION reject_history_change();

CREATE FUNCTION protect_delivery_payload() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
BEGIN
    IF (to_jsonb(NEW) - 'published_at' - 'attempts' - 'next_attempt_at') IS DISTINCT FROM
       (to_jsonb(OLD) - 'published_at' - 'attempts' - 'next_attempt_at') THEN
        RAISE EXCEPTION 'Payload da outbox é imutável' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_delivery_payload BEFORE UPDATE ON outbox_events
    FOR EACH ROW EXECUTE FUNCTION protect_delivery_payload();

CREATE FUNCTION protect_idempotency() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.resource_type IN ('payment','payment_reversal') THEN
            RAISE EXCEPTION 'Vínculo financeiro de idempotência deve ser preservado' USING ERRCODE = '23514';
        END IF;
        RETURN OLD;
    END IF;
    IF ROW(NEW.id, NEW.establishment_id, NEW.user_id, NEW.operation, NEW.key, NEW.request_hash, NEW.created_at)
       IS DISTINCT FROM ROW(OLD.id, OLD.establishment_id, OLD.user_id, OLD.operation, OLD.key, OLD.request_hash, OLD.created_at)
       OR (OLD.status = 'CONCLUIDO' AND NEW IS DISTINCT FROM OLD) THEN
        RAISE EXCEPTION 'Chave e resultado concluído de idempotência são imutáveis' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_idempotency BEFORE UPDATE OR DELETE ON idempotency_records
    FOR EACH ROW EXECUTE FUNCTION protect_idempotency();
CREATE TRIGGER protect_idempotency_truncate BEFORE TRUNCATE ON idempotency_records
    FOR EACH STATEMENT EXECUTE FUNCTION reject_history_change();
