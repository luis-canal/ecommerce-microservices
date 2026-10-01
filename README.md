# OMA — E-commerce Acadêmico de Artigos Gaúchos

Repositório acadêmico com os microsserviços `product-api` e `currency-api`, usando Spring Boot, PostgreSQL, Flyway e HashiCorp Consul para configuração externa.

## Status atual

- `product-api` está presente e utiliza a porta `8000`.
- `currency-api` está presente e foi iniciado com JDK 21 na porta `8100`.
- O `currency-api` consulta configurações no Consul local e usa PostgreSQL no banco `db_currency`.
- As migrações Flyway V1 e V2 do `currency-api` foram validadas e executadas.
- O endpoint `GET /currencies?source=USD&target=BRL` foi testado e retornou HTTP 200, taxa `5.15` e o campo `environment` com a porta `8100`.
- O script `start_consul.bat` cadastra configurações dos dois microsserviços no Consul para uso local em Windows.

> O teste acima confirma uma execução local; os serviços precisam estar iniciados para que o endpoint responda. O Consul em modo de desenvolvimento mantém os dados apenas enquanto sua instância estiver ativa.

## Requisitos

- JDK 21
- PostgreSQL em execução
- HashiCorp Consul instalado e acessível na porta `8500`
- Bancos PostgreSQL `db_currency` e, para executar o `product-api`, `db_product`

O usuário, a senha e as URLs do PostgreSQL precisam corresponder às configurações cadastradas no Consul. O script `.bat` deste repositório usa o usuário `postgres` e a senha local `postgres`; ajuste-o se as credenciais do seu PostgreSQL forem diferentes. Não reutilize essas credenciais de desenvolvimento em produção.

## Iniciar localmente no Linux

### 1. Inicie o Consul

Em um terminal, inicie o agente de desenvolvimento e deixe-o aberto:

```bash
consul agent -dev -client=127.0.0.1
```

Em outro terminal, cadastre as configurações do `currency-api` (altere a senha se necessário):

```bash
consul kv put config/currency-api/server.port 8100
consul kv put config/currency-api/spring.datasource.url jdbc:postgresql://localhost/db_currency
consul kv put config/currency-api/spring.datasource.username postgres
consul kv put config/currency-api/spring.datasource.password postgres
```

O arquivo `currency-api/src/main/resources/application.properties` configura a conexão ao Consul local e importa essas propriedades. O script `start_consul.bat` contém também as chaves de configuração do `product-api`.

### 2. Inicie o `currency-api`

Com Java 21 selecionado e o PostgreSQL ativo, a partir da raiz do repositório:

```bash
cd currency-api
./mvnw spring-boot:run
```

O Flyway aplica automaticamente as migrações em `currency-api/src/main/resources/db/migration/` ao iniciar a aplicação.

### 3. Teste o endpoint

Em outro terminal:

```bash
curl -i 'http://localhost:8100/currencies?source=USD&target=BRL'
```

Resposta esperada: HTTP 200, com `sourceCurrency`, `targetCurrency`, `conversionRate` e `environment`. A migração inicial fornece o par USD → BRL. Um par não cadastrado retorna HTTP 404.

## Estrutura

```text
product-api/     Microsserviço de produtos (porta 8000)
currency-api/    Microsserviço de moedas (porta 8100)
start_consul.bat Inicialização do Consul e cadastro das configurações (Windows)
```

## Repositório

https://github.com/luis-canal/ecommerce-microservices