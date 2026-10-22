# Convenções de API

Este documento define o contrato compartilhado entre os serviços. Novos endpoints e mudanças de contrato devem seguir estas regras.

## Tipos e formatos

- **Dinheiro:** representar com `BigDecimal`, sempre em BRL, com escala de duas casas. Não usar `float` ou `double` para valores monetários.
- **Data e hora:** representar instantes com `Instant`, em UTC, serializados como ISO-8601 (por exemplo, `2026-10-22T14:30:00Z`).
- **Paginação:** respostas paginadas usam o formato `{ "content": [], "page": 0, "size": 20, "totalElements": 0 }`. `page` é baseado em zero.

## Erros

Respostas de erro seguem RFC 7807 usando `ProblemDetail` do Spring. Os serviços devem manter status HTTP e corpo consistentes; não expor stack traces, segredos ou detalhes internos da infraestrutura.

## Rotas e acesso

| Prefixo | Uso | Acesso |
| --- | --- | --- |
| `/recurso` | Endpoints públicos explicitamente permitidos | Público conforme política do gateway |
| `/ws/recurso` | Operações de usuário autenticado | JWT válido |
| `/ws/admin/recurso` | Operações administrativas | Perfil `ADMINISTRADOR` |
| `/internal/**` | Comunicação interna entre serviços | Nunca expor pelo gateway; validar a chave interna |

Não criar aliases de rotas que reduzam o nível de proteção. O gateway é responsável por aplicar a política de acesso externa.

## Cabeçalhos de contexto

Os cabeçalhos abaixo são propagados pelo gateway para serviços internos. Serviços não devem confiar em valores fornecidos diretamente por clientes externos.

| Cabeçalho | Significado |
| --- | --- |
| `X-User-Id` | Identificador do usuário autenticado |
| `X-User-Type` | Perfil do usuário autenticado |
| `X-User-Email` | E-mail associado ao usuário autenticado |
| `X-Internal-Key` | Chave para validar que a chamada veio de um componente confiável |
| `X-Token-Id` | Identificador único (`jti`) do token autenticado |

## Perfis

Os valores de perfil válidos são `CLIENTE` e `ADMINISTRADOR`. Comparações devem usar os nomes do contrato, não ordinais de enum.

## Evolução de banco

Migrações Flyway já aplicadas são imutáveis. Não editar V1 ou V2; toda mudança de schema deve ser criada em uma nova migração, começando em V3.