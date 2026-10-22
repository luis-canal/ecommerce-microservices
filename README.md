# OMA — E-commerce Acadêmico de Artigos Gaúchos

Repositório de microsserviços Java/Spring para e-commerce. O build Maven da raiz agrega os módulos e centraliza as versões comuns.

## Módulos

- `auth-api` — autenticação e usuários.
- `product-api` — catálogo de produtos.
- `currency-api` — moedas e cotações PTAX.
- `gateway-api` — gateway reativo para os serviços.
- `commons` — módulo compartilhado, reservado para contratos e autoconfiguração comum.

## Requisitos

- JDK 21 (todos os módulos compilam com Java 21).
- Acesso à internet na primeira execução do Maven Wrapper para baixar Maven e dependências.
- Para executar os serviços: PostgreSQL e Consul local, com configurações compatíveis com o serviço escolhido. A configuração atual dos serviços ainda depende de valores no Consul; o arquivo `start_consul.bat` é o exemplo de cadastro no Windows. Não use credenciais de desenvolvimento em ambientes compartilhados ou produção.

Os Dockerfiles dos serviços executáveis usam imagens runtime Eclipse Temurin JRE 21. Empacote o módulo antes de executar `docker build` em seu diretório.

## Build e testes

A partir da raiz do repositório, o wrapper delega para o Maven Wrapper do auth-api. Todos os wrappers usam Maven 3.10.0:

```bash
./mvnw clean verify
```

Para compilar um serviço e suas dependências no reactor:

```bash
./mvnw -pl currency-api -am clean verify
```

Substitua `currency-api` por `auth-api`, `product-api` ou `gateway-api` para validar outro módulo. Para iniciar, por exemplo, o currency-api depois de configurar PostgreSQL e Consul:

```bash
./mvnw -pl currency-api spring-boot:run
```

Também é possível executar o wrapper dentro de cada diretório de serviço. O parent está referenciado relativamente à raiz do repositório.

O parent usa Spring Boot 4.1.1 e Spring Cloud 2025.1.3. Essa combinação é compatível: a matriz oficial do Spring Cloud 2025.1.x suporta Spring Boot 4.1.x a partir da versão 2025.1.2. As versões compartilhadas são centralizadas no POM raiz.

## Execução local do currency-api

1. Inicie PostgreSQL e crie/configure o banco `db_currency` com as credenciais utilizadas pela aplicação.
2. Inicie Consul em modo de desenvolvimento. Em outro terminal, cadastre as propriedades do serviço conforme `start_consul.bat`; no Linux, use `consul kv put` com as chaves de `config/currency-api/`.
3. Confirme no Consul a URL PTAX e a data de cotação configuradas. A aplicação também usa as propriedades de cache e resiliência cadastradas para `currency-api`.
4. Rode `./mvnw -pl currency-api spring-boot:run` com JDK 21.
5. Teste `GET http://localhost:8100/currencies?source=USD&target=BRL`.

O Flyway aplica as migrações do próprio serviço no startup. As configurações locais existentes e os endpoints podem mudar conforme os valores cadastrados no Consul.

## Contratos e migrações

As convenções para valores monetários, datas, paginação, erros, caminhos, cabeçalhos e perfis estão em [docs/api-conventions.md](docs/api-conventions.md).

Migrações Flyway que já foram aplicadas são imutáveis: **não altere V1 ou V2**. Toda evolução de schema deve ser adicionada como uma nova migração, começando em V3, para evitar incompatibilidades de checksum.

## Consul no Windows

`start_consul.bat` contém comandos de desenvolvimento para popular o Consul. Revise as configurações e credenciais para seu ambiente antes de usá-lo; nunca reutilize valores locais em produção.

## Licença e repositório

Projeto acadêmico: https://github.com/luis-canal/ecommerce-microservices
