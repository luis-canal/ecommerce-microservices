# Documentação da API — Autenticação e catálogo

Guia para integração do front-end com os fluxos de autenticação e gestão de produtos deste repositório. As rotas abaixo são relativas à URL-base do **API Gateway**; a porta e o host dependem da configuração do ambiente.

## Convenções

- Envie e receba JSON com `Content-Type: application/json` e `Accept: application/json`, salvo respostas sem corpo.
- IDs são inteiros de 64 bits (`Long` no servidor); preços são números decimais.
- Os exemplos de erro refletem o comportamento atual dos serviços. As APIs **não** têm um envelope de erro uniforme: vários endpoints retornam texto simples, e o Gateway pode responder sem corpo.
- As rotas `/ws/**` são protegidas pelo Gateway. Envie `Authorization: Bearer <token>`; **não** envie manualmente os cabeçalhos internos `X-User-Id`, `X-User-Type` ou `X-User-Email`. O Gateway os remove da requisição recebida do cliente e propaga os valores derivados do JWT.
- Tokens JWT expiram em 24 horas. O logout revoga o token até a expiração.
- O tipo de usuário de cadastro público é sempre `Common`. A conta administradora é provisionada no banco; não há rota separada de login administrativo.

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
| `400 Bad Request` | Campos inválidos ou e-mail já cadastrado. | ` "E-mail informado inválido" ` (texto simples) |
| `5xx` | Falha não tratada no serviço ou persistência. | O corpo depende da camada que gerou o erro. |

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
| `401 Unauthorized` | E-mail ou senha não autenticam. | ` "Bad credentials" ` (texto simples; a mensagem pode variar) |
| `400 Bad Request` | Corpo inválido ou erro genérico tratado pelo controller. | Texto simples com a mensagem retornada pelo serviço. |
| `5xx` | Falha não tratada fora dos handlers do controller. | O corpo depende da camada que gerou o erro. |

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

### Logout e invalidação do token

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
| `401 Unauthorized` | Header ausente/malformado, JWT inválido ou expirado. | Sem corpo. |
| `5xx` | Falha ao persistir a revogação. | O corpo depende da camada que gerou o erro. |

## Produtos

### Listar, pesquisar, filtrar e ordenar produtos

**`GET /products`**

Lista produtos ativos em páginas. Também exclui produtos cuja subcategoria ou categoria esteja inativa.

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
| `400 Bad Request` | Parâmetro inválido, faixa de preço invertida/negativa ou campo de ordenação não suportado. | `"O preço mínimo não pode ser superior ao preço máximo"` (texto simples) |
| `503 Service Unavailable` | Não foi possível obter conversão para BRL necessária aos filtros ou à ordenação por preço. | `"Não foi possível converter os preços para BRL"` (texto simples) |
| `500 Internal Server Error` | Outra falha inesperada no serviço. | `"Ops!!! Algo deu errado. - <mensagem>"` (texto simples) |

### Buscar produto por ID

**`GET /products/{id}`**

Retorna um produto ativo, desde que sua subcategoria e categoria também estejam ativas.

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

Mesmo formato do item em [listar produtos](#listar-pesquisar-filtrar-e-ordenar-produtos). Se a conversão do detalhe falhar, o serviço atualmente retorna o produto com `convertedPrice: -1` e registra a falha no campo `environment`.

```json
{
  "id": 17,
  "name": "Cuia artesanal",
  "brand": "Marca Gaúcha",
  "model": "CU-01",
  "description": "Cuia de porongo",
  "currency": "BRL",
  "price": 75.0,
  "image": "https://example.com/cuia.jpg",
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
| `400 Bad Request` | ID não numérico. | `"Parâmetro inválido: id"` (texto simples) |
| `404 Not Found` | Produto não existe, está inativo ou pertence a categoria/subcategoria inativa. | `"Produto não encontrado"` (texto simples) |
| `500 Internal Server Error` | Falha inesperada. A ausência de `targetCurrency` também pode cair no handler genérico atual e ser reportada como `500`. | `"Ops!!! Algo deu errado. - <mensagem>"` (texto simples) |

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

Retorna a entidade de produto criada, incluindo a associação `subcategory`.

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
  }
}
```

**Erros comuns**

| Status | Quando ocorre | Exemplo de corpo atual |
|---|---|---|
| `400 Bad Request` | Campo obrigatório inválido, preço não positivo ou subcategoria inexistente/inativa. | `"Nome inválido"` (texto simples; mensagem varia por campo) |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. O Gateway pode responder sem corpo. | Sem corpo no Gateway. |
| `403 Forbidden` | Token válido de usuário que não é administrador. | `"Usuário sem permissão para esta operação"` (texto simples) |
| `500 Internal Server Error` | Falha inesperada no serviço. | `"Ops!!! Algo deu errado. - <mensagem>"` (texto simples) |

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
| `image` | string ou `null` | Não | Imagem; nulo remove o valor atual. |
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
| `400 Bad Request` | Campo obrigatório inválido ou subcategoria inválida/inativa. | `"Preço inválido"` (texto simples; mensagem varia por campo) |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | Sem corpo no Gateway. |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa. | `"Usuário sem permissão para esta operação"` |
| `404 Not Found` | Produto não encontrado. | `"Produto não encontrado"` (texto simples) |
| `500 Internal Server Error` | Falha inesperada no serviço. | `"Ops!!! Algo deu errado. - <mensagem>"` (texto simples) |

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
| `400 Bad Request` | `active` ausente ou nulo. | `"Status inválido"` (texto simples) |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | Sem corpo no Gateway. |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa. | `"Usuário sem permissão para esta operação"` |
| `404 Not Found` | Produto não encontrado. | `"Produto não encontrado"` |
| `409 Conflict` | Tentativa de reativar com categoria ou subcategoria inativa. | `"Não é possível reativar um produto com categoria ou subcategoria inativa"` |
| `500 Internal Server Error` | Falha inesperada no serviço. | `"Ops!!! Algo deu errado. - <mensagem>"` (texto simples) |

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
| `500 Internal Server Error` | Falha inesperada ao consultar categorias. | O corpo depende do tratamento padrão do serviço. |

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
| `404 Not Found` | Categoria inexistente ou inativa. | Sem corpo. |
| `500 Internal Server Error` | Falha inesperada ao consultar. | O corpo depende do tratamento padrão do serviço. |

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
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | Sem corpo no Gateway. |
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
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | Sem corpo no Gateway. |
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
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | Sem corpo no Gateway. |
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
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | Sem corpo no Gateway. |
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
| `400 Bad Request` | Requisição, nome ou categoria inválidos; categoria inexistente/inativa. | `"Requisição inválida"` (texto simples; mensagem varia) |
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | Sem corpo no Gateway. |
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
| `401 Unauthorized` | Token Bearer ausente, inválido ou revogado. | Sem corpo no Gateway. |
| `403 Forbidden` | Usuário autenticado sem permissão administrativa. | `"Usuário sem permissão para esta operação"` |
| `404 Not Found` | Subcategoria não encontrada. | `"Subcategoria não encontrada"` |
| `409 Conflict` | Tentativa de ativar sob categoria inativa. | `"Não é possível ativar uma subcategoria de uma categoria inativa"` |
