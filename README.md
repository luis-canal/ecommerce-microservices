# OMA — E-commerce de Artigos Gaúchos

Projeto acadêmico de um **e-commerce de artigos gaúchos**, desenvolvido com arquitetura de microsserviços utilizando Java e Spring Boot.

A aplicação é composta por quatro serviços independentes: autenticação, catálogo de produtos, conversão de moedas e API Gateway. Os serviços utilizam PostgreSQL para persistência, Flyway para versionamento do banco de dados e integração HTTP para comunicação entre aplicações.

O objetivo é construir uma base de e-commerce distribuída, explorando conceitos de desenvolvimento backend, APIs REST, autenticação, persistência de dados e comunicação entre microsserviços.

## Arquitetura

O projeto é organizado em quatro microsserviços:

| Serviço | Responsabilidade |
|---|---|
| `gateway-api` | Ponto de entrada da aplicação, roteamento e validação de JWT nas rotas protegidas. |
| `auth-api` | Cadastro de usuários, autenticação e emissão de tokens JWT. |
| `product-api` | Consulta, paginação e cadastro de produtos. |
| `currency-api` | Consulta de taxas cambiais e conversão de moedas. |

### Fluxo de comunicação

As requisições externas são recebidas pelo Gateway, que encaminha as chamadas ao microsserviço responsável.

O catálogo também se comunica com o serviço de moedas quando precisa apresentar um produto em uma moeda diferente da original.

```text
                    CLIENTE
                       |
                       v
                 GATEWAY API
                       |
          +------------+------------+
          |            |            |
          v            v            v
       AUTH API    PRODUCT API   CURRENCY API
                       |            |
                       +------------+
                            |
                            v
                       BANCO CENTRAL
                         (PTAX)
```

Cada microsserviço possui sua própria responsabilidade e configuração de persistência.

## Tecnologias utilizadas

| Tecnologia | Aplicação |
|---|---|
| Java 21 | Linguagem de programação |
| Spring Boot | Desenvolvimento dos microsserviços |
| Spring Web | Construção das APIs REST |
| Spring Cloud Gateway | Roteamento das requisições |
| Spring Security / JWT | Autenticação e proteção de rotas |
| Spring Data JPA | Persistência e acesso aos dados |
| PostgreSQL | Banco de dados relacional |
| Flyway | Versionamento e migração do banco |
| HashiCorp Consul | Descoberta de serviços e configuração externa |
| BCrypt | Criptografia de senhas |
| Resilience4j | Retry e Circuit Breaker |
| Caffeine | Cache de consultas |
| Maven | Gerenciamento de dependências e build |
| Postman | Testes e validação das APIs |

## Funcionalidades implementadas

### 1. Autenticação — `auth-api`

Responsável pelo gerenciamento de usuários e autenticação.

- Cadastro de usuários com nome, e-mail e senha.
- Validação de dados e prevenção de e-mails duplicados.
- Armazenamento de senhas utilizando BCrypt.
- Autenticação por e-mail e senha.
- Emissão de tokens JWT com validade de 24 horas.
- Identificação do usuário por ID, e-mail e tipo de usuário.
- Usuário administrador inicial inserido por migração do banco de dados.

**Principais endpoints:**

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/auth/signup` | Cadastro de usuário |
| POST | `/auth/signin` | Autenticação |

Também existem rotas equivalentes sob o prefixo `/ws/auth`.

### 2. Catálogo de produtos — `product-api`

Responsável pela consulta e pelo cadastro de produtos.

- Consulta de produto por identificador.
- Listagem paginada de produtos.
- Ordenação dos resultados.
- Conversão de preços para outras moedas.
- Cadastro de produtos restrito a administradores.
- Validação de campos obrigatórios e preços positivos.
- Carregamento de dados iniciais por migrações Flyway.

**Principais endpoints:**

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/products` | Lista produtos com paginação |
| GET | `/products/{id}` | Consulta produto por ID |
| POST | `/ws/products` | Cadastra produto (administrador) |

A listagem utiliza, por padrão, página 0, tamanho 10 e ordenação por descrição.

É possível solicitar a conversão do preço utilizando o parâmetro `targetCurrency`.

Exemplo:

```http
GET /products/1?targetCurrency=USD
```

### 3. Conversão de moedas — `currency-api`

Responsável por fornecer as taxas de conversão utilizadas pelo catálogo.

- Consulta de taxas entre diferentes moedas.
- Integração com a API PTAX do Banco Central do Brasil.
- Utilização da cotação de venda.
- Seleção da cotação válida mais recente para a data configurada.
- Utilização de taxas locais como alternativa quando a consulta externa não está disponível.
- Tratamento especial para moedas iguais, retornando taxa 1.
- Cache de consultas.
- Configurações de Retry e Circuit Breaker para comunicação com o Banco Central.

**Principal endpoint:**

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/currencies?source={origem}&target={destino}` | Consulta taxa de conversão |

Exemplo:

```http
GET /currencies?source=USD&target=BRL
```

A resposta contém as moedas de origem e destino, a taxa de conversão e informações do ambiente de execução.

### 4. API Gateway — `gateway-api`

Responsável por centralizar o acesso externo aos microsserviços.

- Recebimento e roteamento de requisições.
- Encaminhamento para os serviços por descoberta de serviços.
- Validação de tokens JWT nas rotas protegidas.
- Identificação do usuário autenticado.
- Encaminhamento de informações de identidade por meio dos cabeçalhos internos:
  - `X-User-Id`
  - `X-User-Type`
  - `X-User-Email`

As rotas protegidas utilizam o prefixo `/ws/**`.

O gateway possui rotas configuradas para autenticação, produtos, moedas e operações protegidas.

## Segurança

A autenticação utiliza JWT (JSON Web Token).

Após realizar o login, o cliente recebe um token que deve ser enviado nas requisições protegidas:

```http
Authorization: Bearer SEU_TOKEN_JWT
```

O gateway valida o token e encaminha os dados de identidade do usuário aos serviços internos.

O cadastro de produtos é restrito a usuários administradores. Usuários autenticados sem essa permissão recebem uma resposta de acesso negado.

**Observação:** a validação de JWT descrita está concentrada no Gateway. Os microsserviços não devem ser considerados protegidos contra acesso direto apenas por essa configuração. A externalização das chaves e o endurecimento da segurança entre serviços são pontos importantes antes de qualquer implantação em produção.

## Banco de dados e persistência

Os microsserviços utilizam PostgreSQL e Spring Data JPA para persistência.

O Flyway gerencia a evolução da estrutura dos bancos por meio de migrações SQL versionadas.

Entre os dados persistidos estão:

- Usuários e seus respectivos tipos.
- Produtos, preços e informações comerciais.
- Taxas cambiais utilizadas nas conversões.

Os bancos utilizados incluem:

- `db_currency`
- `db_product`
- `db_user`

As configurações de conexão devem corresponder às propriedades de cada serviço.

## Configuração e descoberta de serviços

O projeto utiliza HashiCorp Consul como parte da infraestrutura de configuração e descoberta de serviços.

O arquivo `start_consul.bat` auxilia na inicialização do Consul em ambiente Windows e no cadastro das propriedades utilizadas pelos microsserviços.

As configurações incluem propriedades de execução, conexão com PostgreSQL e parâmetros de integração com serviços externos.

O Consul em modo de desenvolvimento não oferece persistência dos dados de configuração após o encerramento da instância.

## Requisitos

Para executar o projeto localmente, são necessários:

- JDK 21.
- Maven ou Maven Wrapper.
- PostgreSQL.
- HashiCorp Consul.
- Git.

As portas e propriedades de cada serviço devem ser verificadas nos respectivos arquivos de configuração.

## Executando localmente

### 1. Clone o repositório

```bash
git clone https://github.com/luis-canal/ecommerce-microservices.git
cd ecommerce-microservices
```

### 2. Configure o PostgreSQL

Inicie o PostgreSQL e prepare os bancos utilizados pelos microsserviços.

Confira os nomes dos bancos, usuários, senhas e URLs de conexão nas configurações de cada aplicação ou no Consul.

### 3. Inicie o Consul

Em um terminal:

```bash
consul agent -dev -client=127.0.0.1
```

A interface web do Consul estará disponível em:

```text
http://localhost:8500
```

No Windows, o script `start_consul.bat` pode ser utilizado para auxiliar no cadastro das configurações.

### 4. Inicie os microsserviços

Com as dependências configuradas, abra um terminal para cada aplicação e execute:

**Auth API**

```bash
cd auth-api
./mvnw spring-boot:run
```

**Product API**

```bash
cd product-api
./mvnw spring-boot:run
```

**Currency API**

```bash
cd currency-api
./mvnw spring-boot:run
```

**Gateway API**

```bash
cd gateway-api
./mvnw spring-boot:run
```

No Windows, utilize `mvnw.cmd` no lugar de `./mvnw`.

As migrações Flyway são executadas automaticamente durante a inicialização, conforme a configuração de cada serviço.

> A inicialização depende de PostgreSQL, configurações necessárias no Consul e disponibilidade dos serviços utilizados na comunicação interna. Confira as portas configuradas antes de realizar as requisições.

## Testando as APIs

As requisições podem ser realizadas pelo Postman ou por ferramentas como cURL.

Exemplos de testes:

**Cadastro de usuário**

```http
POST /auth/signup
Content-Type: application/json
```

```json
{
  "name": "João da Silva",
  "email": "joao@email.com",
  "password": "Senha@123"
}
```

**Login**

```http
POST /auth/signin
Content-Type: application/json
```

```json
{
  "email": "joao@email.com",
  "password": "Senha@123"
}
```

**Consulta de produtos**

```http
GET /products
```

**Consulta de conversão cambial**

```http
GET /currencies?source=USD&target=BRL
```

Para as rotas protegidas, utilize o JWT recebido no login.

Os exemplos de endpoints devem ser enviados ao endereço e à porta configurados para o Gateway ou para o serviço correspondente, conforme o teste desejado.

## Estrutura do repositório

```text
ecommerce-microservices/
│
├── auth-api/
│   └── Serviço de autenticação e gerenciamento de usuários
│
├── product-api/
│   └── Serviço de catálogo e gerenciamento de produtos
│
├── currency-api/
│   └── Serviço de conversão de moedas
│
├── gateway-api/
│   └── Gateway e roteamento das requisições
│
├── start_consul.bat
│   └── Inicialização e configuração do Consul no Windows
│
└── README.md
```

Cada microsserviço possui seu próprio projeto Maven, configurações e estrutura de código.

## Projeto acadêmico

Desenvolvido no curso de Ciência da Computação da Atitus Educação.

**Repositório:** [github.com/luis-canal/ecommerce-microservices](https://github.com/luis-canal/ecommerce-microservices)

Projeto acadêmico desenvolvido para aplicação prática de conceitos de engenharia de software, arquitetura de microsserviços, desenvolvimento backend e sistemas distribuídos.