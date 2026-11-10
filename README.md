# OMA — E-commerce de Artigos Gaúchos

Projeto acadêmico de um **e-commerce de artigos gaúchos**, desenvolvido com arquitetura de microsserviços utilizando Java e Spring Boot.

A aplicação é composta por cinco serviços independentes: autenticação, catálogo de produtos, controle de estoque, conversão de moedas e API Gateway. Os serviços utilizam PostgreSQL para persistência, Flyway para versionamento do banco de dados e integração HTTP para comunicação entre aplicações.

O objetivo é construir uma base de e-commerce distribuída, explorando conceitos de desenvolvimento backend, APIs REST, autenticação, persistência de dados e comunicação entre microsserviços.

Consulte [API.md](./API.md) para a documentação detalhada dos fluxos de autenticação, catálogo de produtos e estoque.

## Arquitetura

O projeto é organizado em cinco microsserviços:

| Serviço | Responsabilidade |
|---|---|
| `gateway-api` | Ponto de entrada da aplicação, roteamento e validação de JWT nas rotas protegidas. |
| `auth-api` | Cadastro de usuários, autenticação e emissão de tokens JWT. |
| `product-api` | Consulta e gerenciamento do ciclo de vida de produtos. |
| `inventory-api` | Estoque físico, disponibilidade, movimentações e reservas temporárias. |
| `currency-api` | Consulta de taxas cambiais e conversão de moedas. |

### Fluxo de comunicação

Todas as requisições externas às APIs devem passar pelo Gateway na porta `8080` (por exemplo, `http://localhost:8080`). As portas próprias dos microsserviços são internas à rede Docker para descoberta e comunicação entre serviços; não as utilize como portas de entrada para clientes.

O catálogo também se comunica com o serviço de moedas quando precisa apresentar um produto em uma moeda diferente da original.

```text
                    CLIENTE
                       |
                       v
                 GATEWAY API
                       |
          +-----------+-----------+-----------+
          |           |           |           |
          v           v           v           v
       AUTH API   PRODUCT API  INVENTORY API CURRENCY API
                       |           |           |
                       +-----------+           v
                                   |       BANCO CENTRAL
                                   |         (PTAX)
                             INVENTORY DB
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

Responsável pela consulta e pelo ciclo de vida dos produtos do catálogo.

- Consulta de produto por identificador.
- Listagem paginada de produtos.
- Ordenação dos resultados.
- Conversão de preços para outras moedas.
- Cadastro de produtos restrito a administradores.
- Edição, desativação e reativação de produtos restritas a administradores.
- Classificação dos produtos como unitários ou multi-unitários; o controle das quantidades é responsabilidade do `inventory-api`.
- Produtos inativos não são exibidos nas consultas públicas.
- Validação de campos obrigatórios e preços positivos.
- Carregamento de dados iniciais por migrações Flyway.

**Principais endpoints:**

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/products` | Pesquisa, filtra e ordena produtos com paginação |
| GET | `/products/{id}` | Consulta produto por ID |
| POST | `/ws/products` | Cadastra produto (administrador) |
| PUT | `/ws/products/{id}` | Edita dados permitidos (administrador) |
| PATCH | `/ws/products/{id}/status` | Desativa ou reativa um produto (administrador) |

O cadastro recebe `name`, `brand`, `model`, `description`, `currency`, `price`, `image`, `subcategoryId` e `productType`. A descrição identifica o texto descritivo do produto; `productType` aceita `SINGLE_UNIT` ou `MULTI_UNIT`. O PUT atualiza os dados editáveis, mas não permite alterar o identificador, o tipo de estoque ou o status. O status é controlado exclusivamente pelo PATCH com `{"active": false}` ou `{"active": true}`. A reativação é recusada se a categoria ou subcategoria do produto estiver inativa.

Na migração para os novos campos, produtos existentes recebem `name` a partir de `model`; produtos sem nome válido ou sem classificação explícita de tipo impedem a migração até que os dados sejam mapeados.

A listagem utiliza, por padrão, página 0, tamanho 10 e ordenação por descrição.

O endpoint aceita os filtros combináveis `name` (trecho do nome, sem diferenciar maiúsculas/minúsculas), `categoryId`, `subcategoryId`, `minPrice` e `maxPrice`. IDs de categoria e subcategoria são `Long`. Os limites de preço são não negativos, usam `BigDecimal` e são sempre interpretados em BRL; para produtos cadastrados em outra moeda, os valores são convertidos para BRL antes da filtragem ou ordenação. Se uma conversão necessária falhar, a API retorna erro em vez de resultados incompletos. Os preços são persistidos com duas casas decimais.

A ordenação pode ser feita por `price` ou `name`, em ordem `asc` ou `desc`; nome é comparado sem distinção entre maiúsculas/minúsculas. A paginação aceita `page` e `size`, com padrão página 0 e tamanho 10. `targetCurrency` define a moeda dos preços apresentados na resposta e, quando omitido, usa BRL.

Exemplo:

```http
GET /products?name=cuia&categoryId=2&subcategoryId=7&minPrice=50&maxPrice=100&sort=price,asc&page=0&size=10
```

Para consultar um produto específico com o preço convertido:

```http
GET /products/1?targetCurrency=USD
```

### 3. Estoque e reservas — `inventory-api`

Responsável pela quantidade física, disponibilidade comercial do estoque, histórico de movimentações e ciclo de vida das reservas iniciadas pelo checkout.

- Calcula disponibilidade como quantidade física menos quantidade reservada.
- Impede reservas acima do saldo disponível com operações transacionais e bloqueios no banco.
- Diferencia produtos `SINGLE_UNIT` e `MULTI_UNIT`; o tipo é consultado no `product-api`.
- Registra entradas, saídas manuais, ajustes e baixas de reservas em um histórico imutável de movimentações.
- Cria reservas para vários produtos atomicamente, com expiração em 10 minutos.
- Confirma, libera e expira reservas sem permitir dupla baixa ou dupla liberação.

O serviço e seu PostgreSQL utilizam, respectivamente, as portas 8400 e 5432 na rede Docker. Os endpoints estão detalhados em [API.md](./API.md).

### 4. Conversão de moedas — `currency-api`

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

### 5. API Gateway — `gateway-api`

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

O gateway possui rotas configuradas para autenticação, produtos, moedas, estoque, pedidos e operações protegidas. Nas chamadas ao `inventory-api`, ele encaminha a credencial interna de downstream; chamadas entre serviços também usam credencial serviço-a-serviço.

## Segurança

A autenticação utiliza JWT (JSON Web Token).

Após realizar o login, o cliente recebe um token que deve ser enviado nas requisições protegidas:

```http
Authorization: Bearer SEU_TOKEN_JWT
```

O gateway valida o token e encaminha os dados de identidade do usuário aos serviços internos.

O cadastro de produtos é restrito a usuários administradores. Usuários autenticados sem essa permissão recebem uma resposta de acesso negado.

O `inventory-api` valida uma credencial privada do Gateway em todas as rotas e exige autenticação serviço-a-serviço nas operações internas. Em produção, configure Consul com ACL/TLS e um gerenciador de segredos; os demais microsserviços ainda devem ser protegidos contra acesso direto conforme forem integrados.

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
- `db_inventory`

As configurações de conexão devem corresponder às propriedades de cada serviço.

## Configuração e descoberta de serviços

O projeto utiliza HashiCorp Consul como parte da infraestrutura de configuração e descoberta de serviços.

O `load-config.sh`, executado pelo Docker Compose, cadastra no Consul as propriedades utilizadas pelos microsserviços e provisiona credenciais internas para as integrações.

As configurações incluem propriedades de execução, conexão com PostgreSQL e parâmetros de integração com serviços externos.

O Consul em modo de desenvolvimento não oferece persistência dos dados de configuração após o encerramento da instância.

## Requisitos

Para executar o projeto localmente, são necessários:

- JDK 21.
- Maven ou Maven Wrapper.
- PostgreSQL.
- HashiCorp Consul.
- Git.

Para requisições de clientes, utilize somente a porta `8080` do Gateway. As portas dos microsserviços e dos bancos são internas à rede Docker; a interface do Consul continua disponível na porta `8500` para configuração e administração local.

## Executando com Docker Compose

O Compose sobe Consul, carrega as configurações por [`load-config.sh`](./docker/consul/config/load-config.sh), inicia os bancos e constrói os serviços:

```bash
docker compose -f docker/docker-compose.yml up --build
```

O script gera três segredos se ainda não existirem, grava-os no volume `auth_internal_secret` e publica os valores no KV do Consul. A mesma credencial de serviço é compartilhada pelo `gateway-api`, `product-api` e `inventory-api`; a credencial de encaminhamento do Gateway é compartilhada pelo `gateway-api` e `inventory-api`. Os valores são preservados enquanto o volume existir. Não execute `docker compose down -v` se quiser conservar as credenciais e os dados dos bancos.

O Consul configurado com `-dev` é para desenvolvimento local; ele não persiste suas chaves após a remoção/recriação do container. O script repopula o KV ao iniciar o Compose, usando as credenciais persistidas. Em produção, use Consul com ACL/TLS e um gerenciador de segredos apropriado em vez do modo de desenvolvimento.

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

**Inventory API**

```bash
cd inventory-api
./mvnw spring-boot:run
```

**Gateway API**

```bash
cd gateway-api
./mvnw spring-boot:run
```

No Windows, utilize `mvnw.cmd` no lugar de `./mvnw`.

As migrações Flyway são executadas automaticamente durante a inicialização, conforme a configuração de cada serviço.

> A inicialização depende de PostgreSQL, configurações necessárias no Consul e disponibilidade dos serviços utilizados na comunicação interna. Envie as requisições da aplicação para o Gateway em `http://localhost:8080`; as portas individuais dos microsserviços não são publicadas no host.

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

Os endpoints de disponibilidade e reservas também são protegidos por credencial de serviço. Chamadas entre serviços devem passar pelo Gateway usando a credencial interna provisionada no Consul; não exponha ou envie esses segredos a clientes externos.

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
├── inventory-api/
│   └── Serviço de controle de estoque e reservas
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