#!/usr/bin/env bash
# Cadastra estoque inicial para os produtos ativos do catálogo.
# Usa a API pelo Gateway (mesmas regras de negócio e histórico de movimentações).
#
# Para cada produto ativo:
#   - sem registro no estoque: registra e lança a entrada inicial;
#   - registrado com saldo físico zero: trata como pendente e lança a entrada inicial;
#   - registrado com saldo: ignora.
# Produtos SINGLE_UNIT recebem 1 unidade (o inventory-api não aceita mais que isso);
# os demais recebem SEED_QUANTITY. Pode rodar várias vezes.
#
# Uso (com o back-end no ar), a partir da raiz do repositório:
#   bash docker/scripts/seed-inventory.sh
# Variáveis opcionais:
#   GATEWAY_URL     (padrão http://localhost:8080)
#   ADMIN_EMAIL     (padrão admin@admin.dev, do seed do auth-api)
#   ADMIN_PASSWORD  (se ausente, é pedida sem eco no terminal)
#   SEED_QUANTITY   (padrão 50, para produtos MULTI_UNIT)
#   SEED_REASON     (padrão "Carga inicial para testes")
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@admin.dev}"
SEED_QUANTITY="${SEED_QUANTITY:-50}"
SEED_REASON="${SEED_REASON:-Carga inicial para testes}"

command -v curl >/dev/null || { echo "curl não encontrado." >&2; exit 1; }
if command -v python3 >/dev/null && python3 -c '' 2>/dev/null; then PY=python3; else PY=python; fi

if [ -z "${ADMIN_PASSWORD:-}" ]; then
    read -r -s -p "Senha de ${ADMIN_EMAIL}: " ADMIN_PASSWORD; echo
fi

# Login: a senha vai pela entrada padrão, não aparece na linha de comando nem em log.
TOKEN=$(ADMIN_EMAIL="$ADMIN_EMAIL" ADMIN_PASSWORD="$ADMIN_PASSWORD" $PY -c \
    'import json,os;print(json.dumps({"email":os.environ["ADMIN_EMAIL"],"password":os.environ["ADMIN_PASSWORD"]}))' \
    | curl -sf -X POST "$GATEWAY_URL/auth/signin" -H 'Content-Type: application/json' --data-binary @- \
    | $PY -c 'import json,sys;print(json.load(sys.stdin)["token"])' 2>/dev/null) \
    || { echo "Falha no login de administrador." >&2; exit 1; }
unset ADMIN_PASSWORD
AUTH="Authorization: Bearer $TOKEN"

# Lê um campo de um JSON recebido pela entrada padrão (vazio se ausente ou inválido).
field() { $PY -c 'import json,sys
try: print(json.load(sys.stdin).get(sys.argv[1], ""))
except Exception: print("")' "$1"; }

# Todos os produtos ativos (catálogo público).
IDS=$(curl -sf "$GATEWAY_URL/products?size=200" \
    | $PY -c 'import json,sys;print(" ".join(str(p["id"]) for p in json.load(sys.stdin)["content"]))')

TMP=$(mktemp); trap 'rm -f "$TMP"' EXIT
created=0; pending=0; skipped=0; failed=0

for id in $IDS; do
    status=$(curl -s -o "$TMP" -w '%{http_code}' -H "$AUTH" "$GATEWAY_URL/ws/inventory/$id")
    case "$status" in
        200)
            if [ "$(field quantityOnHand < "$TMP")" != 0 ]; then
                skipped=$((skipped + 1)); continue
            fi
            kind=pendente ;;
        404)
            status=$(curl -s -o "$TMP" -w '%{http_code}' -X POST -H "$AUTH" "$GATEWAY_URL/ws/inventory/$id/registration")
            if [[ ! "$status" =~ ^2 ]]; then
                echo "Produto $id: registration=$status $(field message < "$TMP")" >&2; failed=$((failed + 1)); continue
            fi
            kind=novo ;;
        *)
            echo "Produto $id: consulta respondeu $status $(field message < "$TMP"); ignorado." >&2
            failed=$((failed + 1)); continue ;;
    esac

    type=$(field productType < "$TMP")
    if [ "$type" = SINGLE_UNIT ]; then qty=1; else qty="$SEED_QUANTITY"; fi
    body=$(QTY="$qty" SEED_REASON="$SEED_REASON" $PY -c \
        'import json,os;print(json.dumps({"quantity":int(os.environ["QTY"]),"reason":os.environ["SEED_REASON"]}))')

    status=$(curl -s -o "$TMP" -w '%{http_code}' -X POST -H "$AUTH" -H 'Content-Type: application/json' \
        --data-binary "$body" "$GATEWAY_URL/ws/inventory/$id/entries")
    if [[ "$status" =~ ^2 ]]; then
        echo "Produto $id ($type, $kind): entrada de $qty unidade(s)."
        if [ "$kind" = novo ]; then created=$((created + 1)); else pending=$((pending + 1)); fi
    else
        echo "Produto $id ($type, $kind): entries=$status $(field message < "$TMP")" >&2; failed=$((failed + 1))
    fi
done

echo "Concluído: $created cadastrados, $pending pendentes completados, $skipped já tinham estoque, $failed com falha."
[ "$failed" -eq 0 ]
