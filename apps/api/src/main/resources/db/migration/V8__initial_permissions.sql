SET search_path TO bar, pg_catalog;

-- Somente catálogo global: sem usuário, senha ou unidade de demonstração.
INSERT INTO permissions (code, description) VALUES
('user.manage', 'Administrar usuários e perfis'),
('catalog.manage', 'Administrar catálogo e disponibilidade'),
('table.manage', 'Administrar salão e autorizações'),
('account.open', 'Abrir contas'),
('account.adjust', 'Aplicar descontos, serviço e acréscimos'),
('account.close', 'Encerrar contas quitadas'),
('account.reopen', 'Reabrir conta com auditoria'),
('order.create', 'Criar pedido em mesa autorizada'),
('order.cancel', 'Cancelar item com motivo'),
('production.update', 'Atualizar preparo e entrega'),
('payment.receive', 'Registrar recebimento'),
('payment.reverse', 'Estornar recebimento com autorização'),
('cash.open', 'Abrir turno de caixa'),
('cash.move', 'Registrar suprimento ou sangria'),
('cash.close', 'Conferir e fechar turno de caixa'),
('device.revoke', 'Bloquear dispositivo e revogar sessão'),
('audit.view', 'Consultar auditoria'),
('report.view', 'Consultar relatórios'),
('stock.adjust', 'Ajustar estoque, reservado para fase 2');
