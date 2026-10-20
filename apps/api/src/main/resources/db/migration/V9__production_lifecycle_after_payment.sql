SET search_path TO bar, pg_catalog;

-- O cliente pode pagar antes de a cozinha terminar: avanço de produção não
-- altera o saldo e continua permitido após quitação. Cancelamento muda o saldo.
CREATE OR REPLACE FUNCTION guard_account_content() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
DECLARE account_uuid uuid; original account_adjustments%ROWTYPE; linked_account uuid; production_advance boolean := false;
BEGIN
    CASE TG_TABLE_NAME
    WHEN 'orders' THEN account_uuid := NEW.account_id;
    WHEN 'order_items' THEN SELECT account_id INTO account_uuid FROM orders
        WHERE (establishment_id,id) = (NEW.establishment_id,NEW.order_id);
    WHEN 'order_item_modifiers' THEN SELECT o.account_id INTO account_uuid FROM order_items i JOIN orders o
        ON (o.establishment_id,o.id) = (i.establishment_id,i.order_id)
        WHERE (i.establishment_id,i.id) = (NEW.establishment_id,NEW.order_item_id);
    WHEN 'account_adjustments' THEN account_uuid := NEW.account_id;
    END CASE;
    -- Blocos separados: NEW.status não existe em todas as tabelas deste trigger.
    IF TG_TABLE_NAME = 'order_items' THEN
        IF TG_OP = 'UPDATE' THEN production_advance := NEW.status <> 'CANCELADO'; END IF;
    END IF;
    IF production_advance THEN
        PERFORM 1 FROM accounts WHERE (establishment_id,id) = (NEW.establishment_id,account_uuid) FOR UPDATE;
    ELSE
        PERFORM lock_active_account(NEW.establishment_id, account_uuid,
            TG_TABLE_NAME IN ('orders','order_item_modifiers') OR (TG_TABLE_NAME = 'order_items' AND TG_OP = 'INSERT'));
    END IF;
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

CREATE OR REPLACE FUNCTION protect_item_snapshot() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
DECLARE states text[] := ARRAY['ENVIADO','ACEITO','EM_PREPARO','PRONTO','ENTREGUE'];
BEGIN
    IF TG_OP = 'DELETE' OR (to_jsonb(NEW) - 'status' - 'version')
                              IS DISTINCT FROM (to_jsonb(OLD) - 'status' - 'version') THEN
        RAISE EXCEPTION 'Item vendido não pode ser apagado ou reescrito' USING ERRCODE = '23514';
    END IF;
    IF NEW.status <> OLD.status AND (OLD.status = 'CANCELADO'
        OR (NEW.status <> 'CANCELADO' AND array_position(states,NEW.status) < array_position(states,OLD.status))) THEN
        RAISE EXCEPTION 'Produção não pode retroceder ou reativar item cancelado' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
