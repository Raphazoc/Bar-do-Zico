"""Integração em PostgreSQL real. Executar com scripts/validate_database.py."""
from __future__ import annotations

import os
import time
import unittest
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal
from threading import Event
from uuid import uuid4

import psycopg
from psycopg.rows import dict_row

DSN = os.environ['BAR_TEST_DSN']


def connect():
    return psycopg.connect(DSN, row_factory=dict_row, options='-c search_path=bar,public -c timezone=UTC -c lock_timeout=5000')


def insert(c, table, **values):
    from psycopg import sql
    query = sql.SQL('INSERT INTO bar.{} ({}) VALUES ({}) RETURNING *').format(
        sql.Identifier(table), sql.SQL(',').join(map(sql.Identifier, values)),
        sql.SQL(',').join(sql.Placeholder() for _ in values))
    return c.execute(query, tuple(values.values())).fetchone()


def fixture(c, price='100.00', quantity='1'):
    e = insert(c, 'establishments', name='Bar de teste')
    u = insert(c, 'users', establishment_id=e['id'], name='Operador',
               email=f'{uuid4()}@example.test', password_hash='hash-ficticio-apenas-para-teste')
    a = insert(c, 'areas', establishment_id=e['id'], name='Salão')
    t = insert(c, 'bar_tables', establishment_id=e['id'], area_id=a['id'], number='01', status='OCUPADA')
    cat = insert(c, 'categories', establishment_id=e['id'], name='Bebidas')
    p = insert(c, 'products', establishment_id=e['id'], category_id=cat['id'], name='Produto',
               price=price, production_destination='BAR')
    acc = insert(c, 'accounts', establishment_id=e['id'], table_id=t['id'], opened_by=u['id'])
    o = insert(c, 'orders', establishment_id=e['id'], account_id=acc['id'], waiter_id=u['id'])
    i = insert(c, 'order_items', establishment_id=e['id'], order_id=o['id'], product_id=p['id'],
               description_snapshot='Produto vendido', unit_price=price, quantity=quantity, destination='BAR')
    terminal = insert(c, 'cash_terminals', establishment_id=e['id'], name='Caixa 1')
    sess = insert(c, 'cash_sessions', establishment_id=e['id'], terminal_id=terminal['id'],
                  operator_id=u['id'], opening_amount='100.00')
    return dict(e=e, u=u, t=t, p=p, acc=acc, o=o, i=i, sess=sess, terminal=terminal)


def payment(c, f, amount, method='PIX', tendered=None, change='0', session=None):
    return insert(c, 'payments', establishment_id=f['e']['id'], account_id=f['acc']['id'],
                  cash_session_id=session or f['sess']['id'], operator_id=f['u']['id'], method=method,
                  applied_amount=amount, tendered_amount=tendered or amount, change_amount=change)


def adjustment(c, f, kind='DESCONTO', amount='10', **kwargs):
    return insert(c, 'account_adjustments', establishment_id=f['e']['id'], account_id=f['acc']['id'],
                  actor_id=f['u']['id'], kind=kind, amount=amount, reason='Ajuste autorizado', **kwargs)


def reversal(c, f, pay, amount, session=None):
    return insert(c, 'payment_reversals', establishment_id=f['e']['id'], payment_id=pay['id'],
                  cash_session_id=session or f['sess']['id'], operator_id=f['u']['id'],
                  authorized_by=f['u']['id'], amount=amount, refund_method='DINHEIRO', reason='Devolução')


def second_session(c, f):
    t = insert(c, 'cash_terminals', establishment_id=f['e']['id'], name=f'Caixa {uuid4()}')
    return insert(c, 'cash_sessions', establishment_id=f['e']['id'], terminal_id=t['id'],
                  operator_id=f['u']['id'], opening_amount='0')


def close_session(c, f):
    closure = insert(c, 'cash_closures', establishment_id=f['e']['id'], cash_session_id=f['sess']['id'],
                     closed_by=f['u']['id'])
    c.execute('''INSERT INTO cash_closure_lines(establishment_id,closure_id,method,expected_amount,counted_amount)
        SELECT establishment_id,%s,method,expected_amount,greatest(expected_amount,0)
        FROM cash_session_totals WHERE cash_session_id=%s''', (closure['id'], f['sess']['id']))
    c.execute("UPDATE cash_sessions SET status='FECHADA',closed_at=CURRENT_TIMESTAMP WHERE id=%s", (f['sess']['id'],))
    return closure


class DatabaseTests(unittest.TestCase):
    def setUp(self):
        self.c = connect()
        self.f = fixture(self.c)

    def tearDown(self):
        self.c.rollback()
        self.c.close()

    def reject(self, callback, state='23514'):
        with self.assertRaises(psycopg.Error) as caught:
            with self.c.transaction():
                callback()
                self.c.execute('SET CONSTRAINTS ALL IMMEDIATE')
        self.assertEqual(caught.exception.sqlstate, state)

    def totals(self):
        return self.c.execute('SELECT * FROM account_totals WHERE account_id=%s', (self.f['acc']['id'],)).fetchone()

    def test_33_tables_and_tenant_foreign_keys(self):
        tables = self.c.execute("SELECT tablename FROM pg_tables WHERE schemaname='bar' AND tablename NOT IN ('flyway_schema_history','app_sessions','login_attempts')").fetchall()
        self.assertEqual(len(tables), 33)
        bad = self.c.execute('''SELECT c.conname FROM pg_constraint c
            JOIN pg_class p ON p.oid=c.confrelid JOIN pg_namespace n ON n.oid=p.relnamespace
            WHERE c.contype='f' AND n.nspname='bar' AND p.relname NOT IN ('establishments','permissions')
            AND cardinality(c.conkey)<>2''').fetchall()
        self.assertEqual(bad, [])

    def test_cross_tenant_reference_rejected(self):
        other = fixture(self.c)
        self.reject(lambda: insert(self.c, 'user_roles', establishment_id=self.f['e']['id'],
            user_id=other['u']['id'], role_id=insert(self.c,'roles',establishment_id=self.f['e']['id'],name='Caixa')['id']), '23503')

    def test_unique_active_account_and_nullable_reopening(self):
        self.reject(lambda: insert(self.c, 'accounts', establishment_id=self.f['e']['id'],
            table_id=self.f['t']['id'], opened_by=self.f['u']['id']), '23505')
        insert(self.c, 'accounts', establishment_id=self.f['e']['id'], opened_by=self.f['u']['id'])

    def test_unique_open_cash_session(self):
        self.reject(lambda: insert(self.c, 'cash_sessions', establishment_id=self.f['e']['id'],
            terminal_id=self.f['terminal']['id'], operator_id=self.f['u']['id'], opening_amount='0'), '23505')

    def test_partial_payment_and_cash_change(self):
        adjustment(self.c,self.f)
        adjustment(self.c,self.f,kind='SERVICO',amount='9',rate='10',base_amount='90')
        payment(self.c,self.f,'40')
        self.assertEqual(self.totals()['balance'],Decimal('59'))
        payment(self.c,self.f,'59',method='DINHEIRO',tendered='100',change='41')
        self.assertEqual(self.totals()['balance'],0)
        expected=self.c.execute("SELECT expected_amount FROM cash_session_totals WHERE cash_session_id=%s AND method='DINHEIRO'",(self.f['sess']['id'],)).fetchone()
        self.assertEqual(expected['expected_amount'],Decimal('159'))

    def test_invalid_cash_change_and_noncash_change(self):
        self.reject(lambda:payment(self.c,self.f,'50',method='DINHEIRO',tendered='100',change='40'))
        self.reject(lambda:payment(self.c,self.f,'50',tendered='60',change='10'))

    def test_overpayment_rejected(self):
        payment(self.c,self.f,'60')
        self.reject(lambda:payment(self.c,self.f,'41'))

    def test_unpaid_account_cannot_close(self):
        self.reject(lambda:self.c.execute("UPDATE accounts SET status='FECHADA',closed_at=CURRENT_TIMESTAMP WHERE id=%s",(self.f['acc']['id'],)))

    def test_negative_balance_after_discount_rejected(self):
        payment(self.c,self.f,'80')
        self.reject(lambda:adjustment(self.c,self.f,amount='30'))

    def test_cancellation_cannot_erase_paid_consumption(self):
        payment(self.c,self.f,'20')
        self.reject(lambda:self.c.execute("UPDATE order_items SET status='CANCELADO' WHERE id=%s",(self.f['i']['id'],)))

    def test_price_snapshot_is_immutable(self):
        self.c.execute('UPDATE products SET price=999 WHERE id=%s',(self.f['p']['id'],))
        self.assertEqual(self.totals()['total'],Decimal('100'))
        self.reject(lambda:self.c.execute('UPDATE order_items SET unit_price=999 WHERE id=%s',(self.f['i']['id'],)))

    def test_unavailable_product_rejected(self):
        self.c.execute('UPDATE products SET available=false WHERE id=%s',(self.f['p']['id'],))
        self.reject(lambda:insert(self.c,'order_items',establishment_id=self.f['e']['id'],order_id=self.f['o']['id'],
            product_id=self.f['p']['id'],description_snapshot='Esgotado',unit_price='100',quantity='1',destination='BAR'))

    def test_modifier_quantity_per_parent_unit(self):
        f=fixture(self.c,price='10',quantity='3')
        g=insert(self.c,'modifier_groups',establishment_id=f['e']['id'],name='Adicionais',max_choices=5)
        m=insert(self.c,'modifiers',establishment_id=f['e']['id'],group_id=g['id'],name='Extra',price='2')
        insert(self.c,'product_modifier_groups',establishment_id=f['e']['id'],product_id=f['p']['id'],group_id=g['id'])
        insert(self.c,'order_item_modifiers',establishment_id=f['e']['id'],order_item_id=f['i']['id'],modifier_id=m['id'],
            description_snapshot='Extra',unit_price='2',quantity='2')
        value=self.c.execute('SELECT total FROM account_totals WHERE account_id=%s',(f['acc']['id'],)).fetchone()
        self.assertEqual(value['total'],Decimal('42'))

    def test_round_half_up_at_line(self):
        f=fixture(self.c,price='1',quantity='0.335')
        total=self.c.execute('SELECT total FROM account_totals WHERE account_id=%s',(f['acc']['id'],)).fetchone()
        self.assertEqual(total['total'],Decimal('0.34'))

    def test_adjustment_compensation_is_linked_and_unique(self):
        original=adjustment(self.c,self.f)
        adjustment(self.c,self.f,reverses_id=original['id'])
        self.assertEqual(self.totals()['total'],Decimal('100'))
        self.reject(lambda:adjustment(self.c,self.f,reverses_id=original['id']),'23505')

    def test_adjustment_cannot_reference_other_account_item(self):
        acc=insert(self.c,'accounts',establishment_id=self.f['e']['id'],opened_by=self.f['u']['id'])
        self.reject(lambda:insert(self.c,'account_adjustments',establishment_id=self.f['e']['id'],
            account_id=acc['id'],order_item_id=self.f['i']['id'],actor_id=self.f['u']['id'],
            kind='ACRESCIMO',amount='10',reason='Ajuste'))

    def test_partial_reversals_do_not_exceed_original(self):
        pay=payment(self.c,self.f,'40')
        reversal(self.c,self.f,pay,'30')
        self.reject(lambda:reversal(self.c,self.f,pay,'11'))
        self.assertEqual(self.totals()['paid_amount'],Decimal('10'))

    def test_history_update_delete_and_truncate_rejected(self):
        pay=payment(self.c,self.f,'10')
        self.reject(lambda:self.c.execute('UPDATE payments SET applied_amount=1 WHERE id=%s',(pay['id'],)))
        self.reject(lambda:self.c.execute('DELETE FROM payments WHERE id=%s',(pay['id'],)))
        self.reject(lambda:self.c.execute('TRUNCATE audit_events'))

    def test_closure_difference_and_closed_cash_rejects_values(self):
        payment(self.c,self.f,'20')
        closure=close_session(self.c,self.f)
        self.c.execute('SET CONSTRAINTS ALL IMMEDIATE')
        self.reject(lambda:payment(self.c,self.f,'10'))
        difference=self.c.execute('SELECT sum(difference_amount) AS n FROM cash_closure_lines WHERE closure_id=%s',(closure['id'],)).fetchone()
        self.assertEqual(difference['n'],0)
        self.reject(lambda:self.c.execute("UPDATE cash_sessions SET status='ABERTA',closed_at=NULL WHERE id=%s",(self.f['sess']['id'],)))

    def test_closure_requires_all_methods_and_real_expected(self):
        self.reject(lambda:self.c.execute("UPDATE cash_sessions SET status='FECHADA',closed_at=CURRENT_TIMESTAMP WHERE id=%s",(self.f['sess']['id'],)))
        def wrong():
            c=close_session(self.c,self.f)
            # Linha já imutável: erro antes de permitir adulteração do esperado.
            self.c.execute("UPDATE cash_closure_lines SET expected_amount=999 WHERE closure_id=%s",(c['id'],))
        self.reject(wrong)

    def test_refund_can_use_later_cash_session_without_double_counting(self):
        pay=payment(self.c,self.f,'40',method='DINHEIRO')
        close_session(self.c,self.f)
        later=second_session(self.c,self.f)
        reversal(self.c,self.f,pay,'10',session=later['id'])
        rows=self.c.execute("SELECT cash_session_id,expected_amount FROM cash_session_totals WHERE establishment_id=%s AND method='DINHEIRO'",(self.f['e']['id'],)).fetchall()
        totals={r['cash_session_id']:r['expected_amount'] for r in rows}
        self.assertEqual(totals[self.f['sess']['id']],Decimal('140'))
        self.assertEqual(totals[later['id']],Decimal('-10'))

    def test_production_can_progress_after_payment_and_cannot_regress(self):
        payment(self.c,self.f,'100')
        self.c.execute("UPDATE accounts SET status='FECHADA',closed_at=CURRENT_TIMESTAMP WHERE id=%s",(self.f['acc']['id'],))
        self.c.execute("UPDATE order_items SET status='ENTREGUE',version=version+1 WHERE id=%s",(self.f['i']['id'],))
        self.c.execute('SET CONSTRAINTS ALL IMMEDIATE')
        self.reject(lambda:self.c.execute("UPDATE order_items SET status='ENVIADO' WHERE id=%s",(self.f['i']['id'],)))

    def test_prepared_cancellation_event_requires_stock_disposition(self):
        self.reject(lambda:insert(self.c,'production_events',establishment_id=self.f['e']['id'],
            order_item_id=self.f['i']['id'],actor_id=self.f['u']['id'],from_status='EM_PREPARO',
            to_status='CANCELADO',reason='Devolução'))

    def test_idempotency_identity_result_and_financial_retention(self):
        def idem(**kwargs):
            return insert(self.c,'idempotency_records',establishment_id=self.f['e']['id'],user_id=self.f['u']['id'],
                operation='payment.create',key='same-key',request_hash='a'*64,**kwargs)
        row=idem(status='CONCLUIDO',response_code=201,response_body='{}',resource_type='payment',resource_id=uuid4())
        self.reject(lambda:idem(),'23505')
        self.reject(lambda:self.c.execute('DELETE FROM idempotency_records WHERE id=%s',(row['id'],)))
        self.reject(lambda:self.c.execute("UPDATE idempotency_records SET request_hash=%s WHERE id=%s",('b'*64,row['id'])))

    def test_outbox_rollback_and_payload_immutability(self):
        eid=uuid4()
        with self.c.transaction(force_rollback=True):
            insert(self.c,'outbox_events',id=eid,establishment_id=self.f['e']['id'],aggregate_type='account',
                aggregate_id=self.f['acc']['id'],event_type='account.updated',payload='{}')
            payment(self.c,self.f,'10')
        self.assertIsNone(self.c.execute('SELECT 1 FROM outbox_events WHERE id=%s',(eid,)).fetchone())
        self.assertEqual(self.totals()['paid_amount'],0)
        ev=insert(self.c,'outbox_events',establishment_id=self.f['e']['id'],aggregate_type='account',
            aggregate_id=self.f['acc']['id'],event_type='account.updated',payload='{}')
        self.c.execute('UPDATE outbox_events SET attempts=attempts+1 WHERE id=%s',(ev['id'],))
        self.reject(lambda:self.c.execute("UPDATE outbox_events SET payload='{}'::jsonb||'{\"changed\":true}'::jsonb WHERE id=%s",(ev['id'],)))

    def test_presence_revocation_block_and_custom_limits(self):
        dev=insert(self.c,'devices',establishment_id=self.f['e']['id'],label='Garçom 1',app_version='test')
        sess=self.c.execute('''INSERT INTO device_sessions(establishment_id,device_id,user_id,created_at,last_seen_at,expires_at)
            VALUES(%s,%s,%s,CURRENT_TIMESTAMP-interval '1 day',CURRENT_TIMESTAMP-interval '4 minutes',
            CURRENT_TIMESTAMP+interval '1 day') RETURNING id''',(self.f['e']['id'],dev['id'],self.f['u']['id'])).fetchone()
        def presence():return self.c.execute('SELECT presence FROM device_presence WHERE session_id=%s',(sess['id'],)).fetchone()['presence']
        self.assertEqual(presence(),'INSTAVEL')
        self.c.execute('UPDATE device_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE id=%s',(sess['id'],))
        self.assertEqual(presence(),'OFFLINE')
        self.c.execute('UPDATE devices SET blocked_at=CURRENT_TIMESTAMP,blocked_by=%s WHERE id=%s',(self.f['u']['id'],dev['id']))
        self.assertEqual(presence(),'BLOQUEADO')

    def test_unsupported_financial_isolation_is_rejected(self):
        with self.assertRaises(psycopg.Error) as caught:
            with connect() as c:
                c.execute('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ')
                fixture(c)
        self.assertEqual(caught.exception.sqlstate,'0A000')

    def competing_transaction(self, first, second, seed_setup=None):
        # Dados confirmados apenas no banco descartável criado pelo runner.
        with connect() as seed:
            f=fixture(seed)
            f['other_session']=second_session(seed,f)
            if seed_setup:seed_setup(seed,f)
        c1=connect()
        ready=Event(); shared={}
        def worker():
            try:
                with connect() as c2:
                    shared['pid']=c2.info.backend_pid;ready.set()
                    second(c2,f)
                return 'committed'
            except psycopg.Error as exc:return exc.sqlstate
        try:
            first(c1,f)
            with ThreadPoolExecutor(max_workers=1) as pool:
                future=pool.submit(worker)
                self.assertTrue(ready.wait(3))
                blocked=False
                deadline=time.monotonic()+3
                while time.monotonic()<deadline:
                    row=self.c.execute('SELECT cardinality(pg_blocking_pids(%s)) AS n',(shared['pid'],)).fetchone()
                    if row['n']>0:blocked=True;break
                    time.sleep(.02)
                # Sempre liberar antes de aguardar a thread, inclusive em falhas.
                c1.commit()
                self.assertTrue(blocked,'A segunda transação não disputou o lock esperado')
                self.assertEqual(future.result(timeout=6),'23514')
        finally:
            c1.rollback();c1.close()

    def test_concurrent_payments_do_not_exceed_balance(self):
        self.competing_transaction(lambda c,f:payment(c,f,'80'),
            lambda c,f:payment(c,f,'80',session=f['other_session']['id']))

    def test_concurrent_refunds_do_not_exceed_original(self):
        def seed(c,f):f['original_payment']=payment(c,f,'80')
        self.competing_transaction(lambda c,f:reversal(c,f,f['original_payment'],'60'),
            lambda c,f:reversal(c,f,f['original_payment'],'30',session=f['other_session']['id']),seed_setup=seed)

    def test_payment_waits_for_cash_closure_and_is_rejected(self):
        self.competing_transaction(lambda c,f:close_session(c,f),lambda c,f:payment(c,f,'10'))

    def test_new_order_waits_for_account_closing_and_is_rejected(self):
        self.competing_transaction(lambda c,f:c.execute("UPDATE accounts SET status='EM_FECHAMENTO' WHERE id=%s",(f['acc']['id'],)),
            lambda c,f:insert(c,'orders',establishment_id=f['e']['id'],account_id=f['acc']['id'],waiter_id=f['u']['id']))


if __name__=='__main__':
    unittest.main(verbosity=2)
