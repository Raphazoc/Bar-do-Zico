"""Create the first empty Azure pilot establishment and administrator.

All connection details and the administrator password are supplied through
environment variables. This script is intended to run once after migrations,
with a privileged database connection, and never runs as part of the API.
"""
import os
import re
import uuid

import psycopg
from argon2 import PasswordHasher


def required(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"Defina {name} no ambiente.")
    return value


def main() -> None:
    database_url = required("AZURE_ADMIN_DATABASE_URL")
    # Um ID obrigatório evita criar outra unidade silenciosamente a cada execução.
    unit_id = uuid.UUID(required("BAR_UNIT_ID"))
    admin_name = required("BAR_ADMIN_NAME")
    admin_username = required("BAR_ADMIN_USERNAME").lower()
    if not re.fullmatch(r"[a-z0-9][a-z0-9._-]{1,39}", admin_username):
        raise RuntimeError("BAR_ADMIN_USERNAME deve ter de 2 a 40 letras minúsculas, números, ponto, hífen ou sublinhado.")
    admin_password = required("BAR_ADMIN_PASSWORD")
    if not 6 <= len(admin_password) <= 128:
        raise RuntimeError("BAR_ADMIN_PASSWORD deve ter de 6 a 128 caracteres.")
    unit_name = required("BAR_UNIT_NAME")

    password_hash = PasswordHasher().hash(admin_password)
    admin_id = uuid.uuid4()

    with psycopg.connect(database_url) as connection:
        with connection.cursor() as cursor:
            # Serializa a primeira criação mesmo quando ainda não existe a linha da unidade.
            cursor.execute("SELECT pg_advisory_xact_lock(hashtextextended(%s, 0))", ("bar-bootstrap:" + str(unit_id),))
            cursor.execute(
                "SELECT id FROM bar.establishments WHERE id = %s",
                (unit_id,),
            )
            if cursor.fetchone() is None:
                cursor.execute(
                    "INSERT INTO bar.establishments(id, name) VALUES (%s, %s)",
                    (unit_id, unit_name),
                )

            cursor.execute(
                "SELECT id,active,deleted_at FROM bar.users WHERE establishment_id = %s AND username = %s FOR UPDATE",
                (unit_id, admin_username),
            )
            row = cursor.fetchone()
            if row is None:
                cursor.execute(
                    """
                    INSERT INTO bar.users
                        (id, establishment_id, name, username, password_hash)
                    VALUES (%s, %s, %s, %s, %s)
                    """,
                    (admin_id, unit_id, admin_name, admin_username, password_hash),
                )
            else:
                if not row[1] or row[2] is not None:
                    raise RuntimeError("O administrador existente está desativado ou excluído; seu cadastro foi preservado.")
                admin_id = row[0]

            cursor.execute(
                "SELECT id FROM bar.roles WHERE establishment_id = %s AND name = 'Administrador'",
                (unit_id,),
            )
            role = cursor.fetchone()
            if role is None:
                role_id = uuid.uuid4()
                cursor.execute(
                    "INSERT INTO bar.roles(id, establishment_id, name) VALUES (%s, %s, 'Administrador')",
                    (role_id, unit_id),
                )
            else:
                role_id = role[0]

            cursor.execute(
                """
                INSERT INTO bar.user_roles(id, establishment_id, user_id, role_id)
                VALUES (%s, %s, %s, %s)
                ON CONFLICT (establishment_id, user_id, role_id) DO NOTHING
                """,
                (uuid.uuid4(), unit_id, admin_id, role_id),
            )
            cursor.execute(
                """
                INSERT INTO bar.role_permissions(id, establishment_id, role_id, permission_id)
                SELECT gen_random_uuid(), %s, %s, id
                FROM bar.permissions
                ON CONFLICT (establishment_id, role_id, permission_id) DO NOTHING
                """,
                (unit_id, role_id),
            )
        connection.commit()

    print(f"Azure pilot bootstrap complete: unit={unit_id}")


if __name__ == "__main__":
    main()
