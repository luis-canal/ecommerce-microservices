# OMA — E-commerce de Artigos Gaúchos

Projeto acadêmico de um **e-commerce de artigos gaúchos**, desenvolvido com arquitetura de microsserviços utilizando Java e Spring Boot.

A aplicação é composta por nove microsserviços independentes: autenticação, catálogo de produtos, controle de estoque, conversão de moedas, carrinho, pedidos, pagamentos e API Gateway. Os serviços utilizam PostgreSQL para persistência, Flyway para versionamento do banco de dados e integração HTTP para comunicação entre aplicações.

O objetivo é construir uma base de e-commerce distribuída, explorando conceitos de desenvolvimento backend, APIs REST, autenticação, persistência de dados e comunicação entre microsserviços.

Consulte [API.md](./API.md) para a documentação detalhada dos fluxos de autenticação, catálogo, estoque, carrinho e pedidos.

## Arquitetura

O projeto é organizado em nove microsserviços:

| Serviço | Responsabilidade |
|---|---|
| `gateway-api` | Ponto de entrada da aplicação, roteamento e validação de JWT nas rotas protegidas. |
| `auth-api` | Cadastro de usuários, autenticação e emissão de tokens JWT. |
| `product-api` | Consulta e gerenciamento do ciclo de vida de produtos. |
| `inventory-api` | Estoque físico, disponibilidade, movimentações e reservas temporárias. |
| `currency-api` | Consulta de taxas cambiais e conversão de moedas. |
| `cart-api` | Carrinho persistente por cliente, quantidades desejadas e estimativa de subtotal. |
| `order-api` | Checkout, valores definitivos, frete, pedidos e coordenação da reserva atômica de estoque. |
| `analytics-api` | Coleta periódica de pedidos e estoque, agregações e indicadores para o painel administrativo. |
| `payment-api` | Pagamento dos pedidos com Stripe Checkout (modo teste), estados do pagamento e webhooks do Stripe. |

### Fluxo de comunicação

Todas as requisições externas às APIs devem passar pelo Gateway na porta `8080` (por exemplo, `http://localhost:8080`). As portas próprias dos microsserviços são internas à rede Docker para descoberta e comunicação entre serviços; não as utilize como portas de entrada para clientes.

O catálogo também se comunica com o serviço de moedas quando precisa apresentar um produto em uma moeda diferente da original.

```text
CLIENTE -> GATEWAY API -> CART API -> PRODUCT API -> CURRENCY API -> BANCO CENTRAL (PTAX)
                         |             ^                ^
                         v             |                |
                    INVENTORY API <--- ORDER API --------+
                         ^               |
                         +---------------+

ADMIN -> GATEWAY API -> ANALYTICS API -(coleta periódica)-> ORDER API
                             |
                             +--------(coleta periódica)-> INVENTORY API

CLIENTE -> GATEWAY API -> PAYMENT API -> ORDER API (payment-info, confirm, cancel)
                              |
                              v
                    STRIPE (sessão de Checkout, estorno)
                              |
                              +--(webhook)--> GATEWAY API -> PAYMENT API
```

O `payment-api` consulta o pedido no `order-api` antes de criar a sessão de pagamento no Stripe e, ao receber o webhook do Stripe, confirma ou cancela o pedido pelas rotas internas. O `order-api` não conhece o Stripe, e nenhum outro serviço depende do `payment-api`.

O `analytics-api` não participa do fluxo síncrono de compra. Em intervalos configuráveis (padrão de 5 minutos), ele consulta os endpoints internos de analytics do `order-api` e do `inventory-api`, grava os dados em seu próprio banco e responde às consultas do painel administrativo a partir dessa cópia. Se o `analytics-api` estiver fora do ar, checkout e pedidos continuam funcionando; se uma origem estiver indisponível, a coleta é repetida no ciclo seguinte.

Cada microsserviço possui sua própria responsabilidade e configuração de persistência.
O `cart-api` consulta o catálogo e a disponibilidade para mostrar uma estimativa, mas não reserva estoque nem calcula frete. O frontend envia os itens e o endereço diretamente ao `order-api`; depois da criação bem-sucedida do pedido, o frontend limpa o carrinho.

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
- Administradores listam e consultam produtos inativos por `GET /ws/products?status=INACTIVE|ALL` e `GET /ws/products/{id}`; sem `status`, a listagem administrativa é idêntica à pública. `INACTIVE` traz produtos com `active = false`; um produto ativo em categoria/subcategoria inativa aparece somente em `ALL`. Detalhes em [API.md](./API.md#listar-produtos-administrador-incluindo-inativos).
- Administradores podem listar categorias e subcategorias ativas e inativas com paginação por `GET /ws/categories`, `GET /ws/subcategories` e `GET /ws/categories/{id}/subcategories`. Detalhes em [API.md](./API.md#listar-todas-as-categorias-administrador).
- Validação de campos obrigatórios e preços positivos.
- Carregamento de dados iniciais por migrações Flyway.
- Upload de imagens de produto (JPEG, PNG e WebP) para o Supabase Storage via API S3, com imagem principal e até 8 imagens por produto.

**Principais endpoints:**

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/products` | Pesquisa, filtra e ordena produtos com paginação |
| GET | `/products/{id}` | Consulta produto por ID |
| POST | `/ws/products` | Cadastra produto (administrador) |
| PUT | `/ws/products/{id}` | Edita dados permitidos (administrador) |
| PATCH | `/ws/products/{id}/status` | Desativa ou reativa um produto (administrador) |
| GET | `/ws/products` | Lista produtos com filtro `status=ACTIVE\|INACTIVE\|ALL` e os mesmos filtros/paginação do catálogo (administrador) |
| GET | `/ws/products/{id}` | Consulta produto por ID, inclusive inativo (administrador) |
| GET | `/ws/categories` | Lista categorias ativas e inativas com paginação (administrador) |
| GET | `/ws/subcategories` | Lista subcategorias ativas e inativas com paginação (administrador) |
| GET | `/ws/categories/{id}/subcategories` | Lista todas as subcategorias da categoria, incluindo inativas (administrador) |
| POST | `/ws/products/{id}/images` | Envia imagem do produto, multipart com campo `file` (administrador) |
| GET | `/ws/products/{id}/images` | Lista as imagens do produto (administrador) |
| PATCH | `/ws/products/{id}/images/{imageId}/primary` | Define a imagem principal (administrador) |
| DELETE | `/ws/products/{id}/images/{imageId}` | Remove uma imagem (administrador) |

O cadastro recebe `name`, `brand`, `model`, `description`, `currency`, `price`, `image`, `subcategoryId` e `productType`. A descrição identifica o texto descritivo do produto; `productType` aceita `SINGLE_UNIT` ou `MULTI_UNIT`. O PUT atualiza os dados editáveis, mas não permite alterar o identificador, o tipo de estoque ou o status. O status é controlado exclusivamente pelo PATCH com `{"active": false}` ou `{"active": true}`. A reativação é recusada se a categoria ou subcategoria do produto estiver inativa.

Na migração para os novos campos, produtos existentes recebem `name` a partir de `model`; produtos sem nome válido ou sem classificação explícita de tipo impedem a migração até que os dados sejam mapeados.

A listagem utiliza, por padrão, página 0, tamanho 10 e ordenação por descrição.

O endpoint aceita os filtros combináveis `name` (trecho do nome, sem diferenciar maiúsculas/minúsculas), `categoryId`, `subcategoryId`, `minPrice` e `maxPrice`. IDs de categoria e subcategoria são `Long`. Os limites de preço são não negativos, usam `BigDecimal` e são sempre interpretados em BRL; para produtos cadastrados em outra moeda, os valores são convertidos para BRL antes da filtragem ou ordenação. Se uma conversão necessária falhar, a API retorna erro em vez de resultados incompletos. Os preços são persistidos com duas casas decimais.

A ordenação pode ser feita por `price` ou `name`, em ordem `asc` ou `desc`; nome é comparado sem distinção entre maiúsculas/minúsculas. A paginação aceita `page` e `size`, com padrão página 0 e tamanho 10. `targetCurrency` define a moeda dos preços apresentados na resposta e, quando omitido, usa BRL.

As respostas de produto trazem `images` (`id`, `url`, `isPrimary`, `position`) e o campo `image`, que é a URL da imagem principal ou, sem imagens enviadas, o valor legado da coluna `image`. Carrinho e pedido continuam usando `image` nos snapshots, sem alterações nesses serviços. O armazenamento fica atrás da interface `ImageStorage`; trocar de provedor compatível com S3 (Cloudflare R2, MinIO) exige apenas configuração. Detalhes em [API.md](./API.md#imagens-do-produto).

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

### 4. Carrinho — `cart-api`

Mantém um carrinho persistente por cliente autenticado. Armazena referências de produto e quantidades desejadas, e consulta `product-api` e `inventory-api` para exibir dados atuais, disponibilidade e subtotal estimado em BRL.

- Produto repetido no POST atualiza a quantidade existente sem duplicar o item.
- `SINGLE_UNIT` aceita somente uma unidade; a quantidade de `MULTI_UNIT` pode ser alterada.
- Adição e alteração validam a disponibilidade observada; a validação definitiva continua no checkout.
- O carrinho não reserva estoque, não calcula frete e não cria pedidos.

Endpoints: `GET /ws/cart`, `POST /ws/cart/items`, `PATCH /ws/cart/items/{productId}`, `DELETE /ws/cart/items/{productId}` e `DELETE /ws/cart/items`.
O serviço e seu banco dedicado utilizam, respectivamente, as portas 8600 e 5432 na rede Docker.

### 5. Pedidos e checkout — `order-api`

Recebe diretamente do frontend os itens e o endereço de entrega, revalida o catálogo, calcula os valores definitivos e o frete, solicita uma reserva atômica de estoque e cria o pedido em `PENDING_PAYMENT` após a reserva bem-sucedida. O checkout não depende do carrinho nem o limpa automaticamente.

Após receber `201 Created` de `POST /ws/orders`, o frontend pode limpar os itens chamando `DELETE /ws/cart/items`. O banco dedicado do serviço é `db_order`.

### 6. Conversão de moedas — `currency-api`

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

### 7. API Gateway — `gateway-api`

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

O gateway possui rotas configuradas para autenticação, produtos, moedas, estoque, carrinho, pedidos e operações protegidas. Nas chamadas ao `inventory-api`, ele encaminha a credencial interna de downstream; chamadas entre serviços também usam credencial serviço-a-serviço.

### 8. Analytics — `analytics-api`

Responsável pelos indicadores do painel administrativo. Coleta periodicamente os dados do `order-api` e do `inventory-api`, mantém uma cópia própria e pré-agregada no banco `analytics_db` e responde às consultas sem acessar os serviços de origem.

- Consome somente as APIs internas do `order-api` e do `inventory-api`; não acessa os bancos de dados de outros microsserviços.
- Coleta incremental de pedidos por data de atualização, captando também mudanças de status (confirmação, cancelamento, expiração).
- Coleta de snapshots de estoque, produtos sem estoque, produtos com estoque baixo e movimentações.
- Gravação idempotente: repetir uma coleta não duplica pedidos, itens ou movimentações.
- Agregações diárias pré-processadas de vendas por estado, região e produto.
- Faturamento, pedidos confirmados, ticket médio e unidades vendidas, com evolução diária, semanal ou mensal.
- Vendas por região e estado, produtos mais vendidos, desempenho por categoria e marca, e faixas de preço.
- Filtros por período, estado, região, categoria e produto.
- Retentativas nas chamadas às origens, histórico das execuções e disparo manual da coleta.
- Somente pedidos `CONFIRMED` em BRL contam como venda; o faturamento é o `totalAmount` do pedido, com frete.

**Principais endpoints (administrador):**

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/ws/admin/analytics/overview` | Resumo de vendas do período |
| GET | `/ws/admin/analytics/sales` | Evolução das vendas ao longo do tempo |
| GET | `/ws/admin/analytics/sales/by-region` | Vendas por região e estado |
| GET | `/ws/admin/analytics/products/top` | Produtos mais vendidos |
| GET | `/ws/admin/analytics/categories` | Desempenho por categoria e marca |
| GET | `/ws/admin/analytics/price-ranges` | Vendas por faixa de preço |
| GET | `/ws/admin/analytics/inventory` | Indicadores de estoque |
| GET | `/ws/admin/analytics/inventory/movements` | Movimentações de estoque coletadas |
| GET | `/ws/admin/analytics/collections` | Situação e histórico das coletas |
| POST | `/ws/admin/analytics/collections/run` | Dispara a coleta manualmente |

O serviço e seu banco dedicado utilizam, respectivamente, as portas 8700 e 5432 na rede Docker. O intervalo da coleta é definido no Consul pela chave `config/analytics-api/analytics.collection.interval-ms` (padrão `300000`, 5 minutos). Os endpoints estão detalhados em [API.md](./API.md).

### 9. Pagamentos — `payment-api`

Responsável pelo pagamento dos pedidos com o **Stripe Checkout em modo de teste**. Inicia o pagamento, acompanha os estados e comunica o resultado ao `order-api`, mantendo a integração com o provedor isolada do domínio de pedidos.

- O valor e a moeda vêm sempre do `order-api`, por uma consulta interna autenticada; o frontend não envia preço.
- O cliente é redirecionado para a página hospedada do Stripe; a loja nunca recebe dados de cartão.
- O resultado chega pelo webhook assinado do Stripe. O retorno do navegador para a página de sucesso não aprova o pagamento: o frontend apenas consulta o status.
- Pagamento aprovado confirma o pedido; pagamento recusado ou sessão expirada cancela o pedido e libera a reserva de estoque.
- Pagamento aprovado depois que a reserva do pedido expirou, ou depois que o pagamento já foi encerrado (`FAILED`, `EXPIRED` ou `CANCELLED`), é estornado automaticamente (`REFUNDED`). Limitação conhecida: não há job de reconciliação; se o estorno ou a confirmação falharem de forma permanente, o webhook responde `500`, o Stripe reenvia o evento por cerca de 3 dias, o pagamento fica `PENDING` e o caso exige intervenção manual.
- Idempotência: no máximo um pagamento aberto por pedido, chaves de idempotência nas chamadas ao Stripe e deduplicação dos eventos do webhook.
- O SDK do Stripe é usado apenas atrás da interface `PaymentGateway`, o que permite trocar o provedor sem alterar o `order-api`.

**Endpoints:**

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/ws/payments/orders/{orderId}/checkout-session` | Inicia (ou reaproveita) o pagamento do pedido e devolve a URL do Checkout |
| GET | `/ws/payments/orders/{orderId}` | Status do pagamento mais recente do pedido |
| POST | `/payments/webhook` | Notificações do Stripe (pública, validada pela assinatura) |

Estados do pagamento: `CREATED`, `PENDING`, `APPROVED`, `FAILED`, `EXPIRED`, `CANCELLED` e `REFUNDED`. O serviço e seu banco dedicado (`payment_db`) utilizam, respectivamente, as portas 8800 e 5432 na rede Docker. O serviço só sobe com uma chave de teste do Stripe (`sk_test_` ou `rk_test_`) e sobe por padrão com o Compose. Os endpoints, as transições e o tratamento de falhas estão detalhados em [API.md](./API.md#pagamentos).

## Segurança

A autenticação utiliza JWT (JSON Web Token).

Após realizar o login, o cliente recebe um token que deve ser enviado nas requisições protegidas:

```http
Authorization: Bearer SEU_TOKEN_JWT
```

O gateway valida o token e encaminha os dados de identidade do usuário aos serviços internos.

O cadastro de produtos é restrito a usuários administradores. Usuários autenticados sem essa permissão recebem uma resposta de acesso negado.

O `inventory-api` e o `cart-api` validam credenciais privadas do Gateway. As consultas internas do `cart-api` ao catálogo e ao estoque usam credencial serviço-a-serviço. Em produção, configure Consul com ACL/TLS e um gerenciador de segredos.

As rotas `/ws/admin/analytics/**` exigem JWT de administrador: sem token o Gateway responde `401`, e usuários sem perfil de administrador recebem `403`. O `analytics-api` aceita somente requisições encaminhadas pelo Gateway com sua credencial privada; chamadas diretas ao serviço são rejeitadas. Para coletar dados, ele chama o `order-api` e o `inventory-api` com a credencial serviço-a-serviço.

As rotas `/ws/payments/**` exigem JWT de cliente e chegam ao `payment-api` com sua credencial privada do Gateway; chamadas diretas são rejeitadas. A única rota pública do serviço é `POST /payments/webhook`, autenticada pela assinatura `Stripe-Signature` do Stripe. O `payment-api` chama as rotas internas do `order-api` (`payment-info`, `confirm` e `cancel`) com a credencial serviço-a-serviço; pelo Gateway, um cliente recebe `401` nessas rotas. A chave secreta do Stripe fica no KV do Consul (nunca no repositório), precisa ser de teste e nunca é registrada em log, assim como o corpo dos webhooks.

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
- `db_order`
- `db_cart`
- `analytics_db`
- `payment_db`

O `analytics_db` guarda a cópia dos pedidos e itens coletados, as agregações diárias de vendas, os snapshots e movimentações de estoque, o progresso de cada coleta e o histórico de execuções.

O `payment_db` guarda os pagamentos (pedido, cliente, valor, moeda, status, sessão de Checkout e transação do Stripe, datas de criação, atualização e confirmação) e os identificadores dos eventos do Stripe já processados. Um índice único parcial garante no máximo um pagamento aberto (`CREATED` ou `PENDING`) por pedido.

As configurações de conexão devem corresponder às propriedades de cada serviço.

## Configuração e descoberta de serviços

O projeto utiliza HashiCorp Consul como parte da infraestrutura de configuração e descoberta de serviços.

O `load-config.sh`, executado pelo Docker Compose, cadastra no Consul as propriedades utilizadas pelos microsserviços e provisiona credenciais internas para as integrações.

As configurações incluem propriedades de execução, conexão com PostgreSQL e parâmetros de integração com serviços externos.

Para o `analytics-api`, o script cadastra a porta, a conexão com o `analytics_db`, as URLs do `order-api` e do `inventory-api`, as credenciais internas e o intervalo da coleta periódica (`analytics.collection.interval-ms`).

Para o `payment-api`, o script cadastra a porta, a conexão com o `payment_db`, a URL do `order-api`, as credenciais internas e a reserva mínima para abrir um checkout (`payment.min-reservation-seconds`, padrão `120`). O script também cadastra as credenciais do Stripe (`payment.stripe.*`), lidas de `docker/.env`.

Para o `product-api`, além da porta, do banco e da resiliência, o script cadastra as credenciais do armazenamento de imagens (`app.storage.*`), lidas de `docker/.env`.

As credenciais externas seguem o mesmo modelo das credenciais internas: o `consul-config` é o único container que recebe o arquivo `docker/.env`, e os microsserviços leem os valores apenas do Consul. Uma variável vazia ou ausente remove a chave correspondente do Consul. O valor é enviado ao Consul pela entrada padrão e não aparece no log do carregador. Qualquer pessoa com acesso à porta `8500` consegue ler o KV; por isso, use apenas chaves de teste neste ambiente.

Para todos os serviços, o script também cadastra em `config/application/`:

- `spring.web.error.include-message=always`: o corpo das respostas de erro inclui a mensagem da regra de negócio (por exemplo, o motivo de um `409`). Stack trace e nome da exceção continuam ocultos.
- `spring.cloud.consul.discovery.prefer-ip-address=true`: cada serviço se registra no Consul pelo IP do container. Com o hostname, o Gateway mantinha em cache o IP antigo e respondia `500` depois que um serviço reiniciava com outro IP.

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

Por padrão, o Compose inicia todos os microsserviços: Consul e seu carregador de configuração, Gateway, Auth, Product, Currency, Inventory, Order, Payment, Cart e Analytics, com os respectivos bancos. Os builds são sequenciais para reduzir o pico de CPU, memória e disco.

**Primeira execução:**

1. Peça o arquivo `docker/.env` a quem montou o ambiente. O arquivo contém as credenciais externas (S3 e Stripe de teste) e não é versionado; envie-o somente por um canal privado.
2. Coloque o arquivo na pasta `docker/` do repositório.
3. Rode o Compose:

```bash
cd docker
docker compose up --build -d
```

Sem o `docker/.env`, os serviços sobem, mas o upload de imagens falha e o `payment-api` não sobe. Para executar a partir da raiz do repositório:

```bash
docker compose --parallel 1 -f docker/docker-compose.yml up --build -d
```

A observabilidade (Prometheus, Loki, Alloy, Grafana e Zipkin) é opcional e pode ser adicionada quando necessária:

```bash
docker compose --parallel 1 -f docker/docker-compose.yml --profile observability up --build -d
```

O BuildKit compartilha o cache de dependências Maven entre as APIs; a primeira compilação ainda precisa baixar as dependências. Use `docker compose -f docker/docker-compose.yml down` para parar os serviços sem apagar dados. Evite `down -v` se quiser conservar volumes.

O script gera cinco segredos se ainda não existirem, grava-os no volume `auth_internal_secret` e publica os valores no KV do Consul. A credencial serviço-a-serviço é compartilhada pelos serviços que realizam chamadas internas; as credenciais de encaminhamento do Gateway são distintas para Inventory, Order e Cart. Os valores são preservados enquanto o volume existir. Não execute `docker compose down -v` se quiser conservar as credenciais e os dados dos bancos.

### Armazenamento de imagens (Supabase Storage)

O `product-api` lê as credenciais do armazenamento do Consul (`config/product-api/app.storage.*`). O `consul-config` cadastra os valores a partir da seção `# --- Imagens / S3 (Supabase) ---` de `docker/.env`. O modelo `docker/.env.example` lista as variáveis sem valores; use-o apenas para montar o arquivo pela primeira vez.

| Variável | Descrição |
|---|---|
| `STORAGE_S3_ENDPOINT` | Endpoint S3 do projeto Supabase (`https://<projeto>.storage.supabase.co/storage/v1/s3`) |
| `STORAGE_S3_REGION` | Região do projeto |
| `STORAGE_S3_ACCESS_KEY` / `STORAGE_S3_SECRET_KEY` | Chaves de acesso S3 geradas no painel do Supabase |
| `STORAGE_S3_BUCKET` | Bucket público, padrão `product-images` |
| `STORAGE_PUBLIC_BASE_URL` | Base das URLs públicas (`https://<projeto>.supabase.co/storage/v1/object/public/product-images`) |

Sem essas variáveis o serviço sobe normalmente; apenas o upload de imagens falha. Nunca versione chaves reais.

O `analytics-api` acrescenta mais uma credencial de encaminhamento do Gateway, gerada e persistida da mesma forma que as demais.

O `payment-api` também acrescenta uma credencial de encaminhamento do Gateway (`GATEWAY_PAYMENT_DOWNSTREAM_TOKEN`), gerada e persistida da mesma forma, e reutiliza a credencial serviço-a-serviço para chamar o `order-api`.

### Pagamentos com Stripe (modo teste)

O `payment-api` lê as credenciais do Stripe do Consul (`config/payment-api/payment.stripe.*`). O `consul-config` cadastra os valores a partir da seção `# --- Stripe / Payment ---` de `docker/.env`; nenhum microsserviço recebe esse arquivo diretamente.

Use somente chaves de **teste**: o serviço não sobe se `STRIPE_SECRET_KEY` não começar com `sk_test_` ou `rk_test_`. Nunca versione chaves.

| Variável | Descrição |
|---|---|
| `STRIPE_SECRET_KEY` | Chave secreta de teste (`sk_test_...` ou chave restrita `rk_test_...`), em *Developers → API keys* no painel do Stripe |
| `STRIPE_WEBHOOK_SECRET` | Segredo de assinatura do webhook (`whsec_...`), exibido pelo `stripe listen` ou pelo endpoint cadastrado no painel |
| `PAYMENT_SUCCESS_URL` | Retorno após o pagamento; deve conter `{CHECKOUT_SESSION_ID}`. Para o app, use a rota de retorno do Gateway num endereço que o celular alcança, por exemplo `http://192.168.0.18:8080/payments/return/success?session_id={CHECKOUT_SESSION_ID}` |
| `PAYMENT_CANCEL_URL` | Retorno quando o cliente desiste do pagamento, por exemplo `http://192.168.0.18:8080/payments/return/cancel` |
| `PAYMENT_APP_SUCCESS_URL` | Opcional. Deep link do app para onde `/payments/return/success` redireciona; padrão `oma://payment/success` |
| `PAYMENT_APP_CANCEL_URL` | Opcional. Deep link do app para onde `/payments/return/cancel` redireciona; padrão `oma://payment/cancel` |

O `payment-api` acrescenta `order_id` às URLs de retorno de cada sessão. Use o IP da máquina na rede local (`ipconfig`) ou um túnel: `localhost` não funciona no celular. No Windows, libere a porta `8080` no firewall para a rede privada.

**Teste local com a Stripe CLI:**

1. Instale a [Stripe CLI](https://docs.stripe.com/stripe-cli) e autentique-se na conta em modo de teste:

   ```bash
   stripe login
   ```

2. Encaminhe os webhooks para o Gateway e copie o segredo `whsec_...` exibido para `STRIPE_WEBHOOK_SECRET` em `docker/.env` (mantenha o comando rodando):

   ```bash
   stripe listen --forward-to localhost:8080/payments/webhook
   ```

3. Preencha `STRIPE_SECRET_KEY`, `PAYMENT_SUCCESS_URL` e `PAYMENT_CANCEL_URL` em `docker/.env` e suba os serviços (na primeira vez, inclua `--build`):

   ```bash
   cd docker
   docker compose up -d
   ```

   Se você alterar `docker/.env` com os serviços em execução (por exemplo, um novo `whsec_...`), recarregue o Consul e reinicie o serviço afetado:

   ```bash
   docker compose up -d --force-recreate consul-config payment-api
   ```

4. Autentique-se como cliente, crie um pedido em `POST /ws/orders` e inicie o pagamento:

   ```http
   POST /ws/payments/orders/{orderId}/checkout-session
   Authorization: Bearer SEU_TOKEN_JWT
   ```

5. Abra o `checkoutUrl` da resposta e pague com um cartão de teste, usando qualquer data de validade futura, qualquer CVC e qualquer CEP.

6. Acompanhe o `stripe listen`: o evento `checkout.session.completed` deve ser respondido com `200`. Em seguida, `GET /ws/payments/orders/{orderId}` retorna `APPROVED`, e `GET /ws/orders/{orderId}` retorna `CONFIRMED`.

| Cartão de teste | Resultado |
|---|---|
| `4242 4242 4242 4242` | Pagamento aprovado |
| `4000 0025 0000 3155` | Exige autenticação 3D Secure; aprovado após autenticar |
| `4000 0000 0000 0002` | Recusado (o Checkout mostra o erro e o cliente pode tentar outro cartão) |
| `4000 0000 0000 9995` | Recusado por saldo insuficiente |

Outros cenários:

- **Sessão expirada:** expire a sessão com `stripe checkout sessions expire cs_test_...`. O pagamento passa a `EXPIRED` e o pedido é cancelado, liberando o estoque.
- **Pagamento tardio:** espere a reserva do pedido expirar (10 minutos) antes de pagar com a sessão ainda aberta. O pedido não é confirmado e o pagamento é estornado (`REFUNDED`); o estorno aparece em *Payments* no painel do Stripe.
- **Reenvio de evento:** `stripe events resend evt_...` reenvia um evento já processado; ele é respondido com `200` sem efeito.

O Consul configurado com `-dev` é para desenvolvimento local; ele não persiste suas chaves após a remoção/recriação do container. O script repopula o KV ao iniciar o Compose, usando as credenciais persistidas. Em produção, use Consul com ACL/TLS e um gerenciador de segredos apropriado em vez do modo de desenvolvimento.

### Dados de teste

Os bancos são locais de cada pessoa. As migrations já criam:

- **Catálogo:** categorias, subcategorias e 24 produtos ativos (`product-api`).
- **Administrador:** `admin@admin.dev`, senha de desenvolvimento `teste` (`auth-api`). Use somente em ambiente local.

O **estoque não tem seed**: um produto sem estoque cadastrado não pode ir para o carrinho (`409` com a mensagem *"A quantidade desejada excede a disponibilidade atual do estoque."*). Com o back-end no ar, rode a partir da raiz do repositório:

```bash
ADMIN_PASSWORD=teste bash docker/scripts/seed-inventory.sh
```

O script usa a API pelo Gateway e, para cada produto ativo:

- sem registro no estoque: registra e lança a entrada inicial;
- registrado com saldo físico zero: trata como pendente e lança a entrada inicial;
- registrado com saldo: ignora.

Produtos `MULTI_UNIT` recebem 50 unidades e produtos `SINGLE_UNIT` recebem 1 (o máximo aceito pelo `inventory-api`), com o motivo *"Carga inicial para testes"*. O script pode rodar várias vezes. Sem `ADMIN_PASSWORD`, ele pede a senha sem eco no terminal. Variáveis opcionais: `GATEWAY_URL` (padrão `http://localhost:8080`), `ADMIN_EMAIL`, `SEED_QUANTITY` e `SEED_REASON`. Requer `bash`, `curl` e Python 3 (no Windows, use o Git Bash).
## Dados de demonstração (seed)

> **Somente ambiente local de demonstração.** Os dados fictícios são carregados automaticamente pelo Flyway. Não use as credenciais de demonstração em ambientes reais.

Ao recriar os bancos PostgreSQL vazios, cada microsserviço aplica suas migrações na inicialização:

- `auth-api`: 20 clientes de teste em `V4__PopulateDemoUsers.sql`. Todos usam a senha exclusiva `Demo@2026`.
- `product-api`: 30 produtos em `V8__PopulateDemoProducts.sql`, associados às categorias da V3. Os exemplos da V5 são desativados.
- `inventory-api`: saldos, entradas, reservas e movimentações em `V2__PopulateDemoInventory.sql`.
- `order-api`: pedidos confirmados, cancelados e expirados em `V4__PopulateDemoOrders.sql`.
- `cart-api`: seis carrinhos em `V2__PopulateDemoCarts.sql`.

Para recriar os dados em bancos vazios:

```bash
docker compose -f docker/docker-compose.yml --profile cart --profile analytics down -v
docker compose --parallel 1 -f docker/docker-compose.yml --profile cart --profile analytics up --build -d
```

> **Atenção:** `down -v` remove todos os volumes PostgreSQL e, consequentemente, todos os dados de negócio e credenciais locais. Não apague os volumes em um ambiente real.

As migrações usam IDs fixos coordenados entre os bancos e avançam as sequences. Elas falham explicitamente se encontrar IDs ou e-mails reservados ao demo, sem apagar ou sobrescrever dados existentes.

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

**Cart API**

```bash
cd cart-api
mvn spring-boot:run
```

**Order API**

```bash
cd order-api
./mvnw spring-boot:run
```

**Analytics API**

```bash
cd analytics-api
./mvnw spring-boot:run
```

**Payment API** (exige as variáveis `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `PAYMENT_SUCCESS_URL` e `PAYMENT_CANCEL_URL` no ambiente)

```bash
cd payment-api
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

Os endpoints de disponibilidade e reservas são protegidos por credencial de serviço. Chamadas externas passam pelo Gateway; os microsserviços se comunicam diretamente pela rede interna usando as credenciais provisionadas no Consul. Não exponha nem envie esses segredos a clientes externos.

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
├── cart-api/
│   └── Serviço de carrinho persistente
├── order-api/
│   └── Serviço de pedidos e checkout
├── analytics-api/
│   └── Serviço de coleta e indicadores do painel administrativo
├── payment-api/
│   └── Serviço de pagamentos com Stripe (modo teste)
│
├── gateway-api/
│   └── Gateway e roteamento das requisições
│
├── docker/
│   ├── docker-compose.yml, consul/ (carregador de configurações) e observabilidade
│   └── scripts/seed-inventory.sh — estoque inicial para testes
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