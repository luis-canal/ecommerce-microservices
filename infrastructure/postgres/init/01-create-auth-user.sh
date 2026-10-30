
#!/bin/sh

set -eu

psql \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=ON_ERROR_STOP=1 \
  --set=app_password="$POSTGRES_AUTH_APP_PASSWORD" <<'SQL'

CREATE ROLE auth_app
    LOGIN
    PASSWORD :'app_password';

GRANT CONNECT ON DATABASE db_user TO auth_app;

GRANT USAGE, CREATE ON SCHEMA public TO auth_app;

SQL
