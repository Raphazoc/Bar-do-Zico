SET search_path TO bar, pg_catalog;

INSERT INTO permissions(code, description)
VALUES ('table.create', 'Cadastrar novas mesas');

-- Quem já administra mesas ou abre atendimentos recebe o cadastro de novas mesas.
-- Edição, exclusão e administração dos locais continuam em table.manage.
INSERT INTO role_permissions(establishment_id, role_id, permission_id)
SELECT DISTINCT rp.establishment_id, rp.role_id, created.id
FROM role_permissions rp
JOIN permissions previous ON previous.id = rp.permission_id
CROSS JOIN permissions created
WHERE previous.code IN ('table.manage', 'account.open') AND created.code = 'table.create'
ON CONFLICT (establishment_id, role_id, permission_id) DO NOTHING;
