SET search_path TO bar, pg_catalog;

-- As validações entre linhas usam novas leituras após a aquisição de locks.
-- REPEATABLE READ poderia conservar um snapshot antigo mesmo após esperar o
-- lock. Exigir o nível validado evita uma falsa garantia de saldo consistente.
CREATE FUNCTION require_financial_read_committed() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, bar AS $$
BEGIN
    IF current_setting('transaction_isolation') <> 'read committed' THEN
        RAISE EXCEPTION 'Operações de conta/caixa exigem isolamento READ COMMITTED nesta versão'
            USING ERRCODE = '0A000';
    END IF;
    RETURN NEW;
END $$;
DO $$
DECLARE table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['accounts','orders','order_items','order_item_modifiers',
        'account_adjustments','payments','payment_reversals','cash_sessions','cash_movements',
        'cash_closures','cash_closure_lines'] LOOP
        EXECUTE format('CREATE TRIGGER a_financial_isolation BEFORE INSERT OR UPDATE ON bar.%I '
            'FOR EACH ROW EXECUTE FUNCTION bar.require_financial_read_committed()',table_name);
    END LOOP;
END $$;
