#!/bin/sh

set -eu

echo "=========================================="
echo " Carregando configurações no Consul"
echo "=========================================="

# Aguarda o Consul responder à API HTTP.
until consul members >/dev/null 2>&1; do
    echo "Aguardando o Consul..."
    sleep 2
done

# Função auxiliar para cadastrar uma configuração.
put() {
    consul kv put "$1" "$2"
}

# ==========================================================
# CONFIGURAÇÕES GERAIS - TODAS AS APLICAÇÕES
# ==========================================================

put "config/application/management.endpoints.web.exposure.include" "*"
put "config/application/management.endpoint.health.show-components" "always"
put "config/application/management.endpoint.health.show-details" "always"
put "config/application/management.health.livenessstate.enabled" "true"
put "config/application/management.health.readinessstate.enabled" "true"
put "config/application/management.health.db.enabled" "true"


# ==========================================================
# PRODUCT API
# ==========================================================

put "config/product-api/server.port" "8000"

# Banco de dados
put "config/product-api/spring.datasource.url" \
    "jdbc:postgresql://product-db:5432/db_product"
put "config/product-api/spring.datasource.username" "postgres"
put "config/product-api/spring.datasource.password" "postgres"

# Mensagem de promoção
put "config/product-api/app.promotion.message" \
    "Promoção Semana do Cliente"

# Mensagem em inglês
put "config/product-api,en/app.promotion.message" \
    "Christmas Promotion!!!!"

# Retry - Currency API
put "config/product-api/resilience4j.retry.instances.currency-api.max-attempts" "3"
put "config/product-api/resilience4j.retry.instances.currency-api.wait-duration" "1s"

# Circuit Breaker - Currency API
put "config/product-api/resilience4j.circuitbreaker.instances.currency-api.sliding-window-type" "COUNT_BASED"
put "config/product-api/resilience4j.circuitbreaker.instances.currency-api.sliding-window-size" "5"
put "config/product-api/resilience4j.circuitbreaker.instances.currency-api.minimum-number-of-calls" "5"
put "config/product-api/resilience4j.circuitbreaker.instances.currency-api.failure-rate-threshold" "50"
put "config/product-api/resilience4j.circuitbreaker.instances.currency-api.wait-duration-in-open-state" "100s"

# Exceção ignorada pelo Circuit Breaker
put "config/product-api/resilience4j.circuitbreaker.instances.currency-api.ignore-exceptions" "io.github.resilience4j.circuitbreaker.CallNotPermittedException"

# Cache - Currency API
put "config/product-api/spring.cache.type" "caffeine"
put "config/product-api/spring.cache.cache-names" "currency-api"
put "config/product-api/spring.cache.caffeine.spec" "maximumSize=1000,expireAfterWrite=10m"

# Ordem dos aspectos
put "config/product-api/resilience4j.retry.retry-aspect-order" "100"
put "config/product-api/resilience4j.circuitbreaker.circuit-breaker-aspect-order" "101"


# ==========================================================
# CURRENCY API
# ==========================================================

put "config/currency-api/server.port" "8100"

# Banco de dados
put "config/currency-api/spring.datasource.url" \
    "jdbc:postgresql://currency-db:5432/db_currency"
put "config/currency-api/spring.datasource.username" "postgres"
put "config/currency-api/spring.datasource.password" "postgres"

# Retry - BCB
put "config/currency-api/resilience4j.retry.instances.bcb-currency.max-attempts" "3"
put "config/currency-api/resilience4j.retry.instances.bcb-currency.wait-duration" "1s"

put "config/currency-api/app.currency.bcb.quote-date" "2025-10-10"
put "config/currency-api/app.currency.bcb.connect-timeout" "2s"
put "config/currency-api/app.currency.bcb.read-timeout" "5s"

# Circuit Breaker - BCB
put "config/currency-api/resilience4j.circuitbreaker.instances.bcb-currency.sliding-window-type" "COUNT_BASED"
put "config/currency-api/resilience4j.circuitbreaker.instances.bcb-currency.sliding-window-size" "5"
put "config/currency-api/resilience4j.circuitbreaker.instances.bcb-currency.minimum-number-of-calls" "5"
put "config/currency-api/resilience4j.circuitbreaker.instances.bcb-currency.failure-rate-threshold" "50"
put "config/currency-api/resilience4j.circuitbreaker.instances.bcb-currency.wait-duration-in-open-state" "100s"

# Exceção ignorada pelo Circuit Breaker
put "config/currency-api/resilience4j.circuitbreaker.instances.bcb-currency.ignore-exceptions" "io.github.resilience4j.circuitbreaker.CallNotPermittedException"

# Cache - BCB
put "config/currency-api/spring.cache.type" "caffeine"
put "config/currency-api/spring.cache.cache-names" "bcb-currency"
put "config/currency-api/spring.cache.caffeine.spec" "maximumSize=1000,expireAfterWrite=10m"

# Ordem dos aspectos
put "config/currency-api/resilience4j.retry.retry-aspect-order" "100"
put "config/currency-api/resilience4j.circuitbreaker.circuit-breaker-aspect-order" "101"

put "config/currency-api/app.currency.bcb.base-url" "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata"
put "config/currency-api/app.currency.bcb.quote-date" "2025-10-10"
put "config/currency-api/app.currency.bcb.connect-timeout" "2s"
put "config/currency-api/app.currency.bcb.read-timeout" "5s"



# ==========================================================
# AUTH API
# ==========================================================

SECRET_FILE="/run/secrets/auth_internal_secret"
if [ ! -s "$SECRET_FILE" ]; then
    umask 077
    generated_secret="$(consul keygen)"
    if [ "${#generated_secret}" -lt 32 ]; then
        echo "Não foi possível gerar um segredo interno válido" >&2
        exit 1
    fi
    printf '%s' "$generated_secret" > "$SECRET_FILE"
fi

AUTH_INTERNAL_SECRET="$(cat "$SECRET_FILE")"
if [ "${#AUTH_INTERNAL_SECRET}" -lt 32 ]; then
    echo "O segredo interno persistido é inválido" >&2
    exit 1
fi

SERVICE_AUTH_SECRET_FILE="/run/secrets/service_auth_token"
if [ ! -s "$SERVICE_AUTH_SECRET_FILE" ]; then
    umask 077
    generated_secret="$(consul keygen)"
    if [ "${#generated_secret}" -lt 32 ]; then
        echo "Não foi possível gerar uma credencial serviço-a-serviço válida" >&2
        exit 1
    fi
    printf '%s' "$generated_secret" > "$SERVICE_AUTH_SECRET_FILE"
fi

SERVICE_AUTH_TOKEN="$(cat "$SERVICE_AUTH_SECRET_FILE")"
if [ "${#SERVICE_AUTH_TOKEN}" -lt 32 ]; then
    echo "A credencial serviço-a-serviço persistida é inválida" >&2
    exit 1
fi

GATEWAY_DOWNSTREAM_SECRET_FILE="/run/secrets/gateway_downstream_token"
if [ ! -s "$GATEWAY_DOWNSTREAM_SECRET_FILE" ]; then
    umask 077
    generated_secret="$(consul keygen)"
    if [ "${#generated_secret}" -lt 32 ]; then
        echo "Não foi possível gerar uma credencial do Gateway válida" >&2
        exit 1
    fi
    printf '%s' "$generated_secret" > "$GATEWAY_DOWNSTREAM_SECRET_FILE"
fi

GATEWAY_DOWNSTREAM_TOKEN="$(cat "$GATEWAY_DOWNSTREAM_SECRET_FILE")"
if [ "${#GATEWAY_DOWNSTREAM_TOKEN}" -lt 32 ]; then
    echo "A credencial do Gateway persistida é inválida" >&2
    exit 1
fi

put "config/auth-api/server.port" "8200"
put "config/auth-api/app.auth.internal-secret" "$AUTH_INTERNAL_SECRET"

# O gateway utiliza o mesmo segredo ao consultar a validação interna do auth-api.
put "config/gateway-api/app.auth.internal-secret" "$AUTH_INTERNAL_SECRET"
put "config/gateway-api/app.auth.internal-url" "http://auth-api:8200"

# Banco de dados
put "config/auth-api/spring.datasource.url" "jdbc:postgresql://auth-db:5432/db_user"
put "config/auth-api/spring.datasource.username" "postgres"
put "config/auth-api/spring.datasource.password" "postgres"

# Credenciais compartilhadas para chamadas internas.
put "config/product-api/inventory.security.service-token" "$SERVICE_AUTH_TOKEN"
put "config/inventory-api/inventory.security.service-token" "$SERVICE_AUTH_TOKEN"
put "config/gateway-api/gateway.security.service-token" "$SERVICE_AUTH_TOKEN"

put "config/inventory-api/inventory.security.gateway-token" "$GATEWAY_DOWNSTREAM_TOKEN"
put "config/gateway-api/gateway.security.inventory-downstream-token" "$GATEWAY_DOWNSTREAM_TOKEN"


# ==========================================================
# INVENTORY API
# ==========================================================

put "config/inventory-api/server.port" "8003"

# Banco de dados
put "config/inventory-api/spring.datasource.url" \
    "jdbc:postgresql://inventory-db:5432/db_inventory"
put "config/inventory-api/spring.datasource.username" "postgres"
put "config/inventory-api/spring.datasource.password" "postgres"

# Integração com Product API e expiração das reservas.
put "config/inventory-api/inventory.clients.product-api.base-url" "http://product-api"
put "config/inventory-api/inventory.reservations.expiration-scan-ms" "30000"


# ==========================================================
# ORDER API
# ==========================================================

put "config/order-api/server.port" "8300"

# Banco de dados
put "config/order-api/spring.datasource.url" \
    "jdbc:postgresql://order-db:5432/db_order"
put "config/order-api/spring.datasource.username" "postgres"
put "config/order-api/spring.datasource.password" "postgres"

# ==========================================================
# FINALIZAÇÃO
# ==========================================================

echo "=========================================="
echo " Configurações carregadas com sucesso!"
echo "=========================================="