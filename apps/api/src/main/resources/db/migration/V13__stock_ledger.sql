SET search_path TO bar, pg_catalog;

ALTER TABLE products ADD COLUMN stock_controlled boolean NOT NULL DEFAULT false;
ALTER TABLE products ADD COLUMN stock_quantity numeric(14,3) NOT NULL DEFAULT 0 CHECK(stock_quantity>=0);
ALTER TABLE products ADD COLUMN stock_minimum numeric(14,3) NOT NULL DEFAULT 0 CHECK(stock_minimum>=0);

CREATE TABLE stock_movements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    product_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    kind varchar(20) NOT NULL CHECK(kind IN ('ENTRADA','SAIDA','AJUSTE','VENDA','CANCELAMENTO')),
    quantity numeric(14,3) NOT NULL CHECK(quantity<>0),
    balance_after numeric(14,3) NOT NULL CHECK(balance_after>=0),
    reason varchar(1000) NOT NULL CHECK(btrim(reason)<>''),
    order_item_id uuid,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(establishment_id,product_id) REFERENCES products(establishment_id,id),
    FOREIGN KEY(establishment_id,actor_id) REFERENCES users(establishment_id,id),
    FOREIGN KEY(establishment_id,order_item_id) REFERENCES order_items(establishment_id,id),
    UNIQUE(establishment_id,order_item_id,kind),
    CHECK((kind IN ('ENTRADA','CANCELAMENTO') AND quantity>0) OR
          (kind IN ('SAIDA','VENDA') AND quantity<0) OR kind='AJUSTE')
);
CREATE INDEX ix_stock_history ON stock_movements(establishment_id,created_at DESC);
CREATE INDEX ix_stock_product ON stock_movements(establishment_id,product_id,created_at DESC);

CREATE FUNCTION apply_stock_movement() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,bar AS $$
DECLARE current_quantity numeric; controlled boolean;
BEGIN
    IF current_setting('transaction_isolation')<>'read committed' THEN
        RAISE EXCEPTION 'Estoque exige READ COMMITTED' USING ERRCODE='0A000';
    END IF;
    SELECT stock_quantity,stock_controlled INTO current_quantity,controlled FROM products
      WHERE establishment_id=NEW.establishment_id AND id=NEW.product_id FOR UPDATE;
    IF NOT FOUND OR NOT controlled THEN RAISE EXCEPTION 'Ative o controle de estoque deste produto' USING ERRCODE='23514'; END IF;
    NEW.balance_after:=current_quantity+NEW.quantity;
    IF NEW.balance_after<0 THEN RAISE EXCEPTION 'Estoque insuficiente' USING ERRCODE='23514'; END IF;
    UPDATE products SET stock_quantity=NEW.balance_after,version=version+1
      WHERE establishment_id=NEW.establishment_id AND id=NEW.product_id;
    RETURN NEW;
END $$;
CREATE TRIGGER apply_stock_movement BEFORE INSERT ON stock_movements FOR EACH ROW EXECUTE FUNCTION apply_stock_movement();

CREATE FUNCTION guard_stock_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Corrija o estoque por uma nova movimentação; o histórico é preservado' USING ERRCODE='23514'; END $$;
CREATE TRIGGER guard_stock_history BEFORE UPDATE OR DELETE ON stock_movements FOR EACH ROW EXECUTE FUNCTION guard_stock_history();
CREATE TRIGGER guard_stock_truncate BEFORE TRUNCATE ON stock_movements FOR EACH STATEMENT EXECUTE FUNCTION guard_stock_history();
CREATE FUNCTION guard_stock_balance() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' AND NEW.stock_quantity<>0 THEN RAISE EXCEPTION 'Informe o saldo inicial por uma entrada' USING ERRCODE='23514'; END IF;
    IF TG_OP='UPDATE' AND NEW.stock_quantity<>OLD.stock_quantity AND pg_trigger_depth()<2 THEN
      RAISE EXCEPTION 'Altere a quantidade por uma movimentação' USING ERRCODE='23514';
    END IF;
    IF TG_OP='UPDATE' AND OLD.stock_controlled AND NOT NEW.stock_controlled THEN
      RAISE EXCEPTION 'Preserve o controle e corrija o saldo por movimentações' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_stock_balance BEFORE INSERT OR UPDATE ON products FOR EACH ROW EXECUTE FUNCTION guard_stock_balance();

CREATE FUNCTION stock_for_order() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,bar AS $$
DECLARE operator_id uuid; tracked boolean;
BEGIN
    SELECT stock_controlled INTO tracked FROM products WHERE establishment_id=NEW.establishment_id AND id=NEW.product_id FOR UPDATE;
    IF tracked THEN
      SELECT waiter_id INTO operator_id FROM orders WHERE establishment_id=NEW.establishment_id AND id=NEW.order_id;
      INSERT INTO stock_movements(establishment_id,product_id,actor_id,kind,quantity,balance_after,reason,order_item_id)
      VALUES(NEW.establishment_id,NEW.product_id,operator_id,'VENDA',-NEW.quantity,0,'Pedido enviado',NEW.id);
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER stock_for_order AFTER INSERT ON order_items FOR EACH ROW EXECUTE FUNCTION stock_for_order();

CREATE FUNCTION stock_for_cancellation() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,bar AS $$
DECLARE original stock_movements%ROWTYPE;
BEGIN
    IF NEW.to_status='CANCELADO' AND (NEW.from_status IN ('ENVIADO','ACEITO') OR NEW.stock_disposition='RETORNO_FISICO') THEN
      SELECT * INTO original FROM stock_movements WHERE establishment_id=NEW.establishment_id
        AND order_item_id=NEW.order_item_id AND kind='VENDA';
      IF FOUND THEN
        INSERT INTO stock_movements(establishment_id,product_id,actor_id,kind,quantity,balance_after,reason,order_item_id)
        VALUES(NEW.establishment_id,original.product_id,NEW.actor_id,'CANCELAMENTO',-original.quantity,0,'Devolução por cancelamento',NEW.order_item_id);
      END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER stock_for_cancellation AFTER INSERT ON production_events FOR EACH ROW EXECUTE FUNCTION stock_for_cancellation();
