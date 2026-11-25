# Documentação da API

Guia para integração com os fluxos de autenticação, catálogo e estoque deste repositório. As rotas abaixo são relativas à URL-base do **API Gateway**; a porta e o host dependem da configuração do ambiente.

## Convenções

- Envie e receba JSON com `Content-Type: application/json` e `Accept: application/json`, salvo respostas sem corpo. Respostas de erro usam `Content-Type: text/plain; charset=UTF-8`.
- IDs são inteiros de 64 bits (`Long` no servidor); preços são números decimais.
- Erros retornam uma mensagem em texto simples, específica para a causa sempre que possível. O Gateway preserva o status e o corpo de erro dos serviços; uma requisição protegida sem JWT válido recebe `401 Unauthorized` com `"Token ausente, inválido ou revogado."`. Falhas inesperadas retornam uma mensagem segura, sem detalhes técnicos.
- As rotas `/ws/**` são protegidas pelo Gateway. Envie `Authorization: Bearer <token>`; **não** envie manualmente os cabeçalhos internos `X-User-Id`, `X-User-Type` ou `X-User-Email`. O Gateway os remove da requisição recebida do cliente e propaga os valores derivados do JWT.
- Tokens JWT expiram em 24 horas. O logout revoga o token até a expiração.
- O tipo de usuário de cadastro público é sempre `Common`. A conta administradora é provisionada no banco; não há rota separada de login administrativo.

### Formato das falhas

Erros são retornados como texto simples UTF-8, sem envelope JSON. O Gateway preserva o status e a mensagem enviados pelo serviço. Exemplos representativos:

| Status | Cenário | Corpo |
|---|---|---|
| `400 Bad Request` | Quantidade de estoque menor ou igual a zero. | `A quantidade deve ser maior que zero.` |
| `400 Bad Request` | Preço de produto menor ou igual a zero. | `O preço deve ser maior que zero.` |
| `401 Unauthorized` | JWT ausente, inválido ou revogado em rota protegida. | `Token ausente, inválido ou revogado.` |
| `403 Forbidden` | Usuário autenticado sem permissão. | `Usuário sem permissão para esta operação.` |
| `404 Not Found` | Produto não encontrado. | `Produto não encontrado.` |
| `404 Not Found` | Estoque não cadastrado para o produto. | `Estoque não encontrado para o produto {productId}.` |
| `409 Conflict` | E-mail já cadastrado. | `Já existe um usuário com este e-mail.` |
| `500 Internal Server Error` | Falha inesperada. | `Ocorreu um erro interno. Tente novamente mais tarde.` |

Falhas inesperadas são registradas no serviço e não incluem mensagens de exceções, infraestrutura ou credenciais na resposta.

## Fluxo recomendado

1. Cadastre-se em `POST /auth/signup`, ou autentique-se em `POST /auth/signin`.
2. Armazene o `token` recebido e envie-o como Bearer nas operações `/ws/**`.
3. Use o mesmo `POST /auth/signin` para uma conta de administrador; operações de escrita exigem que o token pertença a um administrador.
4. Ao sair, chame `POST /auth/logout` com o token atual e descarte-o no cliente.

## Autenticação

### Criar usuário

**`POST /auth/signup`**

Cria uma conta comum. O cadastro não autentica o cliente nem retorna um token.

**Autenticação:** Não.

**Headers**

| Header | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `Content-Type` | string | Sim | `application/json` |
| `Accept` | string | Não | Recomendado: `application/json` |

**Request body**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `name` | string | Sim | Nome não vazio. |
| `email` | string | Sim | E-mail válido e ainda não cadastrado. |
| `password` | string | Sim | Senha não vazia. |

```json
{
  "name": "Ana Silva",
  "email": "ana@example.com",
  "password": "senha-segura"
}
```

**Sucesso — `201 Created`**

Retorna o usuário criado. A senha não é serializada.

```json
{
  "id": 42,
  "name": "Ana Silva",
  "email": "ana@example.com",
  "type": "Common"
}
```

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | Nome, e-mail ou senha inválidos. | `"O e-mail informado é inválido."` |
| `409 Conflict` | E-mail já cadastrado. | `"Já existe um usuário com este e-mail."` |
| `5xx` | Falha inesperada no serviço ou persistência. | `"Ocorreu um erro interno. Tente novamente mais tarde."` |

### Login de usuário

**`POST /auth/signin`**

Autentica por e-mail e senha e emite um JWT. Use esta rota também para autenticar a conta administradora.

**Autenticação:** Não.

**Headers:** `Content-Type: application/json` obrigatório; `Accept: application/json` recomendado.

**Request body**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `email` | string | Sim | E-mail da conta. |
| `password` | string | Sim | Senha da conta. |

```json
{
  "email": "ana@example.com",
  "password": "senha-segura"
}
```

**Sucesso — `200 OK`**

`token` é uma string JWT. O objeto `user` contém os dados da conta; a senha é omitida.

```json
{
  "user": {
    "id": 42,
    "name": "Ana Silva",
    "email": "ana@example.com",
    "type": "Common"
  },
  "token": "<jwt>"
}
```

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | Corpo da requisição inválido. | `"Corpo da requisição inválido."` |
| `401 Unauthorized` | E-mail ou senha não autenticam. | `"E-mail ou senha inválidos."` |
| `5xx` | Falha inesperada no serviço ou persistência. | `"Ocorreu um erro interno. Tente novamente mais tarde."` |

### Login com usuário administrador

**Não existe endpoint administrativo separado.** Use `POST /auth/signin` com as credenciais de uma conta cujo tipo seja `Admin`. A resposta tem o mesmo formato do login comum, mas `user.type` será `"Admin"`. Somente administradores podem executar as operações `/ws/products`, `/ws/categories` e `/ws/subcategories`.

**Conta criada pela migração inicial**

| Campo | Valor |
|---|---|
| E-mail | `admin@admin.dev` |
| Senha | teste |

Request:

```json
{
  "email": "admin@admin.dev",
  "password": "teste"
}
```

Para falha de autenticação, consulte os erros de [login](#login-de-usuário). Um usuário válido que não seja administrador recebe `403 Forbidden` ao tentar uma operação administrativa.

## Logout e invalidação do token

**`POST /auth/logout`**

Revoga o JWT apresentado. O cliente também deve remover o token armazenado depois do sucesso.

**Autenticação:** Sim — Bearer Token.

**Headers**

| Header | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `Authorization` | string | Sim | `Bearer <jwt>` válido e não expirado. |

**Request body / Query parameters:** Nenhum.

**Sucesso — `204 No Content`**

Sem corpo.

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `401 Unauthorized` | Header ausente/malformado, JWT inválido ou expirado. | `"Token Bearer ausente ou inválido."` |
| `5xx` | Falha ao persistir a revogação. | `"Ocorreu um erro interno. Tente novamente mais tarde."` |

## Câmbio

**`GET /currencies?source={moeda}&target={moeda}`**

Consulta a taxa de conversão entre dois códigos de moeda. Os parâmetros `source` e `target` são obrigatórios.

| Status | Quando ocorre | Exemplo de resposta |
|---|---|---|
| `400 Bad Request` | Código de moeda vazio ou parâmetro obrigatório ausente. | `"Código de moeda inválido."` ou `"Parâmetro obrigatório ausente: source."` |
| `404 Not Found` | Não há cotação disponível para a moeda informada. | `"Cotação não encontrada para a moeda informada."` |
| `500 Internal Server Error` | Falha inesperada. | `"Ocorreu um erro interno. Tente novamente mais tarde."` |

## Produtos

### Listar, pesquisar, filtrar e ordenar produtos

**`GET /products`**

Lista produtos ativos em páginas. Também exclui produtos cuja subcategoria ou categoria esteja inativa.

Este endpoint não aceita filtro de status: um parâmetro `status` enviado aqui é ignorado e o resultado continua contendo somente produtos ativos. Para listar produtos inativos, use [`GET /ws/products`](#listar-produtos-administrador-incluindo-inativos) (administrador).

**Autenticação:** Não.

**Headers:** `Accept: application/json` recomendado.

**Query parameters**

| Parâmetro | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `name` | string | Não | Trecho do nome, busca sem diferenciar maiúsculas/minúsculas. |
| `categoryId` | integer (`Long`) | Não | Filtra pelo ID da categoria. |
| `subcategoryId` | integer (`Long`) | Não | Filtra pelo ID da subcategoria. |
| `minPrice` | number (`BigDecimal`) | Não | Preço mínimo inclusivo, não negativo; sempre interpretado em BRL. |
| `maxPrice` | number (`BigDecimal`) | Não | Preço máximo inclusivo, não negativo e maior ou igual a `minPrice`; sempre interpretado em BRL. |
| `targetCurrency` | string | Não | Moeda dos preços convertidos na resposta. Padrão: `BRL`. |
| `page` | integer | Não | Página baseada em zero. Padrão: `0`. |
| `size` | integer | Não | Itens por página. Padrão: `10`. |
| `sort` | string | Não | Campo e direção no formato `campo,direção`; campos aceitos: `name`, `price`, `description`; direção `asc` ou `desc`. Padrão: `description,asc`. Pode ser repetido para múltiplos critérios. |

Os filtros de preço e ordenação por `price` são calculados em BRL, inclusive quando o produto está cadastrado em outra moeda. `targetCurrency` afeta o preço convertido apresentado, não a moeda dos filtros. Uma falha de conversão necessária para filtro/ordenação pode resultar em `503`.

```http
GET /products?name=cuia&categoryId=2&subcategoryId=7&minPrice=50&maxPrice=100&sort=price,asc&page=0&size=10&targetCurrency=BRL
```

**Sucesso — `200 OK`**

Retorna uma página Spring Data. `price` é o valor original e `convertedPrice` é o valor calculado em `targetCurrency`.

```json
{
  "content": [
    {
      "id": 17,
      "name": "Cuia artesanal",
      "brand": "Marca Gaúcha",
      "model": "CU-01",
      "description": "Cuia de porongo",
      "currency": "BRL",
      "price": 75.0,
      "image": "https://example.com/cuia.jpg",
      "images": [],
      "subcategoryId": 7,
      "subcategoryName": "Porongo",
      "categoryId": 2,
      "categoryName": "Cuias",
      "productType": "SINGLE_UNIT",
      "active": true,
      "environment": "Product API running in port 8080",
      "promotionMessage": "Nenhuma Promoção Ativa",
      "targetCurrency": "BRL",
      "convertedPrice": 75.0
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10
  },
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 10,
  "numberOfElements": 1,
  "first": true,
  "last": true,
  "empty": false
}
```

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | Parâmetro inválido, faixa de preço invertida/negativa ou campo de ordenação não suportado. | `"O preço mínimo não pode ser superior ao preço máximo"` ou `"Parâmetro inválido: minPrice."` |
| `503 Service Unavailable` | Não foi possível obter conversão para BRL necessária aos filtros ou à ordenação por preço. | `"Não foi possível converter os preços para BRL"` (texto simples) |
| `500 Internal Server Error` | Outra falha inesperada no serviço. | `"Ocorreu um erro interno. Tente novamente mais tarde."` |

### Buscar produto por ID

**`GET /products/{id}`**

Retorna um produto ativo, desde que sua subcategoria e categoria também estejam ativas.

Para consultar um produto inativo, use [`GET /ws/products/{id}`](#buscar-produto-por-id-administrador-incluindo-inativo) (administrador).

**Autenticação:** Não.

**Headers:** `Accept: application/json` recomendado.

**Path e query parameters**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `id` | integer (`Long`) | Sim | ID do produto no path. |
| `targetCurrency` | string | Sim | Código da moeda desejada para `convertedPrice` (por exemplo, `BRL` ou `USD`). |

```http
GET /products/17?targetCurrency=USD
```

**Sucesso — `200 OK`**

Mesmo formato do item em [listar produtos](#listar-pesquisar-filtrar-e-ordenar-produtos). O campo `image` é a URL da imagem principal; sem imagens enviadas, mantém o valor legado da coluna `image` (ou `null`). `images` lista as imagens enviadas por [upload](#imagens-do-produto), ordenadas por `position` (lista vazia quando não houver). Se a conversão do detalhe falhar, o serviço atualmente retorna o produto com `convertedPrice: -1` e registra a falha no campo `environment`.

```json
{
  "id": 17,
  "name": "Cuia artesanal",
  "brand": "Marca Gaúcha",
  "model": "CU-01",
  "description": "Cuia de porongo",
  "currency": "BRL",
  "price": 75.0,
  "image": "https://<projeto>.supabase.co/storage/v1/object/public/product-images/products/17/3f1c...png",
  "images": [
    {
      "id": 4,
      "url": "https://<projeto>.supabase.co/storage/v1/object/public/product-images/products/17/3f1c...png",
      "isPrimary": true,
      "position": 1
    }
  ],
  "subcategoryId": 7,
  "subcategoryName": "Porongo",
  "categoryId": 2,
  "categoryName": "Cuias",
  "productType": "SINGLE_UNIT",
  "active": true,
  "environment": "Product API running in port 8080",
  "promotionMessage": "Nenhuma Promoção Ativa",
  "targetCurrency": "USD",
  "convertedPrice": 15.0
}
```

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | ID ou parâmetro inválido, ou `targetCurrency` ausente. | `"Parâmetro inválido: id."` ou `"Parâmetro obrigatório ausente: targetCurrency."` |
| `404 Not Found` | Produto não existe, está inativo ou pertence a categoria/subcategoria inativa. | `"Produto não encontrado."` |
| `500 Internal Server Error` | Falha inesperada. | `"Ocorreu um erro interno. Tente novamente mais tarde."` |

### Criar produto

**`POST /ws/products`**

Cria um produto ativo. A subcategoria informada precisa existir e estar ativa, assim como sua categoria.

**Autenticação:** Sim — Bearer Token de administrador.

**Headers:** `Authorization: Bearer <jwt>` e `Content-Type: application/json` obrigatórios; `Accept: application/json` recomendado.

**Request body**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `name` | string | Sim | Nome não vazio. |
| `brand` | string | Sim | Marca não vazia. |
| `model` | string | Sim | Modelo não vazio. |
| `description` | string | Sim | Descrição não vazia. |
| `currency` | string | Sim | Código de moeda não vazio, por exemplo `BRL`. |
| `price` | number (`BigDecimal`) | Sim | Preço positivo, persistido com duas casas decimais. |
| `image` | string ou `null` | Não | URL ou referência da imagem. |
| `subcategoryId` | integer (`Long`) | Sim | ID de subcategoria ativa, vinculada a categoria ativa. |
| `productType` | string (enum) | Sim | `SINGLE_UNIT` ou `MULTI_UNIT`. |

```json
{
  "name": "Cuia artesanal",
  "brand": "Marca Gaúcha",
  "model": "CU-01",
  "description": "Cuia de porongo",
  "currency": "BRL",
  "price": 75.0,
  "image": "https://example.com/cuia.jpg",
  "subcategoryId": 7,
  "productType": "SINGLE_UNIT"
}
```

**Sucesso — `201 Created`**

Retorna a entidade de produto criada, incluindo a associação `subcategory`. O campo `image` traz a URL da imagem principal (ou o valor legado da coluna, ou `null`) e `images` lista as imagens enviadas por [upload](#imagens-do-produto) (vazia em um produto recém-criado).

```json
{
  "id": 17,
  "name": "Cuia artesanal",
  "brand": "Marca Gaúcha",
  "model": "CU-01",
  "description": "Cuia de porongo",
  "currency": "BRL",
  "price": 75.00,
  "image": "https://example.com/cuia.jpg",
  "productType": "SINGLE_UNIT",
  "active": true,
  "subcategory": {
    "id": 7,
    "category": {
      "id": 2,
      "name": "Cuias",
      "slug": "cuias",
      "active": true,
      "createdAt": "2026-01-15T12:00:00Z",
      "updatedAt": "2026-01-15T12:00:00Z"
    },
    "name": "Porongo",
    "slug": "porongo",
    "active": true,
    "createdAt": "2026-01-15T12:00:00Z",
    "updatedAt": "2026-01-15T12:00:00Z"
  },
  "images": []
}
```

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | Campo obrigatório inválido, preço não positivo ou subcategoria inexistente/inativa. | `"Nome inválido"`, `"O preço deve ser maior que zero."` ou `"A subcategoria informada não existe ou está inativa"` |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | `"Token ausente, inválido ou revogado."` |
| `403 Forbidden` | Token válido de usuário que não é administrador. | `"Usuário sem permissão para esta operação"` |
| `500 Internal Server Error` | Falha inesperada no serviço. | `"Ocorreu um erro interno. Tente novamente mais tarde."` |

### Editar produto

**`PUT /ws/products/{id}`**

Atualiza os campos editáveis do produto. O request é efetivamente completo: envie novamente todos os campos obrigatórios. `productType`, `id` e `active` não são alterados por este endpoint.

**Autenticação:** Sim — Bearer Token de administrador.

**Headers:** `Authorization: Bearer <jwt>` e `Content-Type: application/json` obrigatórios; `Accept: application/json` recomendado.

**Path e request body**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `id` | integer (`Long`) | Sim | ID do produto no path. |
| `name` | string | Sim | Nome não vazio. |
| `brand` | string | Sim | Marca não vazia. |
| `model` | string | Sim | Modelo não vazio. |
| `description` | string | Sim | Descrição não vazia. |
| `currency` | string | Sim | Código de moeda não vazio. |
| `price` | number (`BigDecimal`) | Sim | Preço positivo. |
| `image` | string ou `null` | Não | URL legada da imagem; nulo remove o valor atual. Só aparece como `image` enquanto o produto não tiver imagens enviadas por [upload](#imagens-do-produto). |
| `subcategoryId` | integer (`Long`) | Sim | Subcategoria ativa e de categoria ativa. |

```json
{
  "name": "Cuia artesanal premium",
  "brand": "Marca Gaúcha",
  "model": "CU-01-P",
  "description": "Cuia de porongo selecionado",
  "currency": "BRL",
  "price": 89.9,
  "image": "https://example.com/cuia-premium.jpg",
  "subcategoryId": 7
}
```

**Sucesso — `200 OK`**

Retorna a entidade de produto atualizada, no formato demonstrado em [criar produto](#criar-produto).

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | Campo obrigatório inválido ou subcategoria inválida/inativa. | `"O preço deve ser maior que zero."` ou `"A subcategoria informada não existe ou está inativa"` |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | `"Token ausente, inválido ou revogado."` |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa. | `"Usuário sem permissão para esta operação"` |
| `404 Not Found` | Produto não encontrado. | `"Produto não encontrado."` |
| `500 Internal Server Error` | Falha inesperada no serviço. | `"Ocorreu um erro interno. Tente novamente mais tarde."` |

### Ativar ou desativar produto

**`PATCH /ws/products/{id}/status`**

Altera apenas o status do produto. Para reativar, a categoria e a subcategoria associadas também precisam estar ativas.

**Autenticação:** Sim — Bearer Token de administrador.

**Headers:** `Authorization: Bearer <jwt>` e `Content-Type: application/json` obrigatórios; `Accept: application/json` recomendado.

**Path e request body**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `id` | integer (`Long`) | Sim | ID do produto no path. |
| `active` | boolean | Sim | `false` desativa; `true` ativa. |

```json
{
  "active": false
}
```

**Sucesso — `200 OK`**

Retorna a entidade do produto com `active` atualizado. Exemplo: o mesmo objeto descrito em [criar produto](#criar-produto), com `"active": false`.

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | `active` ausente ou nulo. | `"Status inválido"` |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | `"Token ausente, inválido ou revogado."` |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa. | `"Usuário sem permissão para esta operação"` |
| `404 Not Found` | Produto não encontrado. | `"Produto não encontrado."` |
| `409 Conflict` | Tentativa de reativar com categoria ou subcategoria inativa. | `"Não é possível reativar um produto com categoria ou subcategoria inativa"` |
| `500 Internal Server Error` | Falha inesperada no serviço. | `"Ocorreu um erro interno. Tente novamente mais tarde."` |

### Listar produtos (administrador), incluindo inativos

**`GET /ws/products`**

Mesma listagem paginada de [`GET /products`](#listar-pesquisar-filtrar-e-ordenar-produtos), com os mesmos filtros, paginação, ordenação e formato de resposta, mais o filtro `status`. Sem `status`, o resultado é idêntico ao do catálogo público.

**Autenticação:** Sim — Bearer Token de administrador.

**Headers:** `Authorization: Bearer <jwt>` obrigatório; `Accept: application/json` recomendado.

**Query parameters:** todos os de `GET /products` (`name`, `categoryId`, `subcategoryId`, `minPrice`, `maxPrice`, `targetCurrency`, `page`, `size`, `sort`), mais:

| Parâmetro | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `status` | string | Não | `ACTIVE`, `INACTIVE` ou `ALL`. Padrão: `ACTIVE`. |

Semântica do `status`:

| Valor | Produtos retornados |
|---|---|
| `ACTIVE` (padrão) | Produto, subcategoria e categoria ativos — exatamente o mesmo critério de `GET /products`. |
| `INACTIVE` | Produtos com `active = false`, independentemente do status da subcategoria/categoria. |
| `ALL` | Todos os produtos, sem filtro de ativo. |

Um produto com `active = true` cuja categoria ou subcategoria esteja inativa **não** aparece em `ACTIVE` nem em `INACTIVE`; ele aparece **somente** em `ALL`. O campo `active` da resposta reflete apenas o status do próprio produto (o mesmo alterado por `PATCH /ws/products/{id}/status`); use `categoryId`/`subcategoryId` para conferir o status da classificação.

O `status` combina com todos os outros filtros, e `totalElements`/`totalPages` refletem o filtro aplicado.

```http
GET /ws/products?status=INACTIVE&name=cuia&categoryId=2&sort=name,asc&page=0&size=10
Authorization: Bearer <jwt>
```

**Sucesso — `200 OK`**

Mesmo formato de página de `GET /products`. Exemplo de item de produto inativo:

```json
{
  "content": [
    {
      "id": 18,
      "name": "Cuia descontinuada",
      "brand": "Marca Gaúcha",
      "model": "CU-02",
      "description": "Cuia de porongo",
      "currency": "BRL",
      "price": 80.0,
      "image": "https://example.com/cuia-2.jpg",
      "images": [],
      "subcategoryId": 7,
      "subcategoryName": "Porongo",
      "categoryId": 2,
      "categoryName": "Cuias",
      "productType": "SINGLE_UNIT",
      "active": false,
      "environment": "Product API running in port 8080",
      "promotionMessage": "Nenhuma Promoção Ativa",
      "targetCurrency": "BRL",
      "convertedPrice": 80.0
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 10
}
```

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | `status` fora de `ACTIVE`/`INACTIVE`/`ALL`, outro parâmetro com tipo inválido, faixa de preço inválida ou ordenação não suportada. | `"Parâmetro inválido: status"` (texto simples) |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | `"Token ausente, inválido ou revogado."` |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa (qualquer valor de `status`). | `"Usuário sem permissão para esta operação"` |
| `503 Service Unavailable` | Conversão para BRL indisponível para filtro/ordenação por preço. | `"Não foi possível converter os preços para BRL"` |
| `500 Internal Server Error` | Falha inesperada no serviço. | `"Ocorreu um erro interno. Tente novamente mais tarde."` |

### Buscar produto por ID (administrador), incluindo inativo

**`GET /ws/products/{id}`**

Retorna o produto mesmo que ele, sua subcategoria ou sua categoria estejam inativos. O formato da resposta é o mesmo de [`GET /products/{id}`](#buscar-produto-por-id); `active` indica o status do produto.

**Autenticação:** Sim — Bearer Token de administrador.

**Headers:** `Authorization: Bearer <jwt>` obrigatório; `Accept: application/json` recomendado.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `id` | integer (`Long`) | Sim | ID do produto no path. |
| `targetCurrency` | string | Não | Moeda de `convertedPrice`. Padrão: `BRL`. |

```http
GET /ws/products/18?targetCurrency=BRL
Authorization: Bearer <jwt>
```

**Sucesso — `200 OK`**: o objeto de produto, com `"active": false` quando o produto está desativado.

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | ID não numérico. | `"Parâmetro inválido: id"` (texto simples) |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | `"Token ausente, inválido ou revogado."` |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa. | `"Usuário sem permissão para esta operação"` |
| `404 Not Found` | Produto não existe. | `"Produto não encontrado."` |
| `500 Internal Server Error` | Falha inesperada no serviço. | `"Ocorreu um erro interno. Tente novamente mais tarde."` |

### Imagens do produto

Imagens enviadas pelo administrador e armazenadas no Supabase Storage (API compatível com S3). O banco guarda apenas a chave do objeto (`products/{productId}/{uuid}.{ext}`); a URL pública é montada como `{STORAGE_PUBLIC_BASE_URL}/{chave}`.

Regras:

- Formatos aceitos: JPEG, PNG e WebP, identificados pelo conteúdo (magic bytes), não pela extensão nem pelo `Content-Type` enviado. O arquivo é gravado no storage com o `Content-Type` detectado.
- Até 5 MB por imagem e 8 imagens por produto (configuráveis por `app.images.max-size-bytes` e `app.images.max-per-product`).
- A primeira imagem enviada vira a principal; há no máximo uma principal por produto.
- Ao remover a principal, a próxima imagem (menor `position`) é promovida.
- O campo `image` das respostas de produto e dos snapshots de carrinho e pedido é a URL da imagem principal; sem imagens, continua com o valor legado.

**Autenticação:** Sim — Bearer Token de administrador em todos os endpoints abaixo.

| Método | Endpoint | Descrição | Sucesso |
|---|---|---|---|
| POST | `/ws/products/{id}/images` | Envia uma imagem (`multipart/form-data`, campo `file`). | `201 Created` |
| GET | `/ws/products/{id}/images` | Lista as imagens do produto, ordenadas por `position`. | `200 OK` |
| PATCH | `/ws/products/{id}/images/{imageId}/primary` | Define a imagem principal. | `200 OK` |
| DELETE | `/ws/products/{id}/images/{imageId}` | Remove a imagem (linha e arquivo). | `204 No Content` |

```bash
curl -X POST http://localhost:8080/ws/products/17/images \
  -H "Authorization: Bearer <jwt>" \
  -F "file=@cuia.png"
```

**Resposta do upload e do PATCH (mesmo formato de cada item da listagem)**

```json
{
  "id": 4,
  "url": "https://<projeto>.supabase.co/storage/v1/object/public/product-images/products/17/3f1c...png",
  "isPrimary": true,
  "position": 1
}
```

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | Campo `file` ausente ou vazio, ou conteúdo que não é JPEG/PNG/WebP. | `"Formato de imagem não suportado. Envie JPEG, PNG ou WebP."` |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | `"Token ausente, inválido ou revogado."` |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa. | `"Usuário sem permissão para esta operação"` |
| `404 Not Found` | Produto ou imagem não encontrados (inclusive imagem de outro produto). | `"Produto não encontrado."` / `"Imagem não encontrada."` |
| `409 Conflict` | O produto já tem o número máximo de imagens. | `"Limite de 8 imagens por produto atingido."` |
| `413 Payload Too Large` | Imagem acima do limite. | `"A imagem excede o tamanho máximo de 5 MB."` |
| `502 Bad Gateway` | Falha ao enviar o arquivo ao armazenamento. | `"Falha ao enviar a imagem ao armazenamento."` |

Se a gravação no banco falhar após o envio, o arquivo é removido do armazenamento. Na remoção, a linha é apagada primeiro; uma falha ao apagar o arquivo é apenas registrada em log.

## Categorias e subcategorias

### Dados pré-cadastrados

Na migração inicial do catálogo, são inseridas as seguintes categorias e subcategorias. Os nomes são os valores enviados pela API; os slugs são gerados no seed. As entidades começam ativas.

| Categoria | Slug da categoria | Subcategorias pré-cadastradas (slug) |
|---|---|---|
| Erva-mate | `erva-mate` | Nativa (`nativa`), Para tereré (`para-terere`), Com sabores (`com-sabores`), Moída grossa (`moida-grossa`), Pura folha (`pura-folha`), Tradicional (`tradicional`) |
| Cuias | `cuias` | Porongo (`porongo`), Madeira (`madeira`), Tereré (`terere`) |
| Bombas | `bombas` | Aço comum (`aco-comum`), Aço inoxidável (`aco-inoxidavel`), Prata (`prata`), De alpaca (`de-alpaca`) |
| Garrafa térmica | `garrafa-termica` | Termolar (`termolar`), Mokha (`mokha`) |
| Mateiras | `mateiras` | Abertas (`abertas`), Fechadas (`fechadas`) |
| Porta erva-mate | `porta-erva-mate` | Couro (`couro`), Lata (`lata`), Porongo (`porongo`), Madeira (`madeira`) |
| Acessórios para chimarrão | `acessorios-para-chimarrao` | Suportes para cuia (`suportes-para-cuia`), Tapa erva (`tapa-erva`), Vira-mate (`vira-mate`) |

Os IDs são gerados pelo banco e devem ser obtidos em tempo de execução: consulte `GET /categories` e, para cada `id`, `GET /categories/{id}/subcategories`. Use os IDs retornados nessas consultas ao filtrar produtos ou criar/editar produtos e subcategorias; não derive IDs da ordem da tabela acima.

### Listar categorias

**`GET /categories`**

Lista categorias ativas, ordenadas por nome.

**Autenticação:** Não.

**Headers:** `Accept: application/json` recomendado.

**Request body / Query parameters:** Nenhum.

**Sucesso — `200 OK`**

```json
[
  {
    "id": 2,
    "name": "Cuias",
    "slug": "cuias",
    "active": true,
    "createdAt": "2026-01-15T12:00:00Z",
    "updatedAt": "2026-01-15T12:00:00Z"
  }
]
```

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `500 Internal Server Error` | Falha inesperada ao consultar categorias. | `"Ocorreu um erro interno. Tente novamente mais tarde."` |

### Listar subcategorias de uma categoria

**`GET /categories/{id}/subcategories`**

Lista subcategorias ativas, ordenadas por nome. Se a categoria não existir ou estiver inativa, retorna `404`.

**Autenticação:** Não.

**Headers:** `Accept: application/json` recomendado.

**Path parameters**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `id` | integer (`Long`) | Sim | ID da categoria. |

**Sucesso — `200 OK`**

```json
[
  {
    "id": 7,
    "categoryId": 2,
    "categoryName": "Cuias",
    "name": "Porongo",
    "slug": "porongo",
    "active": true,
    "createdAt": "2026-01-15T12:00:00Z",
    "updatedAt": "2026-01-15T12:00:00Z"
  }
]
```

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | `id` não numérico. | `"Parâmetro inválido: id"` (texto simples) |
| `404 Not Found` | Categoria inexistente ou inativa. | `"Categoria não encontrada ou inativa."` |
| `500 Internal Server Error` | Falha inesperada ao consultar. | `"Ocorreu um erro interno. Tente novamente mais tarde."` |

### Criar categoria

**`POST /ws/categories`**

Cria uma categoria ativa. O `slug` é gerado a partir do nome.

**Autenticação:** Sim — Bearer Token de administrador.

**Headers:** `Authorization: Bearer <jwt>` e `Content-Type: application/json` obrigatórios; `Accept: application/json` recomendado.

**Request body**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `name` | string | Sim | Nome não vazio, com até 180 caracteres. Deve ser único. |

```json
{
  "name": "Utensílios de mate"
}
```

**Sucesso — `201 Created`**

```json
{
  "id": 2,
  "name": "Utensílios de mate",
  "slug": "utensilios-de-mate",
  "active": true,
  "createdAt": "2026-01-15T12:00:00Z",
  "updatedAt": "2026-01-15T12:00:00Z"
}
```

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | Nome vazio ou com mais de 180 caracteres. | `"Nome inválido"` (texto simples) |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | `"Token ausente, inválido ou revogado."` |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa. | `"Usuário sem permissão para esta operação"` |
| `409 Conflict` | Nome/slug já utilizado. | `"Já existe uma categoria com esse nome ou slug"` |

### Editar categoria

**`PUT /ws/categories/{id}`**

Atualiza nome e slug de uma categoria existente.

**Autenticação:** Sim — Bearer Token de administrador.

**Headers:** `Authorization: Bearer <jwt>` e `Content-Type: application/json` obrigatórios; `Accept: application/json` recomendado.

**Path e request body**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `id` | integer (`Long`) | Sim | ID da categoria no path. |
| `name` | string | Sim | Nome não vazio, com até 180 caracteres e único. |

```json
{
  "name": "Utensílios para chimarrão"
}
```

**Sucesso — `200 OK`**

Retorna o mesmo formato de objeto da resposta de [criar categoria](#criar-categoria).

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | Nome inválido. | `"Nome inválido"` |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | `"Token ausente, inválido ou revogado."` |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa. | `"Usuário sem permissão para esta operação"` |
| `404 Not Found` | Categoria não encontrada. | `"Categoria não encontrada"` (texto simples) |
| `409 Conflict` | Nome/slug já utilizado. | `"Já existe uma categoria com esse nome ou slug"` |

### Ativar ou desativar categoria

**`PATCH /ws/categories/{id}/status`**

Altera o status da categoria. Categorias inativas deixam de aparecer nas consultas públicas; produtos associados também deixam de aparecer.

**Autenticação:** Sim — Bearer Token de administrador.

**Headers:** `Authorization: Bearer <jwt>` e `Content-Type: application/json` obrigatórios; `Accept: application/json` recomendado.

**Path e request body**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `id` | integer (`Long`) | Sim | ID da categoria no path. |
| `active` | boolean | Sim | `false` desativa; `true` ativa. |

```json
{
  "active": false
}
```

**Sucesso — `200 OK`**

Retorna o objeto de categoria com `active` atualizado.

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | `active` ausente ou nulo. | `"Status inválido"` |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | `"Token ausente, inválido ou revogado."` |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa. | `"Usuário sem permissão para esta operação"` |
| `404 Not Found` | Categoria não encontrada. | `"Categoria não encontrada"` |

### Criar subcategoria

**`POST /ws/subcategories`**

Cria uma subcategoria ativa associada a uma categoria existente e ativa. O `slug` é gerado a partir do nome.

**Autenticação:** Sim — Bearer Token de administrador.

**Headers:** `Authorization: Bearer <jwt>` e `Content-Type: application/json` obrigatórios; `Accept: application/json` recomendado.

**Request body**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `categoryId` | integer (`Long`) | Sim | ID da categoria ativa. |
| `name` | string | Sim | Nome não vazio, com até 180 caracteres; único dentro da categoria. |

```json
{
  "categoryId": 2,
  "name": "Cuia personalizada"
}
```

**Sucesso — `201 Created`**

```json
{
  "id": 7,
  "categoryId": 2,
  "categoryName": "Cuias",
  "name": "Cuia personalizada",
  "slug": "cuia-personalizada",
  "active": true,
  "createdAt": "2026-01-15T12:00:00Z",
  "updatedAt": "2026-01-15T12:00:00Z"
}
```

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | Nome ou categoria inválidos; categoria inexistente/inativa. | `"A categoria informada não existe ou está inativa"` (texto simples) |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | `"Token ausente, inválido ou revogado."` |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa. | `"Usuário sem permissão para esta operação"` |
| `409 Conflict` | Nome/slug já utilizado na categoria. | `"Já existe uma subcategoria com esse nome ou slug nesta categoria"` |

### Editar subcategoria

**`PUT /ws/subcategories/{id}`**

Atualiza nome e categoria da subcategoria. A categoria de destino precisa estar ativa.

**Autenticação:** Sim — Bearer Token de administrador.

**Headers:** `Authorization: Bearer <jwt>` e `Content-Type: application/json` obrigatórios; `Accept: application/json` recomendado.

**Path e request body**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `id` | integer (`Long`) | Sim | ID da subcategoria no path. |
| `categoryId` | integer (`Long`) | Sim | ID da categoria de destino, ativa. |
| `name` | string | Sim | Nome não vazio, com até 180 caracteres; único dentro da categoria. |

```json
{
  "categoryId": 2,
  "name": "Porongo selecionado"
}
```

**Sucesso — `200 OK`**

Retorna o mesmo formato de objeto da resposta de [criar subcategoria](#criar-subcategoria).

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | Requisição, nome ou categoria inválidos; categoria inexistente/inativa. | `"Nome inválido"` ou `"A categoria informada não existe ou está inativa"` |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | `"Token ausente, inválido ou revogado."` |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa. | `"Usuário sem permissão para esta operação"` |
| `404 Not Found` | Subcategoria não encontrada. | `"Subcategoria não encontrada"` |
| `409 Conflict` | Nome/slug já utilizado na categoria. | `"Já existe uma subcategoria com esse nome ou slug nesta categoria"` |

### Ativar ou desativar subcategoria

**`PATCH /ws/subcategories/{id}/status`**

Altera o status da subcategoria. Uma subcategoria só pode ser ativada se sua categoria também estiver ativa.

**Autenticação:** Sim — Bearer Token de administrador.

**Headers:** `Authorization: Bearer <jwt>` e `Content-Type: application/json` obrigatórios; `Accept: application/json` recomendado.

**Path e request body**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `id` | integer (`Long`) | Sim | ID da subcategoria no path. |
| `active` | boolean | Sim | `false` desativa; `true` ativa. |

```json
{
  "active": true
}
```

**Sucesso — `200 OK`**

Retorna o objeto de subcategoria com `active` atualizado.

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | `active` ausente ou nulo. | `"Status inválido"` |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | `"Token ausente, inválido ou revogado."` |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa. | `"Usuário sem permissão para esta operação"` |
| `404 Not Found` | Subcategoria não encontrada. | `"Subcategoria não encontrada"` |
| `409 Conflict` | Tentativa de ativar sob categoria inativa. | `"Não é possível ativar uma subcategoria de uma categoria inativa"` |

## Estoque e reservas

O `inventory-api` mantém quantidades físicas, reservas temporárias e movimentações. O `productId` é uma referência ao catálogo; dados comerciais e ativação do produto continuam sob responsabilidade do `product-api`. Disponibilidade no estoque é calculada como `quantityOnHand - quantityReserved` e não indica, isoladamente, que o produto está ativo para venda.

As rotas administrativas exigem JWT de administrador. Consultas de disponibilidade, analytics e operações de reserva são destinadas a chamadas serviço-a-serviço autenticadas com `X-Service-Token`; quando encaminhadas pelo Gateway, também passam pela validação da credencial Gateway. A consulta individual de uma reserva também aceita acesso administrativo via JWT. Clientes externos não devem enviar nem configurar cabeçalhos ou credenciais internos.

### Consultar estoque e disponibilidade

| Método e rota | Acesso | Descrição |
|---|---|---|
| `GET /ws/inventory/{productId}` | Administrador | Consulta saldo, tipo, limite de estoque baixo e timestamps. |
| `GET /ws/inventory` | Administrador | Lista com paginação e filtros `productType` e `lowStock`. |
| `GET /ws/inventory/{productId}/availability` | Serviço interno | Consulta saldo e disponibilidade de um produto. |
| `POST /ws/inventory/availability/batch` | Serviço interno | Consulta disponibilidade de até 500 produtos. |

O endpoint de disponibilidade retorna o mesmo formato da consulta administrativa:

```json
{
  "productId": 101,
  "productType": "MULTI_UNIT",
  "quantityOnHand": 10,
  "quantityReserved": 3,
  "availableQuantity": 7,
  "lowStockThreshold": 0,
  "lowStock": false,
  "createdAt": "2026-01-15T12:00:00Z",
  "updatedAt": "2026-01-15T12:00:00Z"
}
```

Consulta em lote:

```json
{
  "productIds": [101, 102]
}
```

IDs duplicados, inválidos, lista vazia ou mais de 500 IDs resultam em `400 Bad Request` com uma mensagem como `"A lista de productIds não pode conter duplicados."`; se algum estoque não estiver cadastrado, a resposta é `404 Not Found` com `"Estoque não encontrado para: [101, 102]"`.

### Administrar quantidades

As operações de escrita abaixo exigem JWT de administrador. O estoque precisa estar registrado antes de receber movimentações. Para registrar o produto no estoque, use `POST /ws/inventory/{productId}/registration`; o serviço consulta `productType` no catálogo e não permite alterá-lo por uma operação administrativa de estoque.

| Método e rota | Descrição |
|---|---|
| `POST /ws/inventory/{productId}/registration` | Registra o produto no estoque, se ainda não existir. |
| `POST /ws/inventory/{productId}/entries` | Registra uma entrada física. |
| `POST /ws/inventory/{productId}/exits` | Registra saída manual, limitada ao saldo não reservado. |
| `POST /ws/inventory/{productId}/adjustments` | Define nova quantidade física, sem ficar abaixo do reservado. |
| `PATCH /ws/inventory/{productId}/low-stock-threshold` | Configura o limite de estoque baixo. |
| `GET /ws/inventory/{productId}/movements` | Consulta o histórico de movimentações do produto. |
| `GET /ws/inventory/movements` | Consulta movimentações gerais. |

#### Registrar produto no estoque

**`POST /ws/inventory/{productId}/registration`**

- Não recebe corpo. Envie a requisição sem JSON.
- Em caso de sucesso, retorna `200 OK` com um `InventoryResponse` no corpo. Um novo registro começa com `quantityOnHand`, `quantityReserved` e `lowStockThreshold` iguais a zero. Se já existir um registro com o mesmo tipo do catálogo, a resposta contém o registro existente, sem criar outro.
- `{productId}` deve ser um inteiro positivo. O tipo do produto é obtido no catálogo.

| Status | Quando ocorre | Exemplo de resposta |
|---|---|---|
| `400 Bad Request` | `productId` inválido. | `"productId deve ser positivo."` |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa. | `"Usuário sem permissão para esta operação."` |
| `404 Not Found` | Produto não encontrado no catálogo. | `"Produto não encontrado."` |
| `409 Conflict` | O tipo do produto no catálogo difere do tipo já registrado no estoque. | `"O tipo de produto informado diverge do tipo registrado no estoque."` |
| `502 Bad Gateway` / `503 Service Unavailable` | Dados incompletos ou rejeição do catálogo (`502`), ou falha/configuração ausente na consulta ao catálogo (`503`). | Mensagem segura indicando a falha na consulta ao catálogo. |

#### Registrar entrada física

**`POST /ws/inventory/{productId}/entries`**

O corpo é obrigatório; o DTO da operação tem os campos `quantity` e `reason`:

```json
{
  "quantity": 5,
  "reason": "Reposição de mercadoria"
}
```

`quantity` é um inteiro positivo de 64 bits (`long`, mínimo `1`). Para `MULTI_UNIT`, o máximo aceito é `Long.MAX_VALUE - quantityOnHand` do saldo atual, para que o novo saldo não exceda o limite representável. Para `SINGLE_UNIT`, o saldo físico total não pode passar de `1`. `reason` é texto livre obrigatório, não pode ser vazio ou conter apenas espaços e aceita até 500 caracteres.

Em caso de sucesso, retorna `200 OK` com um `InventoryResponse` atualizado no corpo. A entrada também é registrada no histórico como `ENTRY` com delta positivo.

| Status | Quando ocorre | Exemplo de resposta |
|---|---|---|
| `400 Bad Request` | `productId` inválido; `quantity` ausente, inválido ou menor/igual a zero; `reason` ausente, em branco ou maior que 500 caracteres; ou a soma excede o limite de inteiro de 64 bits. | `"productId deve ser positivo."`, `"A quantidade deve ser maior que zero."`, `"O motivo é obrigatório e deve ter no máximo 500 caracteres."` ou `"A quantidade informada excede o limite suportado."`, conforme o caso. |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa. | `"Usuário sem permissão para esta operação."` |
| `404 Not Found` | O estoque ainda não está registrado para o `productId`. | `"Estoque não encontrado para o produto {productId}."` |
| `409 Conflict` | A entrada faria o saldo de um produto `SINGLE_UNIT` ultrapassar uma unidade. | `"Produtos SINGLE_UNIT podem ter no máximo uma unidade física."` |

Os corpos de erro são texto simples UTF-8. Os exemplos acima mostram mensagens efetivamente retornadas; o Gateway mantém o status e o corpo enviados pelo serviço. Requisições sem JWT válido recebem `401 Unauthorized` com `"Token ausente, inválido ou revogado."`.

Exemplo de entrada ou saída manual:

```json
{
  "quantity": 5,
  "reason": "Reposição de mercadoria"
}
```

Exemplo de ajuste e de configuração do limite:

```json
{
  "newQuantity": 12,
  "reason": "Contagem física"
}
```

```json
{
  "threshold": 3
}
```

As consultas de movimentos aceitam `productId`, `movementType`, `from` e `to` (timestamps ISO-8601), além de `page`, `size` e `sort`. A consulta por produto também aceita os filtros de período e tipo. Tipos de movimento incluem `ENTRY`, `MANUAL_EXIT`, `ADJUSTMENT` e `RESERVATION_CONFIRMATION`; deltas negativos representam saídas.

### Reservas de checkout

As reservas são destinadas ao `order-api`. Uma criação reserva todos os itens ou nenhum; cada reserva expira após 10 minutos. Repetir a criação para o mesmo `orderId` e mesmos itens é idempotente; reutilizar o pedido com itens diferentes resulta em `409 Conflict`.

| Método e rota | Acesso | Descrição |
|---|---|---|
| `POST /ws/inventory/reservations` | Serviço interno | Cria reserva temporária multi-item. |
| `GET /ws/inventory/reservations/{reservationId}` | Serviço interno ou administrador | Consulta estado, prazo e itens da reserva. |
| `POST /ws/inventory/reservations/{reservationId}/confirm` | Serviço interno | Confirma e baixa o estoque uma única vez. |
| `POST /ws/inventory/reservations/{reservationId}/release` | Serviço interno | Libera quantidade reservada de forma idempotente. |

Request para criar reserva:

```json
{
  "orderId": 9001,
  "items": [
    { "productId": 101, "quantity": 3 },
    { "productId": 102, "quantity": 1 }
  ]
}
```

A resposta contém `reservationId`, `orderId`, `status`, `expiresAt`, timestamps do ciclo de vida e os itens. Estados possíveis: `ACTIVE`, `CONFIRMED`, `RELEASED` e `EXPIRED`. Confirmar reserva expirada retorna `409 Conflict`; tentativas que excedam o disponível, incluindo concorrentes, também são rejeitadas sem reserva parcial. Para `SINGLE_UNIT`, estoque e quantidade solicitada não podem exceder uma unidade por `productId`.

### Consultas para analytics

Todas as rotas abaixo são serviço-a-serviço e têm paginação padrão de 20 itens, salvo o resumo.

| Método e rota | Descrição |
|---|---|
| `GET /ws/inventory/analytics/summary` | Totais de itens cadastrados, quantidades física/reservada/disponível, sem estoque e baixo estoque. |
| `GET /ws/inventory/analytics/out-of-stock` | Estoques cuja disponibilidade é zero. |
| `GET /ws/inventory/analytics/low-stock` | Estoques cuja quantidade física está no limite configurado ou abaixo dele. |
| `GET /ws/inventory/analytics/movements?from={instant}&to={instant}` | Movimentações no período; aceita também `productId`, `movementType` e paginação. |

Erros de validação retornam `400`, produtos/estoques não encontrados retornam `404`, conflitos de saldo e de estado retornam `409`, e chamadas internas sem credencial válida são rejeitadas pelo serviço ou pelo Gateway.

## Fluxo de criação de produto no catálogo e no estoque

O cadastro é uma operação coordenada entre `product-api` e `inventory-api`; atualmente, não existe uma transação distribuída nem criação automática de estoque junto com o produto. O backend responsável pelo fluxo deve executar estas etapas:

1. **Criar o produto no catálogo** com `POST /ws/products`, enviando os dados comerciais, uma subcategoria ativa e o `productType` (`SINGLE_UNIT` ou `MULTI_UNIT`). A resposta `201 Created` inclui o `id` do produto; guarde esse identificador para os próximos passos.
2. **Criar o registro de estoque** com `POST /ws/inventory/{productId}/registration`, substituindo `{productId}` pelo `id` retornado. O Inventory API consulta o tipo de produto no catálogo e cria o saldo inicial zerado. Esta chamada não altera o catálogo nem adiciona unidades.
3. **Registrar o saldo físico inicial** com `POST /ws/inventory/{productId}/entries`, enviando uma quantidade positiva e um motivo, por exemplo:

   ```json
   {
     "quantity": 10,
     "reason": "Estoque inicial"
   }
   ```

   A resposta informa o saldo físico, reservado e disponível. Entradas são registradas no histórico de movimentações.

As três chamadas administrativas usam o JWT de um administrador encaminhado pelo Gateway. Não envie cabeçalhos internos `X-User-*` nem tokens serviço-a-serviço do cliente.

**Falhas e retentativas:** como as etapas não são atômicas, uma falha depois da primeira chamada pode deixar o produto criado sem registro de estoque ou sem saldo. O backend deve persistir o progresso do fluxo e tratar cada resposta antes de avançar.

Não há suporte a chave de idempotência nesses POSTs. O registro do estoque é, na prática, repetível para o mesmo produto enquanto o tipo do catálogo não divergir: ele cria o registro se não existir e devolve o existente se já existir. Depois de um timeout nessa chamada, `GET /ws/inventory/{productId}` permite confirmar se há um registro (responde `200` se houver e `404` se não houver), mas não informa se esse registro foi criado por aquela tentativa ou já existia.

A entrada (`entries`) não é idempotente: repetir a mesma requisição pode somar a quantidade novamente. Seu resultado após timeout é incerto. O saldo retornado por `GET /ws/inventory/{productId}` não identifica uma entrada específica, pois pode incluir outras operações concorrentes. O histórico em `GET /ws/inventory/{productId}/movements` permite procurar movimentações `ENTRY` por quantidade, motivo e horário, mas não recebe um identificador da requisição; portanto, não é uma confirmação confiável quando podem existir entradas iguais ou concorrentes. Não repita automaticamente uma entrada de resultado incerto. Reconcilie o caso com o histórico e o estado operacional antes de decidir se deve lançar uma nova entrada.

O produto é criado ativo no catálogo antes de o estoque ser provisionado. Portanto, pode existir temporariamente um produto ativo sem estoque registrado; a disponibilidade comercial deve considerar tanto o status do catálogo quanto a disponibilidade retornada pelo Inventory API. Se o fluxo precisar garantir que nenhum produto seja exposto antes da conclusão do provisionamento, o sistema que o orquestra deve controlar essa publicação/ativação; o fluxo atual não fornece essa garantia atômica.

Para `SINGLE_UNIT`, cadastre cada peça exclusiva como um produto distinto e registre no máximo uma unidade para cada `productId`. Para `MULTI_UNIT`, o saldo pode ser maior que uma unidade.

## Pedidos

O `order-api` registra pedidos e coordena a reserva de estoque. Requisições de clientes e administradores devem entrar pelo Gateway (`:8080`); não exponha diretamente a porta interna do serviço. As chamadas do Order API para Product, Currency e Inventory usam descoberta pelo Consul na rede interna, sem passar novamente pelo Gateway.

No checkout, o Order API valida cada produto no catálogo, persiste um snapshot dos dados disponíveis do produto e seu preço convertido para BRL, grava o endereço de entrega e calcula os valores do pedido. Em seguida, cria uma reserva atômica de todos os itens no Inventory API. O pedido só passa a `PENDING_PAYMENT` após receber uma reserva ativa. Consultas históricas usam o snapshot persistido e não dependem de o produto ou endereço ainda existirem ou estarem ativos nos serviços de origem.

### Criar pedido

**`POST /ws/orders`**

**Autenticação:** cliente autenticado. O Gateway deriva a identidade do JWT; o Order API exige perfil de cliente.

**Headers**

| Header | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `Authorization` | string | Sim | `Bearer <JWT>` válido de um cliente. |
| `Idempotency-Key` | string | Recomendado | Chave de até 128 caracteres para tornar repetível uma tentativa após timeout. A mesma chave e o mesmo conteúdo retornam o pedido existente; a mesma chave com itens diferentes resulta em `409 Conflict`. |
| `Content-Type` | string | Sim | `application/json`. |

O corpo deve conter entre 1 e 100 itens, com `productId` e `quantity` positivos, sem IDs duplicados, e um endereço de entrega:

```json
{
  "items": [
    { "productId": 101, "quantity": 3 },
    { "productId": 102, "quantity": 1 }
  ],
  "shippingAddress": {
    "zipCode": "99000-000",
    "street": "Rua das Flores",
    "number": "150",
    "complement": "Apartamento 202",
    "neighborhood": "Centro",
    "city": "Passo Fundo",
    "state": "RS"
  }
}
```

`zipCode`, `street`, `number`, `neighborhood`, `city` e `state` são obrigatórios. `complement` é opcional. O CEP deve ter oito dígitos, com hífen opcional; `state` deve ser uma UF brasileira válida. O endereço é armazenado no pedido e não é consultado novamente no cadastro do cliente.

O frete inicial é fixo por região: Sul R$ 20,00; Sudeste R$ 25,00; Centro-Oeste R$ 30,00; Nordeste R$ 35,00; Norte R$ 40,00. A região é determinada pela UF. Os valores persistidos do pedido são em BRL. Cada item guarda preço unitário convertido no checkout, quantidade, desconto e subtotal; o pedido guarda subtotal, desconto, frete, total final e moeda. Não há regra de desconto definida, portanto o desconto é R$ 0,00. O catálogo atual não possui variações de produto; elas não são incluídas no snapshot.

**Sucesso — `201 Created`**

Retorna o pedido com endereço, itens e dados de catálogo persistidos, valores financeiros, status, `reservationId` e expiração. O estado inicial após a reserva é `PENDING_PAYMENT`; isso significa que o estoque está reservado, não que o pagamento foi aprovado.

**Erros comuns**

| Status | Quando ocorre | Exemplo de resposta |
|---|---|---|
| `400 Bad Request` | Lista vazia, mais de 100 itens, IDs ou quantidades inválidas, IDs duplicados, endereço/UF/CEP inválido ou chave de idempotência maior que 128 caracteres. | `"O pedido deve conter ao menos um item."`, `"Cada item deve ter productId e quantity positivos."`, `"CEP inválido."` ou `"Rua do endereço de entrega é obrigatório e deve respeitar o limite de tamanho."` |
| `403 Forbidden` | Usuário autenticado sem perfil de cliente. | `"Somente clientes podem acessar pedidos."` |
| `409 Conflict` | Estoque insuficiente, chave repetida com conteúdo diferente ou reserva não ativa. O pedido provisório é removido nas rejeições definitivas de estoque. | `"Estoque indisponível para o pedido."`, `"Idempotency-Key já foi usado com conteúdo diferente."` ou `"Reserva de estoque não pode ser confirmada."` |
| `422 Unprocessable Entity` | Produto não encontrado ou inativo no catálogo, produto sem estoque cadastrado no Inventory API (`"Produto sem estoque cadastrado no inventário."`) ou reserva recusada pelo Inventory API como inválida (`"Reserva de estoque recusada pelo inventário."`). Nos casos de estoque, o pedido provisório é removido. | `"Produto indisponível no catálogo: {productId}"`, `"Produto sem estoque cadastrado no inventário."` ou `"Reserva de estoque recusada pelo inventário."` |
| `502 Bad Gateway` / `503 Service Unavailable` | Falha de integração. Se o resultado da reserva for incerto, repita a mesma requisição com a mesma `Idempotency-Key`; o pedido fica recuperável e a operação de reserva é idempotente por `orderId`. | `"Falha ao consultar o catálogo."` ou `"Catálogo indisponível."` |

Sem `Idempotency-Key`, repetir uma requisição após timeout pode criar outro pedido. Clientes devem gerar uma chave por intenção de compra e reutilizá-la em todas as tentativas daquela mesma operação. A chave considera os itens e o endereço; reutilizá-la com qualquer conteúdo diferente resulta em conflito.

### Consultar pedidos

**`GET /ws/orders`**

**Autenticação:** cliente autenticado. A consulta é sempre limitada ao cliente identificado pelo JWT, não por um `customerId` enviado pelo cliente.

| Parâmetro | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `page` | inteiro | Não | Página baseada em zero; padrão `0`. |
| `size` | inteiro | Não | Itens por página; padrão `5`, máximo `100`. |
| `sort` | string | Não | Ordenação Spring Data; padrão por `orderDate` e `id` em ordem decrescente. |
| `targetCurrency` | string | Não | Código de moeda ISO de três letras para os valores apresentados; padrão `BRL`. |

Os dados comerciais e o endereço vêm dos snapshots persistidos. Quando `targetCurrency` não é BRL, a listagem faz uma chamada de câmbio por página; a conversão é apenas para apresentação e não altera os valores históricos.

**`GET /ws/orders/{orderId}`**

Retorna os detalhes históricos somente se o pedido pertencer ao cliente autenticado. Um pedido inexistente ou pertencente a outro cliente retorna `404 Not Found`.

### Consultas e gerenciamento administrativos

As operações administrativas exigem JWT de usuário com perfil Admin. O Gateway encaminha essas rotas ao Order API e o serviço também valida o perfil recebido do Gateway.

| Método e rota | Descrição |
|---|---|
| `GET /ws/admin/orders` | Lista pedidos paginados. |
| `GET /ws/admin/orders/{orderId}` | Consulta os detalhes de um pedido. |
| `POST /ws/admin/orders/{orderId}/cancel` | Cancela pedido pendente após liberar a reserva. |

A listagem aceita `status`, `customerId`, `orderId`, `createdFrom`, `createdTo`, `confirmedFrom` e `confirmedTo`, além dos parâmetros de paginação e ordenação (`page`, `size`, `sort`). O tamanho padrão é 20 e o máximo é 100. As datas devem ser timestamps ISO-8601.

O detalhe administrativo inclui cliente, itens/snapshots, endereço, valores, estado e dados da reserva. `paymentDataAvailable` é `false`: o Order API não guarda dados de pagamento (eles ficam no Payment API, ver [Pagamentos](#pagamentos)), e a API não inventa informações de pagamento. Um pedido `PENDING_PAYMENT` pode ser cancelado se a reserva for liberada. Um pedido confirmado não pode ser cancelado (`409 Conflict`) até existir uma política de estorno.

### Transições internas e pagamento

Um serviço confiável deve chamar diretamente o Order API pela rede interna com `X-Service-Token`. O endpoint de confirmação só deve ser chamado pelo fluxo responsável depois que o pagamento tiver sido aprovado: esse fluxo é o Payment API, que chama `confirm` ao receber do Stripe a notificação de pagamento aprovado e `cancel` quando o pagamento é recusado ou a sessão expira (ver [Pagamentos](#pagamentos)). O segredo não deve ser enviado ao aplicativo cliente; pelo Gateway, essas rotas respondem `401 Unauthorized`.

| Método e rota interna | Efeito |
|---|---|
| `POST /ws/orders/{orderId}/confirm` | Confirma a reserva no Inventory API e muda o pedido para `CONFIRMED`; repetição após confirmação não baixa estoque novamente. |
| `POST /ws/orders/{orderId}/cancel` | Libera a reserva e muda o pedido para `CANCELLED`; não cancela pedido confirmado. |

Se a reserva tiver expirado, o pedido passa a `EXPIRED` e não é confirmado. O Order API também reconcilia pedidos cujo prazo passou, consultando o estado atual da reserva no Inventory API. A expiração automática do Inventory API, por si só, não altera instantaneamente o estado local do pedido; a reconciliação ocorre periodicamente.

Estados de pedido utilizados: `RESERVATION_PENDING` durante a coordenação inicial, `PENDING_PAYMENT` após reserva bem-sucedida, `CONFIRMED`, `CANCELLED`, `EXPIRED`, `FAILED` e `LEGACY` para registros anteriores às migrações.

### Consulta interna para analytics

**`GET /ws/orders/internal/analytics?updatedAfter={timestamp}`**

Disponível somente na rede interna e protegido por `X-Service-Token`; não deve ser exposto como rota de cliente. A resposta é paginada (`page`, `size`, `sort`; padrão de 100 registros, máximo de 100) e inclui os dados históricos persistidos do pedido, inclusive `customerId`. `updatedAfter` é obrigatório e inclusivo, retornando pedidos atualizados naquele instante ou depois dele, permitindo capturar mudanças de status além da criação. O consumidor deve avançar a paginação e deduplicar registros na fronteira do instante de atualização.

### Consulta interna para pagamentos

**`GET /ws/orders/internal/{orderId}/payment-info`**

Contrato mínimo usado pelo Payment API para validar o pedido antes de iniciar um pagamento. Disponível somente na rede interna e protegido por `X-Service-Token`; pelo Gateway, um cliente recebe `401 Unauthorized`. Pedido inexistente retorna `404 Not Found`.

```json
{
  "id": 42,
  "customerId": 7,
  "status": "PENDING_PAYMENT",
  "totalAmount": 149.90,
  "currency": "BRL",
  "reservationExpiresAt": "2026-11-18T12:10:00Z"
}
```

A resposta contém apenas esses campos: dono do pedido, estado, valor total (com frete), moeda e validade da reserva de estoque.

## Carrinho

O `cart-api` mantém um carrinho persistente por cliente autenticado. As rotas abaixo são encaminhadas pelo Gateway e exigem JWT de cliente. O identificador do cliente vem do token validado pelo Gateway, não do corpo da requisição.

O carrinho consulta os dados comerciais em `product-api` e a disponibilidade em `inventory-api`. Valores e subtotais são estimativas em BRL consultadas no momento da leitura; preço, estoque e disponibilidade podem mudar antes do checkout. O carrinho não reserva estoque, não calcula frete e não cria pedidos.

Para incluir produtos desativados nas leituras do carrinho, o `cart-api` consulta o endpoint interno `GET /internal/products/{productId}/cart-data` do `product-api`, protegido por `X-Service-Token`; ele retorna os dados comerciais atuais em BRL e o status efetivo de ativação do produto, categoria e subcategoria. Esse endpoint não deve ser chamado pelo frontend.

### Consultar carrinho

**`GET /ws/cart`**

Retorna o carrinho persistido do cliente; quando ainda não existe, cria um carrinho vazio. A resposta inclui o produto, a quantidade desejada, estoque físico/reservado/disponível, preço unitário e subtotal estimado.

```json
{
  "id": 13,
  "version": 2,
  "createdAt": "2026-11-10T12:00:00Z",
  "updatedAt": "2026-11-10T12:10:00Z",
  "currency": "BRL",
  "estimatedSubtotal": 150.00,
  "estimateComplete": true,
  "items": [
    {
      "productId": 17,
      "name": "Cuia artesanal",
      "brand": "Marca Gaúcha",
      "model": "CU-01",
      "image": "https://example.com/cuia.jpg",
      "productType": "MULTI_UNIT",
      "active": true,
      "productAvailable": true,
      "desiredQuantity": 2,
      "quantityOnHand": 10,
      "quantityReserved": 1,
      "availableQuantity": 9,
      "unitPrice": 75.00,
      "estimatedSubtotal": 150.00,
      "available": true,
      "addedAt": "2026-11-10T12:00:00Z",
      "updatedAt": "2026-11-10T12:00:00Z"
    }
  ]
}
```

Se o produto for desativado, seus dados comerciais atuais continuam disponíveis para exibição, mas a resposta marca `active: false` e `available: false`; o item não é removido. Se o produto tiver sido removido do catálogo, o item continua no carrinho com `productAvailable: false`; seus dados e subtotais ficam nulos, e `estimateComplete` será `false` com `estimatedSubtotal` nulo. Se não houver registro de estoque, as quantidades são apresentadas como zero. Disponibilidade insuficiente não remove o item; a resposta marca `available: false`.

### Adicionar produto ou atualizar sua quantidade

**`POST /ws/cart/items`**

```json
{
  "productId": 17,
  "quantity": 2
}
```

Se o produto já estiver no carrinho, a quantidade é substituída pela informada, sem criar um segundo item e sem somar à quantidade anterior. Produtos `SINGLE_UNIT` aceitam somente quantidade `1`; produtos `MULTI_UNIT` aceitam quantidade inteira positiva. O serviço rejeita produto inativo/inexistente ou quantidade acima da disponibilidade observada. O sucesso retorna `200 OK` com o carrinho atualizado.

### Alterar quantidade de um produto

**`PATCH /ws/cart/items/{productId}`**

Atualiza a quantidade de um item já existente, após consultar novamente catálogo e disponibilidade.

```json
{
  "quantity": 3
}
```

O sucesso retorna `200 OK` com o carrinho atualizado. Item inexistente retorna `404 Not Found`.

### Remover itens

- **`DELETE /ws/cart/items/{productId}`** remove um produto; retorna `204 No Content` ou `404 Not Found` se ele não estiver no carrinho.
- **`DELETE /ws/cart/items`** remove todos os produtos e mantém o registro persistente do carrinho; retorna `204 No Content`.

Quantidade inválida ou tentativa de exceder o estoque retorna `400 Bad Request` ou `409 Conflict`, respectivamente. Falhas ao consultar os serviços dependentes retornam erro `502`/`503`, sem usar preços ou disponibilidade em cache como fallback.

Exemplos de mensagens: quantidade zero retorna `400` com `"A quantidade deve ser maior que zero."`; produto ausente no carrinho retorna `404` com `"Produto não encontrado no carrinho."`; disponibilidade insuficiente retorna `409` com `"A quantidade desejada excede a disponibilidade atual do estoque."`. Falhas de consulta retornam mensagens seguras como `"Não foi possível consultar o product-api."` ou `"Não foi possível consultar o inventory-api."`.

### Checkout do carrinho

O frontend envia os itens escolhidos e o endereço diretamente para **`POST /ws/orders`**; o contrato existente de pedido recebe `items` e `shippingAddress`. O `order-api` consulta novamente o catálogo, define os valores finais, calcula o frete e coordena a reserva atômica de todos os itens no `inventory-api`. O carrinho não participa da criação do pedido.

Somente depois de receber `201 Created` do `order-api`, o frontend deve chamar `DELETE /ws/cart/items` para limpar o carrinho. Se a criação do pedido falhar, o frontend mantém o carrinho.

## Analytics

O `analytics-api` fornece os indicadores do painel administrativo. Ele não participa do checkout: em intervalos configuráveis, coleta pedidos do `order-api` e dados de estoque do `inventory-api`, grava uma cópia própria no `analytics_db` e responde às consultas a partir dela. Por isso os números refletem a última coleta concluída, e não o instante exato da consulta.

Todas as rotas abaixo exigem JWT de administrador. O Gateway encaminha as requisições ao Analytics API com uma credencial privada; o serviço também valida o perfil recebido do Gateway e rejeita chamadas diretas.

| Situação | Status |
|---|---|
| Sem `Authorization` ou com token inválido/revogado | `401 Unauthorized` |
| Usuário autenticado sem perfil de administrador | `403 Forbidden` |
| Administrador | `200 OK` |

### Regras de negócio

- **Venda:** somente pedidos com status `CONFIRMED` e moeda `BRL` são contados. Pedidos pendentes, cancelados, expirados ou com falha são coletados, mas não entram nos indicadores. Se um pedido mudar de status, a coleta seguinte atualiza os números.
- **Faturamento:** soma do `totalAmount` dos pedidos, que inclui o frete. Os valores por produto, categoria, marca e faixa de preço usam o subtotal de cada item, sem frete.
- **Data da venda:** `confirmedAt` do pedido convertido para o fuso `America/Sao_Paulo`. Os filtros `from` e `to` usam essa data.
- **Ticket médio:** faturamento dividido pela quantidade de pedidos confirmados.
- **Região:** derivada da UF do endereço de entrega, conforme as regiões do IBGE: `NORTE`, `NORDESTE`, `CENTRO_OESTE`, `SUDESTE` e `SUL`.
- **Faixas de preço:** definidas pelo preço unitário do item no momento da compra.

| Faixa | Preço unitário |
|---|---|
| `UP_TO_50` | Até R$ 50,00 |
| `FROM_50_TO_150` | Acima de R$ 50,00 até R$ 150,00 |
| `FROM_150_TO_300` | Acima de R$ 150,00 até R$ 300,00 |
| `FROM_300_TO_600` | Acima de R$ 300,00 até R$ 600,00 |
| `ABOVE_600` | Acima de R$ 600,00 |

### Coleta periódica

A coleta roda automaticamente a cada `analytics.collection.interval-ms` milissegundos; o padrão é `300000` (5 minutos), configurado no Consul em `config/analytics-api/analytics.collection.interval-ms`. Cada execução processa três fontes, de forma independente:

| Fonte | Origem | O que é coletado |
|---|---|---|
| `ORDERS` | `GET /ws/orders/internal/analytics` | Pedidos criados ou atualizados desde a última coleta, com itens e endereço. |
| `INVENTORY_SNAPSHOT` | `GET /ws/inventory/analytics/summary`, `/out-of-stock` e `/low-stock` | Totais de estoque e produtos sem estoque ou com estoque baixo. |
| `INVENTORY_MOVEMENTS` | `GET /ws/inventory/analytics/movements` | Movimentações de estoque desde a última coleta. |

A gravação é idempotente: repetir uma coleta não duplica pedidos, itens nem movimentações. As chamadas às origens têm retentativas; se uma origem continuar indisponível, a fonte é registrada como `FAILED` e coletada novamente no ciclo seguinte, sem afetar as demais. Após cada coleta de pedidos, as agregações diárias de vendas dos dias afetados são recalculadas.

### Parâmetros comuns de vendas

Os endpoints de vendas (`overview`, `sales`, `sales/by-region`, `products/top`, `categories` e `price-ranges`) aceitam os filtros abaixo, combináveis entre si:

| Parâmetro | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `from` | data `YYYY-MM-DD` | Não | Início do período (inclusivo); padrão: 29 dias antes de `to`. |
| `to` | data `YYYY-MM-DD` | Não | Fim do período (inclusivo); padrão: data atual no fuso de São Paulo. |
| `state` | string | Não | UF do endereço de entrega, por exemplo `RS`. |
| `region` | string | Não | `NORTE`, `NORDESTE`, `CENTRO_OESTE`, `SUDESTE` ou `SUL`. |
| `categoryId` | inteiro | Não | Considera somente itens da categoria. |
| `productId` | inteiro | Não | Considera somente itens do produto. |

O período padrão cobre os últimos 30 dias, e o máximo é de 5 anos. Todas as respostas de vendas repetem o período e os filtros aplicados, e informam `currency: "BRL"`:

```json
{
  "period": { "from": "2026-10-19", "to": "2026-11-17" },
  "filters": { "state": null, "region": "NORDESTE", "categoryId": null, "productId": null },
  "currency": "BRL"
}
```

Os exemplos abaixo omitem esses campos.

### Resumo de vendas

**`GET /ws/admin/analytics/overview`**

Retorna os totais do período, os cinco produtos com mais unidades vendidas e as cinco categorias com maior faturamento.

```json
{
  "totalRevenue": 219.50,
  "confirmedOrders": 1,
  "averageTicket": 219.50,
  "unitsSold": 5,
  "topProducts": [
    {
      "productId": 37,
      "productName": "Tapa-erva ajustável",
      "brand": "Campeira",
      "categoryId": 7,
      "categoryName": "Acessórios para chimarrão",
      "units": 3,
      "revenue": 44.70,
      "orders": 1
    }
  ],
  "topCategories": [
    {
      "categoryId": 3,
      "categoryName": "Bombas",
      "revenue": 139.80,
      "orders": 1,
      "units": 2,
      "revenueShare": 75.77
    }
  ]
}
```

`revenueShare` é a participação percentual no faturamento dos itens do período.

### Evolução das vendas

**`GET /ws/admin/analytics/sales`**

Além dos parâmetros comuns:

| Parâmetro | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `granularity` | string | Não | `DAY`, `WEEK` ou `MONTH`; padrão `DAY`. |

Retorna um ponto por dia, semana (iniciada na segunda-feira) ou mês, inclusive os períodos sem vendas, com valores zerados.

```json
{
  "granularity": "DAY",
  "totals": { "revenue": 219.50, "orders": 1, "units": 5, "averageTicket": 219.50 },
  "points": [
    { "periodStart": "2026-11-16", "revenue": 0.00, "orders": 0, "units": 0, "averageTicket": 0.00 },
    { "periodStart": "2026-11-17", "revenue": 219.50, "orders": 1, "units": 5, "averageTicket": 219.50 }
  ]
}
```

### Vendas por região e estado

**`GET /ws/admin/analytics/sales/by-region`**

Retorna as cinco regiões, inclusive as sem vendas, e os estados com vendas no período.

```json
{
  "totals": { "revenue": 219.50, "orders": 1, "units": 5, "averageTicket": 219.50 },
  "regions": [
    {
      "region": "NORDESTE",
      "regionName": "Nordeste",
      "revenue": 219.50,
      "orders": 1,
      "units": 5,
      "averageTicket": 219.50,
      "revenueShare": 100.00
    }
  ],
  "states": [
    {
      "state": "BA",
      "region": "NORDESTE",
      "revenue": 219.50,
      "orders": 1,
      "units": 5,
      "averageTicket": 219.50,
      "revenueShare": 100.00
    }
  ]
}
```

### Produtos mais vendidos

**`GET /ws/admin/analytics/products/top`**

Além dos parâmetros comuns:

| Parâmetro | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `rankBy` | string | Não | `UNITS` (unidades vendidas) ou `REVENUE` (faturamento); padrão `UNITS`. |
| `limit` | inteiro | Não | Quantidade de produtos; padrão `10`, máximo `100`. |

```json
{
  "rankBy": "REVENUE",
  "products": [
    {
      "productId": 26,
      "productName": "Bomba de chimarrão acabamento prata",
      "brand": "Prata Sul",
      "categoryId": 3,
      "categoryName": "Bombas",
      "units": 2,
      "revenue": 139.80,
      "orders": 1
    }
  ]
}
```

### Desempenho por categoria e marca

**`GET /ws/admin/analytics/categories`**

Além dos parâmetros comuns, aceita `limit` (padrão `20`, máximo `100`), aplicado separadamente às categorias e às marcas. Ambas as listas são ordenadas por faturamento.

```json
{
  "categories": [
    { "categoryId": 3, "categoryName": "Bombas", "revenue": 139.80, "orders": 1, "units": 2, "revenueShare": 75.77 },
    { "categoryId": 7, "categoryName": "Acessórios para chimarrão", "revenue": 44.70, "orders": 1, "units": 3, "revenueShare": 24.23 }
  ],
  "brands": [
    { "brand": "Prata Sul", "revenue": 139.80, "orders": 1, "units": 2, "revenueShare": 75.77 },
    { "brand": "Campeira", "revenue": 44.70, "orders": 1, "units": 3, "revenueShare": 24.23 }
  ]
}
```

### Vendas por faixa de preço

**`GET /ws/admin/analytics/price-ranges`**

Retorna todas as faixas, inclusive as sem vendas.

```json
{
  "ranges": [
    { "range": "UP_TO_50", "label": "Até R$ 50", "minExclusive": null, "maxInclusive": 50.00, "units": 3, "revenue": 44.70, "orders": 1 },
    { "range": "FROM_50_TO_150", "label": "R$ 50 – R$ 150", "minExclusive": 50.00, "maxInclusive": 150.00, "units": 2, "revenue": 139.80, "orders": 1 }
  ]
}
```

### Indicadores de estoque

**`GET /ws/admin/analytics/inventory`**

| Parâmetro | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `from` | data `YYYY-MM-DD` | Não | Início do período do histórico; padrão: últimos 30 dias. |
| `to` | data `YYYY-MM-DD` | Não | Fim do período do histórico; padrão: data atual. |
| `granularity` | string | Não | `DAY`, `WEEK` ou `MONTH` para o histórico; padrão `DAY`. |
| `limit` | inteiro | Não | Máximo de itens nas listas sem estoque e com estoque baixo; padrão `50`, máximo `100`. |

`summary`, `outOfStock` e `lowStock` refletem o snapshot mais recente. `availabilityHistory` traz um ponto por período, com o último snapshot de cada um, e `movementTotals` soma as movimentações do período por tipo; quantidades negativas representam saídas.

```json
{
  "period": { "from": "2026-10-19", "to": "2026-11-17" },
  "granularity": "DAY",
  "summary": {
    "capturedAt": "2026-11-17T16:52:49.158722Z",
    "inventoryCount": 3,
    "quantityOnHand": 145,
    "quantityReserved": 0,
    "availableQuantity": 145,
    "outOfStockCount": 0,
    "lowStockCount": 0
  },
  "outOfStock": [],
  "lowStock": [],
  "availabilityHistory": [
    {
      "periodStart": "2026-11-17",
      "capturedAt": "2026-11-17T16:52:49.158722Z",
      "quantityOnHand": 145,
      "quantityReserved": 0,
      "availableQuantity": 145,
      "outOfStockCount": 0,
      "lowStockCount": 0
    }
  ],
  "movementTotals": [
    { "movementType": "ENTRY", "movements": 3, "quantity": 150 },
    { "movementType": "RESERVATION_CONFIRMATION", "movements": 2, "quantity": -5 }
  ]
}
```

As definições de "sem estoque" e "estoque baixo" são as do `inventory-api`: disponibilidade zero e quantidade física no limite configurado ou abaixo dele, respectivamente.

### Movimentações de estoque

**`GET /ws/admin/analytics/inventory/movements`**

| Parâmetro | Tipo | Obrigatório | Descrição |
|---|---|---:|---|
| `from` | data `YYYY-MM-DD` | Não | Início do período; padrão: últimos 30 dias. |
| `to` | data `YYYY-MM-DD` | Não | Fim do período; padrão: data atual. |
| `productId` | inteiro | Não | Filtra por produto. |
| `movementType` | string | Não | Filtra por tipo, por exemplo `ENTRY` ou `RESERVATION_CONFIRMATION`. |
| `page` | inteiro | Não | Página baseada em zero; padrão `0`. |
| `size` | inteiro | Não | Itens por página; padrão `20`, máximo `100`. |

As movimentações são ordenadas da mais recente para a mais antiga.

```json
{
  "period": { "from": "2026-10-19", "to": "2026-11-17" },
  "content": [
    {
      "id": 5,
      "productId": 37,
      "movementType": "RESERVATION_CONFIRMATION",
      "quantityDelta": -3,
      "reason": "Confirmação da reserva 8",
      "orderId": 8,
      "reservationId": 8,
      "createdAt": "2026-11-17T16:23:45.573078Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1
}
```

### Controle das coletas

**`GET /ws/admin/analytics/collections`**

Retorna a situação de cada fonte e as 20 execuções mais recentes, da mais nova para a mais antiga. `cursorAt` é o ponto até onde a fonte já foi coletada; `lastStatus` pode ser `SUCCESS`, `FAILED`, `RUNNING` ou `NEVER_RUN`.

```json
{
  "sources": [
    {
      "source": "ORDERS",
      "cursorAt": "2026-11-17T16:33:15.884417Z",
      "lastAttemptAt": "2026-11-17T16:52:48.837417Z",
      "lastSuccessAt": "2026-11-17T16:52:49.090547Z",
      "lastStatus": "SUCCESS",
      "lastError": null
    }
  ],
  "recentRuns": [
    {
      "id": 135,
      "source": "INVENTORY_MOVEMENTS",
      "startedAt": "2026-11-17T16:52:49.193563Z",
      "finishedAt": "2026-11-17T16:52:49.215413Z",
      "status": "SUCCESS",
      "recordsProcessed": 0,
      "errorMessage": null
    }
  ]
}
```

**`POST /ws/admin/analytics/collections/run`**

Executa a coleta imediatamente, sem aguardar o próximo ciclo, por exemplo depois de uma indisponibilidade das origens. Não tem corpo. Retorna `200 OK` com o mesmo formato de `GET /collections` ao final da execução. Se já houver uma coleta em andamento, nada é executado e a resposta é `202 Accepted` com a situação atual.

### Erros comuns

| Status | Quando ocorre | Exemplo de resposta |
|---|---|---|
| `400 Bad Request` | Data inválida, `from` posterior a `to`, período maior que 5 anos, UF ou região inválida, `granularity` ou `rankBy` desconhecido, `limit`/`size` menor que 1 ou `page` negativa. | `"O parâmetro 'from' deve ser anterior ou igual a 'to'."`, `"O período máximo de consulta é de 5 anos."` ou `"Região inválida: ..."` |
| `401 Unauthorized` | Requisição sem JWT válido ou que não passou pelo Gateway. | `"Token ausente, inválido ou revogado."` |
| `403 Forbidden` | Usuário autenticado sem perfil de administrador. | `"Usuário sem permissão para esta operação."` |
| `500 Internal Server Error` | Falha inesperada ao consultar Analytics. Checkout e pedidos não são afetados. | `"Ocorreu um erro interno. Tente novamente mais tarde."` |

## Pagamentos

O `payment-api` integra a loja ao **Stripe Checkout em modo de teste**. Ele inicia o pagamento de um pedido, acompanha o estado do pagamento e comunica o resultado ao `order-api`. O Order API não conhece o provedor: a integração fica isolada no Payment API, atrás de uma interface que permite trocar o provedor.

Regras gerais:

- O valor e a moeda vêm **sempre** do Order API (`totalAmount`, com frete). O frontend não envia preço nem total.
- O cliente paga na página hospedada do Stripe (`checkoutUrl`); a loja nunca recebe dados de cartão.
- O pagamento só é aprovado pelo **webhook** assinado do Stripe. O retorno do navegador para a página de sucesso **não** aprova nada: o frontend apenas consulta o status.
- A chave secreta do Stripe fica somente no servidor, em variável de ambiente, e precisa ser de teste (`sk_test_` ou `rk_test_`).

### Fluxo recomendado

1. Crie o pedido em `POST /ws/orders`. Ele fica `PENDING_PAYMENT` com reserva de estoque de 10 minutos.
2. Chame `POST /ws/payments/orders/{orderId}/checkout-session` e redirecione o cliente para o `checkoutUrl` recebido.
3. Ao terminar, o Stripe redireciona o navegador para `GET /payments/return/success` (ou `/cancel`), que redireciona para o deep link do app com `order_id` (e `session_id`). Veja [Retorno do checkout](#retorno-do-checkout).
4. Em paralelo, o Stripe notifica o webhook. O Payment API confirma o pedido no Order API e marca o pagamento como `APPROVED`.
5. Na tela de sucesso, consulte `GET /ws/payments/orders/{orderId}` (com o `order_id` recebido no deep link) até o status deixar de ser `PENDING`.

### Iniciar pagamento

**`POST /ws/payments/orders/{orderId}/checkout-session`**

Exige JWT de cliente. Não tem corpo.

```http
POST /ws/payments/orders/42/checkout-session
Authorization: Bearer <token>
```

**Resposta `200 OK`:**

```json
{
  "paymentId": 15,
  "orderId": 42,
  "status": "PENDING",
  "checkoutUrl": "https://checkout.stripe.com/c/pay/cs_test_..."
}
```

Comportamento:

- Valida no Order API que o pedido existe, pertence ao cliente, está `PENDING_PAYMENT` e tem pelo menos `payment.min-reservation-seconds` (padrão 120 segundos) de reserva restante.
- Cria uma sessão do Stripe Checkout (somente cartão, um item "Pedido #{orderId}" com o total do pedido) válida por cerca de 30 minutos, o mínimo aceito pelo Stripe.
- Se já existe um pagamento aberto para o pedido, devolve o mesmo `checkoutUrl`, sem criar outra sessão. Se esse pagamento aberto não serve mais (pedido não mais pendente, ou reserva com menos de `payment.min-reservation-seconds` restantes), a sessão é expirada no Stripe, o pagamento passa a `CANCELLED` e as regras acima são reavaliadas; com a reserva no fim, a resposta é `409 Conflict`.
- Repetir a chamada é seguro: há no máximo um pagamento aberto por pedido, inclusive com requisições simultâneas.

| Status | Quando ocorre | Exemplo de resposta |
|---|---|---|
| `200 OK` | Sessão criada, ou sessão aberta existente reaproveitada. | — |
| `401 Unauthorized` | Sem JWT válido. | `"Token ausente, inválido ou revogado."` |
| `403 Forbidden` | Usuário autenticado que não é cliente. | `"Somente clientes podem pagar pedidos."` |
| `404 Not Found` | Pedido inexistente ou de outro cliente. | `"Pedido não encontrado."` |
| `409 Conflict` | Pedido não está `PENDING_PAYMENT`, a reserva expira em menos de 2 minutos, outra requisição está criando o pagamento neste instante, ou o checkout anterior já foi concluído e está em processamento. | Mensagem específica sobre o estado do pedido ou da reserva. |
| `502 Bad Gateway` | Stripe ou Order API indisponível. Se a falha foi do Stripe, a tentativa é registrada como `FAILED` e uma nova chamada cria outra. | `"Serviço de pedidos indisponível."`, `"Não foi possível iniciar o pagamento."` ou `"Não foi possível encerrar o pagamento anterior."` |

### Consultar pagamento

**`GET /ws/payments/orders/{orderId}`**

Exige JWT de cliente. Retorna o pagamento mais recente do pedido do cliente. O frontend usa esta rota para acompanhar o resultado; ela nunca altera o status.

```json
{
  "paymentId": 15,
  "orderId": 42,
  "status": "APPROVED",
  "amount": 149.90,
  "currency": "BRL",
  "checkoutUrl": null,
  "createdAt": "2026-11-18T12:00:05Z",
  "updatedAt": "2026-11-18T12:01:40Z",
  "confirmedAt": "2026-11-18T12:01:40Z"
}
```

`checkoutUrl` só vem preenchido enquanto o pagamento está `PENDING`. Sem pagamento para o pedido, ou pedido de outro cliente: `404 Not Found`.

### Retorno do checkout

**`GET /payments/return/success?session_id=...&order_id=...`** e **`GET /payments/return/cancel?order_id=...`**

Rotas públicas (sem JWT) usadas como `success_url` e `cancel_url` do Stripe. Respondem `302 Found` para o deep link do app configurado em `PAYMENT_APP_SUCCESS_URL` / `PAYMENT_APP_CANCEL_URL` (padrão `oma://payment/success` e `oma://payment/cancel`) e **não alteram nenhum status**.

```http
GET /payments/return/success?session_id=cs_test_a1B2&order_id=42

HTTP/1.1 302 Found
Location: oma://payment/success?order_id=42&session_id=cs_test_a1B2
Cache-Control: no-store
```

- O `payment-api` acrescenta `order_id` às URLs de retorno de cada sessão; o app não precisa guardar o pedido antes de abrir o Stripe.
- O destino vem só da configuração. Da requisição seguem apenas `order_id` (número positivo) e `session_id` (`cs_...`); valores com outro formato são descartados.
- Use o `order_id` para consultar o pagamento. O `session_id` é apenas informativo.

### Estados e transições

Os estados do pagamento são próprios e separados dos estados do pedido. As transições são controladas pelo backend a partir dos eventos validados do Stripe; estados finais não mudam, e eventos atrasados ou duplicados são ignorados. A única exceção é a aprovação que chega para um pagamento já `FAILED`, `EXPIRED` ou `CANCELLED`: o valor foi capturado e não será honrado, então é estornado e o pagamento passa a `REFUNDED`.

| Estado | Significado |
|---|---|
| `CREATED` | Tentativa registrada; a sessão no Stripe está sendo criada. |
| `PENDING` | Sessão criada; aguardando o cliente ou a confirmação do Stripe. |
| `APPROVED` | Pago e pedido confirmado. Final. |
| `FAILED` | Falha ao criar a sessão ou pagamento recusado. Final (salvo estorno). |
| `EXPIRED` | Sessão expirou sem pagamento. Final (salvo estorno). |
| `CANCELLED` | Substituído por um novo checkout porque o anterior não servia mais. Final (salvo estorno). |
| `REFUNDED` | Pago depois que o pedido deixou de aceitar pagamento; estornado. Final. |

| De | Para |
|---|---|
| `CREATED` | `PENDING`, `FAILED`, `CANCELLED` |
| `PENDING` | `APPROVED`, `FAILED`, `EXPIRED`, `CANCELLED`, `REFUNDED` |
| `FAILED`, `EXPIRED`, `CANCELLED` | `REFUNDED` (somente ao receber aprovação depois do encerramento) |

### Webhook do Stripe

**`POST /payments/webhook`**

Rota pública (sem JWT), chamada somente pelo Stripe e autenticada pela assinatura do cabeçalho `Stripe-Signature`. O Gateway encaminha o corpo e o cabeçalho sem alterações. Fora de `/ws/`, o Gateway expõe para o Payment API apenas esta rota e as de [retorno do checkout](#retorno-do-checkout).

| Evento | Efeito |
|---|---|
| `checkout.session.completed` | Se `payment_status` é `paid`, segue o fluxo de aprovação. Caso contrário, mantém `PENDING` aguardando o resultado assíncrono. |
| `checkout.session.async_payment_succeeded` | Fluxo de aprovação. |
| `checkout.session.async_payment_failed` | `FAILED` e cancelamento do pedido para liberar o estoque. |
| `checkout.session.expired` | `EXPIRED` e cancelamento do pedido para liberar o estoque. |
| Outros | Registrados e ignorados. |

**Fluxo de aprovação:** o Payment API chama `POST /ws/orders/{orderId}/confirm` e confere o **status no corpo** da resposta. Só com `CONFIRMED` o pagamento passa a `APPROVED`. Se o pedido não puder mais ser confirmado, aplica-se o tratamento de pagamento tardio (abaixo).

Garantias:

- O identificador de cada evento é gravado em `processed_stripe_event` na mesma transação do processamento. Um evento repetido responde `200 OK` sem ser reprocessado.
- O pagamento é localizado pelo id da sessão ou, se necessário, pelo `payment_id` enviado na metadata. Evento de sessão desconhecida responde `200 OK` e é apenas registrado em log.
- Se o Order API ou o Stripe estiverem indisponíveis (ou o `confirm` responder `404`), o webhook responde `500` e a transação inteira é desfeita, inclusive o registro do evento. O Stripe reenvia o evento, e as chamadas ao Order API são idempotentes.
- Um pedido já confirmado não é confirmado de novo, e um `409` no cancelamento de pedido confirmado é apenas registrado em log.

| Status | Quando ocorre | Exemplo de resposta |
|---|---|---|
| `200 OK` | Evento processado, duplicado, ignorado ou de sessão desconhecida. | Sem corpo. |
| `400 Bad Request` | Assinatura ausente ou inválida, ou corpo que não é um evento do Stripe. | `"Assinatura ou evento do webhook inválido."` |
| `500 Internal Server Error` | Falha de infraestrutura; nada é gravado e o Stripe reenviará. | `"Não foi possível processar o webhook. O evento será reenviado."` |

### Pagamento aprovado tardiamente

A reserva de estoque dura 10 minutos, mas a sessão do Stripe dura no mínimo 30. O cliente pode, portanto, pagar depois que a reserva expirou. Nesse caso o pedido não pode ser confirmado sem estoque:

1. O `confirm` do Order API responde `200` com status `EXPIRED` ou `CANCELLED`, ou `409 Conflict`.
2. O Payment API estorna o valor inteiro no Stripe (pela transação do pagamento, com chave de idempotência por pagamento, sem estorno duplicado em reenvios).
3. O pagamento passa a `REFUNDED` e o pedido continua sem confirmação. O cliente pode fazer um novo pedido.

Para reduzir esse caso, nenhuma sessão é criada nem reaproveitada quando a reserva tem menos de 2 minutos restantes, e uma sessão aberta de pedido vencido é expirada no Stripe na próxima tentativa de pagamento.

O mesmo estorno acontece se a aprovação chegar para um pagamento já `FAILED`, `EXPIRED` ou `CANCELLED`: o caso é registrado em log de erro, o pedido não é confirmado e o pagamento passa a `REFUNDED`. O estorno é idempotente: se o Stripe informar que a cobrança já foi totalmente estornada (`charge_already_refunded`), o resultado é tratado como sucesso.

**Limitação conhecida:** não há job de reconciliação. Se o estorno ou a confirmação falharem de forma permanente (por exemplo, o Stripe recusa o estorno ou o Order API responde sempre erro), o webhook responde `500` a cada tentativa e o Stripe reenvia o evento por cerca de 3 dias. Durante esse tempo o pagamento continua `PENDING`; se o problema não se resolver, o caso exige intervenção manual (conferir o pagamento no painel do Stripe, estornar ou confirmar o pedido e reenviar o evento).