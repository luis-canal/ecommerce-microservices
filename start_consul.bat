@echo off
echo Iniciando Consul...
start "Consul" consul agent -dev
echo Aguardando Consul iniciar...
timeout /t 10 /nobreak > nul
echo Carregando configuracoes...

consul kv put config/product-api/server.port "8000"
consul kv put config/product-api/spring.datasource.url "jdbc:postgresql://localhost/db_product"
consul kv put config/product-api/spring.datasource.username "postgres"
consul kv put config/product-api/spring.datasource.password "canal222"

consul kv put config/product-api/app.promotion.message "Customer Week Promotion"
consul kv put config/product-api,it/app.promotion.message "Promozione Settimana del Cliente"

consul kv put config/currency-api/server.port "8100"
consul kv put config/currency-api/spring.datasource.url "jdbc:postgresql://localhost/db_currency"
consul kv put config/currency-api/spring.datasource.username "postgres"
consul kv put config/currency-api/spring.datasource.password "canal222"

REM Configuracao para todos os microservicos
consul kv put config/application/management.endpoints.web.exposure.include "*"
consul kv put config/application/management.endpoint.health.show-components "always"
consul kv put config/application/management.endpoint.health.show-details "always"
consul kv put config/application/management.health.livenessstate.enabled "true"
consul kv put config/application/management.health.readinessstate.enabled "true"
consul kv put config/application/management.health.db.enabled "true"

REM Configuracao do Retry
consul kv put config/product-api/resilience4j.retry.instances.currency-api.max-attempts "3"
consul kv put config/product-api/resilience4j.retry.instances.currency-api.wait-duration "1s"

REM Configuracao do Circuit Breaker
consul kv put config/product-api/resilience4j.circuitbreaker.instances.currency-api.sliding-window-type "COUNT_BASED"
consul kv put config/product-api/resilience4j.circuitbreaker.instances.currency-api.sliding-window-size "5"
consul kv put config/product-api/resilience4j.circuitbreaker.instances.currency-api.minimum-number-of-calls "5"
consul kv put config/product-api/resilience4j.circuitbreaker.instances.currency-api.failure-rate-threshold "50"
consul kv put config/product-api/resilience4j.circuitbreaker.instances.currency-api.wait-duration-in-open-state "100s"

REM Nao repetir chamadas quando o Circuit Breaker estiver aberto
REM O Consul nao permite colchetes em nomes de chaves; lista com um item no valor
consul kv put config/product-api/resilience4j.retry.instances.currency-api.ignore-exceptions "io.github.resilience4j.circuitbreaker.CallNotPermittedException"

REM Configuracao do cache utilizando Caffeine
consul kv put config/product-api/spring.cache.type "caffeine"
consul kv put config/product-api/spring.cache.cache-names "currency-api"
consul kv put config/product-api/spring.cache.caffeine.spec "maximumSize=1000,expireAfterWrite=10m"

REM Ordem de execucao dos aspectos de resiliencia e cache
consul kv put config/product-api/resilience4j.retry.retry-aspect-order "100"
consul kv put config/product-api/resilience4j.circuitbreaker.circuit-breaker-aspect-order "101"

echo.
echo Consul iniciado e configuracoes carregadas!
pause