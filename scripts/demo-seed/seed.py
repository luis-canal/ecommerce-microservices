#!/usr/bin/env python3
"""Seed do ambiente de DEMONSTRAÇÃO da loja de chimarrão.

Popula usuários, catálogo, estoque, pedidos (≈90 dias) e carrinhos pelas APIs do
projeto, passando pelo Gateway. Exceções, todas restritas ao ambiente Docker local:

* confirmação de pedido: chamada interna ao order-api com X-Service-Token, lido do
  volume auth_internal_secret (somente leitura). O token fica só em memória e nunca
  é impresso nem gravado;
* datas passadas: um único UPDATE em db_order (tb_order) ao final, que mantém
  updated_at = horário atual para o analytics recoletar os pedidos.

Pagamento/Stripe não é chamado. Uso (a partir da raiz do repositório):

    docker compose -f docker/docker-compose.yml --profile seed run --rm demo-seed apply

Subcomandos: apply | status | pending | help. Sem subcomando, apenas mostra a ajuda.
"""

import json
import os
import random
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta, timezone

# --------------------------------------------------------------------------------------
# Configuração fixa: o seed só enxerga os serviços da rede interna do Compose.
# --------------------------------------------------------------------------------------
GATEWAY_URL = "http://gateway-api:8080"
ORDER_API_URL = "http://order-api:8300"
SERVICE_TOKEN_FILE = "/run/secrets/service_auth_token"
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")

DB_HOSTS = {
    "db_order": "order-db",
    "db_user": "auth-db",
    "db_product": "product-db",
    "db_inventory": "inventory-db",
}

DEFAULT_CUSTOMER_PASSWORD = "Demo@2026"  # SOMENTE para o ambiente de demonstração.

PLAN_VERSION = "v1"
KEY_PREFIX = f"demo-{PLAN_VERSION}-"
PENDING_KEY_PREFIX = "demo-pending-"
RNG_SEED = 20260920
TOTAL_ORDERS = 130
N_CANCELLED = 12
N_EXPIRED = 8
WINDOW_DAYS = 90
SAFE_STOCK_FOR_UNCONFIRMED = 8

BRT = timezone(timedelta(hours=-3))  # America/Sao_Paulo (sem horário de verão desde 2019)

# Pesos por hora do dia (0..23) para o horário dos pedidos.
HOUR_WEIGHTS = [0.2, 0.1, 0.05, 0.05, 0.05, 0.1, 0.3, 0.8, 1.2, 1.4, 1.5, 1.6,
                1.8, 1.6, 1.3, 1.2, 1.2, 1.3, 1.5, 1.9, 2.1, 1.9, 1.3, 0.6]


class SeedError(Exception):
    pass


def log(message=""):
    print(message, flush=True)


# --------------------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------------------
class HttpResult:
    def __init__(self, status, body):
        self.status = status
        self.body = body

    def json(self):
        if not self.body:
            return None
        try:
            return json.loads(self.body)
        except ValueError:
            return None

    def text(self):
        return (self.body or "").strip()[:300]


def http(method, url, token=None, body=None, headers=None, retry=True, attempts=8):
    """Executa uma requisição. Com retry=True, repete em erro de conexão e 502/503/504
    (usado só em operações idempotentes ou protegidas por Idempotency-Key)."""
    data = None
    all_headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        all_headers["Content-Type"] = "application/json"
    if token:
        all_headers["Authorization"] = f"Bearer {token}"
    if headers:
        all_headers.update(headers)

    last_error = None
    for attempt in range(1, (attempts if retry else 1) + 1):
        request = urllib.request.Request(url, data=data, method=method, headers=all_headers)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return HttpResult(response.status, response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            result = HttpResult(error.code, error.read().decode("utf-8", errors="replace"))
            if retry and error.code in (502, 503, 504) and attempt < attempts:
                last_error = f"HTTP {error.code}"
                time.sleep(min(2 * attempt, 10))
                continue
            return result
        except (urllib.error.URLError, ConnectionError, TimeoutError) as error:
            last_error = str(error)
            if retry and attempt < attempts:
                time.sleep(min(2 * attempt, 10))
                continue
            break
    raise SeedError(f"Falha de comunicação em {method} {safe_url(url)}: {last_error}")


def safe_url(url):
    return url.replace(GATEWAY_URL, "gateway").replace(ORDER_API_URL, "order-api")


def expect(result, statuses, what):
    if result.status not in statuses:
        raise SeedError(f"{what}: HTTP {result.status} - {result.text()}")
    return result.json()


def gw(path):
    return GATEWAY_URL + path


# --------------------------------------------------------------------------------------
# Banco (somente via psql na rede do Compose; credenciais de desenvolvimento do compose)
# --------------------------------------------------------------------------------------
def psql(database, sql):
    env = dict(os.environ)
    env.setdefault("PGUSER", "postgres")
    env.setdefault("PGPASSWORD", "postgres")
    completed = subprocess.run(
        ["psql", "-h", DB_HOSTS[database], "-d", database, "-v", "ON_ERROR_STOP=1",
         "-X", "-q", "-A", "-t", "-F", "|", "-f", "-"],
        input=sql, capture_output=True, text=True, env=env, timeout=120,
    )
    if completed.returncode != 0:
        raise SeedError(f"Erro SQL em {database}: {completed.stderr.strip()[:500]}")
    return [line.split("|") for line in completed.stdout.strip().splitlines() if line.strip()]


def sql_literal(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


# --------------------------------------------------------------------------------------
# Dados e configuração
# --------------------------------------------------------------------------------------
def load_json(name):
    with open(os.path.join(DATA_DIR, name), encoding="utf-8") as handle:
        return json.load(handle)


def load_settings(require_admin=True):
    admin_email = (os.environ.get("SEED_ADMIN_EMAIL") or "").strip()
    admin_password = os.environ.get("SEED_ADMIN_PASSWORD") or ""
    if require_admin and (not admin_email or not admin_password):
        raise SeedError(
            "Defina SEED_ADMIN_EMAIL e SEED_ADMIN_PASSWORD no ambiente do shell com as "
            "credenciais do administrador existente (admin@admin.dev; veja API.md, "
            "'Login com usuário administrador'). Nenhuma senha é lida de arquivo."
        )
    customer_password = os.environ.get("SEED_CUSTOMER_PASSWORD") or ""
    if not customer_password:
        customer_password = DEFAULT_CUSTOMER_PASSWORD
        log("Aviso: SEED_CUSTOMER_PASSWORD não definida; usando a senha padrão de DEMONSTRAÇÃO "
            "dos clientes. Nunca use essa senha em ambiente real.")
    return {"admin_email": admin_email, "admin_password": admin_password,
            "customer_password": customer_password}


def read_service_token():
    try:
        with open(SERVICE_TOKEN_FILE, encoding="utf-8") as handle:
            token = handle.read().strip()
    except OSError:
        raise SeedError("Não foi possível ler a credencial serviço-a-serviço no volume montado "
                        "(o consul-config já rodou?).") from None
    if len(token) < 32:
        raise SeedError("Credencial serviço-a-serviço inválida no volume montado.")
    return token


# --------------------------------------------------------------------------------------
# Prontidão
# --------------------------------------------------------------------------------------
def wait_for_gateway(timeout_seconds=300):
    log("Aguardando Gateway e catálogo responderem...")
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        try:
            result = http("GET", gw("/categories"), retry=False)
            if result.status == 200:
                return
        except SeedError:
            pass
        time.sleep(5)
    raise SeedError("Gateway/product-api não responderam em 5 minutos. Suba a stack com "
                    "--profile cart --profile analytics e tente de novo.")


# --------------------------------------------------------------------------------------
# Usuários
# --------------------------------------------------------------------------------------
def signin(email, password, what):
    result = http("POST", gw("/auth/signin"), body={"email": email, "password": password})
    if result.status == 401:
        raise SeedError(f"{what}: e-mail ou senha não autenticam ({email}).")
    data = expect(result, (200,), f"Login de {what}")
    return data["token"], data["user"]


def admin_login(settings):
    token, user = signin(settings["admin_email"], settings["admin_password"], "administrador")
    if user.get("type") not in ("Admin", 0):
        raise SeedError(f"{settings['admin_email']} não tem perfil de administrador.")
    return token


def ensure_customers(customers, password):
    log(f"Clientes: garantindo {len(customers)} contas...")
    created = 0
    for customer in customers:
        result = http("POST", gw("/auth/signup"), body={
            "name": customer["name"], "email": customer["email"], "password": password})
        if result.status == 201:
            created += 1
        elif not (result.status == 400 and "existe" in result.text().lower()):
            raise SeedError(f"Cadastro de {customer['email']}: HTTP {result.status} - {result.text()}")
        token, user = signin(customer["email"], password, "cliente")
        customer["token"] = token
        customer["id"] = int(user["id"])
    log(f"  {created} criados, {len(customers) - created} já existiam.")


# --------------------------------------------------------------------------------------
# Catálogo e estoque
# --------------------------------------------------------------------------------------
def load_subcategories():
    categories = expect(http("GET", gw("/categories")), (200,), "Listar categorias")
    index = {}
    for category in categories:
        subs = expect(http("GET", gw(f"/categories/{category['id']}/subcategories")), (200,),
                      "Listar subcategorias")
        for sub in subs:
            index[(category["slug"], sub["slug"])] = sub["id"]
    return index


def list_all_products(admin_token):
    products = []
    page = 0
    while True:
        query = urllib.parse.urlencode({"status": "ALL", "size": 100, "page": page, "sort": "name,asc"})
        data = expect(http("GET", gw(f"/ws/products?{query}"), token=admin_token), (200,),
                      "Listar produtos (admin)")
        products.extend(data.get("content", []))
        if data.get("last", True):
            return products
        page += 1


def index_by_name(products):
    index = {}
    for product in products:
        index.setdefault(product["name"], product)
    return index


def ensure_products(admin_token, catalog):
    log(f"Catálogo: garantindo {len(catalog)} produtos...")
    subcategories = load_subcategories()
    existing = index_by_name(list_all_products(admin_token))
    created = 0
    for product in catalog:
        key = (product["category"], product["subcategory"])
        if key not in subcategories:
            raise SeedError(f"Subcategoria {key} não encontrada (migração V3 aplicada?).")
        found = existing.get(product["name"])
        if found:
            product["id"] = int(found["id"])
            product["currentlyActive"] = bool(found["active"])
            continue
        body = {
            "name": product["name"], "brand": product["brand"], "model": product["model"],
            "description": product["description"], "currency": "BRL",
            "price": f"{product['price']:.2f}", "image": None,
            "subcategoryId": subcategories[key], "productType": product["productType"],
        }
        data = expect(http("POST", gw("/ws/products"), token=admin_token, body=body, retry=False),
                      (201,), f"Criar produto {product['name']}")
        product["id"] = int(data["id"])
        product["currentlyActive"] = True
        created += 1
    log(f"  {created} criados, {len(catalog) - created} já existiam.")


def set_product_active(admin_token, product_id, active, name):
    expect(http("PATCH", gw(f"/ws/products/{product_id}/status"), token=admin_token,
                body={"active": active}), (200,), f"Alterar status de {name}")


def ensure_inventory(admin_token, catalog, sold_units):
    log("Estoque: registrando produtos e saldos iniciais...")
    entries = 0
    for product in catalog:
        pid = product["id"]
        result = http("GET", gw(f"/ws/inventory/{pid}"), token=admin_token)
        if result.status == 404:
            expect(http("POST", gw(f"/ws/inventory/{pid}/registration"), token=admin_token),
                   (200, 201), f"Registrar estoque de {product['name']}")
            result = http("GET", gw(f"/ws/inventory/{pid}"), token=admin_token)
        inventory = expect(result, (200,), f"Consultar estoque de {product['name']}")

        movements = expect(http("GET", gw(f"/ws/inventory/{pid}/movements?size=1"), token=admin_token),
                           (200,), f"Consultar movimentações de {product['name']}")
        if movements.get("totalElements", 0) == 0:
            quantity = sold_units.get(product["name"], 0) + product["finalStock"]
            if product["productType"] == "SINGLE_UNIT":
                quantity = min(quantity, 1)
            if quantity > 0:
                expect(http("POST", gw(f"/ws/inventory/{pid}/entries"), token=admin_token, retry=False,
                            body={"quantity": quantity, "reason": "Estoque inicial (seed de demonstração)"}),
                       (200, 201), f"Entrada de estoque de {product['name']}")
                entries += 1

        threshold = product.get("lowStockThreshold", 0)
        if inventory.get("lowStockThreshold") != threshold:
            expect(http("PATCH", gw(f"/ws/inventory/{pid}/low-stock-threshold"), token=admin_token,
                        body={"threshold": threshold}), (200,),
                   f"Limite de estoque baixo de {product['name']}")
    log(f"  {entries} entradas iniciais registradas (produtos que já tinham histórico foram mantidos).")


# --------------------------------------------------------------------------------------
# Planejamento determinístico dos pedidos
# --------------------------------------------------------------------------------------
def weighted_sample(rng, items, weights, k):
    items, weights = list(items), list(weights)
    chosen = []
    for _ in range(min(k, len(items))):
        pick = rng.choices(range(len(items)), weights)[0]
        chosen.append(items.pop(pick))
        weights.pop(pick)
    return chosen


def pick_quantity(rng, product, status):
    if product["category"] == "erva-mate":
        quantity = rng.choices([1, 2, 3, 4, 5], [50, 30, 12, 6, 2])[0]
    else:
        quantity = rng.choices([1, 2], [82, 18])[0]
    quantity = min(quantity, product.get("maxQuantity", 1))
    if status != "CONFIRMED":
        quantity = min(quantity, 2 if status == "CANCELLED" else 1)
    return max(1, quantity)


def build_plan(catalog, customers):
    """Plano estável: independe da data de execução, garantindo o mesmo conteúdo para a
    mesma Idempotency-Key em execuções repetidas."""
    rng = random.Random(RNG_SEED)
    n_confirmed = TOTAL_ORDERS - N_CANCELLED - N_EXPIRED
    statuses = ["CONFIRMED"] * n_confirmed + ["CANCELLED"] * N_CANCELLED + ["EXPIRED"] * N_EXPIRED
    rng.shuffle(statuses)

    sellable = [p for p in catalog if p["productType"] == "MULTI_UNIT" and p["popularity"] > 0]
    safe = [p for p in sellable if p["active"] and p["finalStock"] >= SAFE_STOCK_FOR_UNCONFIRMED]
    inactive_cutoff = int(TOTAL_ORDERS * 0.6)  # produtos descontinuados só nos pedidos mais antigos
    customer_weights = [c.get("weight", 1) for c in customers]

    orders = []
    for index, status in enumerate(statuses):
        pool = sellable if status == "CONFIRMED" else safe
        if index >= inactive_cutoff:
            pool = [p for p in pool if p["active"]]
        size = rng.choices([1, 2, 3, 4], [45, 30, 18, 7])[0]
        if status == "EXPIRED":
            size = min(size, 2)
        chosen = weighted_sample(rng, pool, [p["popularity"] for p in pool], size)
        customer = rng.choices(customers, customer_weights)[0]
        orders.append({
            "key": f"{KEY_PREFIX}{index + 1:04d}",
            "index": index,
            "status": status,
            "customer": customer,
            "items": {p["name"]: pick_quantity(rng, p, status) for p in chosen},
        })

    confirmed = [o for o in orders if o["status"] == "CONFIRMED"]
    for product in catalog:
        if product["productType"] == "SINGLE_UNIT" and product.get("soldInDemo"):
            target = rng.choice([o for o in confirmed if not o.get("hasUnique")])
            target["items"][product["name"]] = 1
            target["hasUnique"] = True

    by_name = {p["name"]: p for p in catalog}
    held = {}
    for order in orders:
        if order["status"] == "EXPIRED":
            for name, quantity in order["items"].items():
                held[name] = held.get(name, 0) + quantity
    for name, quantity in held.items():
        if quantity > by_name[name]["finalStock"]:
            raise SeedError(f"Plano inválido: reservas expiradas de {name} excedem o estoque final.")
    return orders


def sold_units_of(plan):
    sold = {}
    for order in plan:
        if order["status"] == "CONFIRMED":
            for name, quantity in order["items"].items():
                sold[name] = sold.get(name, 0) + quantity
    return sold


def day_weight(day):
    weight = 1.0
    if day.weekday() >= 5:
        weight *= 1.35
    if day.day <= 7:
        weight *= 1.25
    if day.month == 9 and 14 <= day.day <= 20:  # Semana Farroupilha
        weight *= 3.6 if day.day == 20 else 2.6
    if day.month == 11 and 10 <= day.day <= 17:  # esquenta da Black November
        weight *= 2.2
    return weight


def plan_dates(count, now_brt):
    rng = random.Random(f"dates-{RNG_SEED}")
    today = now_brt.date()
    days = [today - timedelta(days=offset) for offset in range(WINDOW_DAYS - 1, -1, -1)]
    picked = rng.choices(days, [day_weight(d) for d in days], k=count)
    moments = []
    for day in picked:
        hour = rng.choices(range(24), HOUR_WEIGHTS)[0]
        moment = datetime(day.year, day.month, day.day, hour, rng.randrange(60), rng.randrange(60),
                          tzinfo=BRT)
        if moment > now_brt - timedelta(minutes=30):
            moment -= timedelta(days=1)
        moments.append(moment)
    return sorted(moments)


# --------------------------------------------------------------------------------------
# Pedidos
# --------------------------------------------------------------------------------------
def order_body(order, by_name):
    items = [{"productId": by_name[name]["id"], "quantity": quantity}
             for name, quantity in order["items"].items()]
    return {"items": sorted(items, key=lambda item: item["productId"]),
            "shippingAddress": order["customer"]["address"]}


def create_order(order, by_name):
    customer = order["customer"]
    result = http("POST", gw("/ws/orders"), token=customer["token"], body=order_body(order, by_name),
                  headers={"Idempotency-Key": order["key"]})
    return expect(result, (200, 201), f"Criar pedido {order['key']}")


def confirm_order(order_id, service_token):
    # Chamada interna direta ao order-api (o Gateway remove X-Service-Token).
    result = http("POST", f"{ORDER_API_URL}/ws/orders/{order_id}/confirm",
                  headers={"X-Service-Token": service_token})
    return expect(result, (200,), f"Confirmar pedido {order_id}")


def admin_cancel(order_id, admin_token):
    result = http("POST", gw(f"/ws/admin/orders/{order_id}/cancel"), token=admin_token)
    return expect(result, (200,), f"Cancelar pedido {order_id}")


def admin_get_order(order_id, admin_token):
    return expect(http("GET", gw(f"/ws/admin/orders/{order_id}"), token=admin_token), (200,),
                  f"Consultar pedido {order_id}")


def seed_order_count():
    rows = psql("db_order", f"SELECT count(*) FROM tb_order WHERE idempotency_key LIKE '{KEY_PREFIX}%';")
    return int(rows[0][0])


def run_orders(plan, by_name, admin_token, service_token):
    deviations = []
    first_batch = [o for o in plan if o["status"] != "EXPIRED"]
    expiring = [o for o in plan if o["status"] == "EXPIRED"]

    log(f"Pedidos: criando/confirmando/cancelando {len(first_batch)} pedidos...")
    for position, order in enumerate(first_batch, start=1):
        data = create_order(order, by_name)
        order["id"] = int(data["id"])
        status = data["status"]
        if status == "PENDING_PAYMENT":
            if order["status"] == "CONFIRMED":
                status = confirm_order(order["id"], service_token)["status"]
            elif order["status"] == "CANCELLED":
                status = admin_cancel(order["id"], admin_token)["status"]
        order["actual"] = status
        if status != order["status"]:
            deviations.append((order["key"], order["status"], status))
        if position % 20 == 0:
            log(f"  {position}/{len(first_batch)}")

    log(f"Pedidos: criando {len(expiring)} pedidos que vão expirar pelo TTL da reserva (10 min)...")
    for order in expiring:
        data = create_order(order, by_name)
        order["id"] = int(data["id"])
        order["actual"] = data["status"]
    return deviations


def wait_for_expiration(expiring, admin_token, timeout_seconds=15 * 60):
    pending = [o for o in expiring if o.get("actual") in ("PENDING_PAYMENT", "RESERVATION_PENDING")]
    if not pending:
        return
    log(f"Aguardando a expiração de {len(pending)} reservas (até ~11 min; o inventory expira em 10 min "
        "e o order-api reconcilia a cada 30 s)...")
    deadline = time.time() + timeout_seconds
    started = time.time()
    while pending and time.time() < deadline:
        time.sleep(20)
        still = []
        for order in pending:
            status = admin_get_order(order["id"], admin_token)["status"]
            order["actual"] = status
            if status in ("PENDING_PAYMENT", "RESERVATION_PENDING"):
                still.append(order)
        if len(still) != len(pending):
            log(f"  {len(pending) - len(still)} expiraram ({int(time.time() - started)} s)")
        pending = still
    if pending:
        raise SeedError(f"{len(pending)} pedidos ainda pendentes após {timeout_seconds // 60} min. "
                        "Rode 'apply' de novo mais tarde para concluir.")


# --------------------------------------------------------------------------------------
# Datas passadas (db_order)
# --------------------------------------------------------------------------------------
def backdate_orders(plan, now_brt):
    """Ajusta as datas de tb_order (única tabela do order-api com timestamps de pedido; os
    itens não têm datas). updated_at recebe o horário ATUAL: o analytics ignora versões com
    updatedAt mais antigo do que a já coletada, então nunca é retrocedido."""
    moments = plan_dates(len(plan), now_brt)
    rows = []
    for order, moment in zip(plan, moments):
        offsets = random.Random(order["key"])
        confirm_offset = offsets.randint(60, 540)
        cancel_offset = offsets.randint(120, 540)
        order["plannedDate"] = moment
        rows.append(f"({sql_literal(order['key'])}, {order['customer']['id']}, "
                    f"{sql_literal(moment.isoformat())}::timestamptz, {confirm_offset}, {cancel_offset})")
    values = ",\n".join(rows)
    sql = f"""
BEGIN;
CREATE TEMP TABLE seed_dates (
    idempotency_key text PRIMARY KEY, customer_id bigint, order_date timestamptz,
    confirm_offset int, cancel_offset int
) ON COMMIT DROP;
INSERT INTO seed_dates VALUES
{values};
WITH target AS (
    SELECT o.id,
           s.order_date,
           CASE WHEN o.confirmed_at IS NULL THEN NULL
                ELSE s.order_date + make_interval(secs => s.confirm_offset) END AS confirmed_at,
           CASE WHEN o.cancelled_at IS NULL THEN NULL
                ELSE s.order_date + make_interval(secs => s.cancel_offset) END AS cancelled_at,
           CASE WHEN o.reservation_expires_at IS NULL THEN NULL
                ELSE s.order_date + interval '10 minutes' END AS reservation_expires_at
    FROM tb_order o
    JOIN seed_dates s ON s.idempotency_key = o.idempotency_key AND s.customer_id = o.customer_id
    WHERE o.status IN ('CONFIRMED', 'CANCELLED', 'EXPIRED', 'FAILED')
), changed AS (
    UPDATE tb_order o
    SET order_date = t.order_date,
        confirmed_at = t.confirmed_at,
        cancelled_at = t.cancelled_at,
        reservation_expires_at = t.reservation_expires_at,
        updated_at = clock_timestamp()
    FROM target t
    WHERE o.id = t.id
      AND (o.order_date, o.confirmed_at, o.cancelled_at, o.reservation_expires_at)
          IS DISTINCT FROM (t.order_date, t.confirmed_at, t.cancelled_at, t.reservation_expires_at)
    RETURNING 1
)
SELECT count(*) FROM changed;
COMMIT;
"""
    updated = int(psql("db_order", sql)[0][0])
    log(f"Datas: {updated} pedidos ajustados para os últimos {WINDOW_DAYS} dias "
        f"({len(plan) - updated} já estavam com as datas planejadas).")


# --------------------------------------------------------------------------------------
# Carrinhos
# --------------------------------------------------------------------------------------
def ensure_carts(carts, customers_by_email, by_name):
    log(f"Carrinhos: preenchendo {len(carts)} carrinhos...")
    for cart in carts:
        customer = customers_by_email[cart["email"]]
        for item in cart["items"]:
            product = by_name[item["product"]]
            result = http("POST", gw("/ws/cart/items"), token=customer["token"],
                          body={"productId": product["id"], "quantity": item["quantity"]})
            if result.status != 200:
                log(f"  Aviso: item '{item['product']}' no carrinho de {cart['email']}: "
                    f"HTTP {result.status} - {result.text()}")


# --------------------------------------------------------------------------------------
# Analytics e resumo
# --------------------------------------------------------------------------------------
def run_collection(admin_token):
    log("Analytics: disparando a coleta...")
    for _ in range(30):
        result = http("POST", gw("/ws/admin/analytics/collections/run"), token=admin_token)
        if result.status == 200:
            data = result.json() or {}
            for source in data.get("sources", []):
                log(f"  {source['source']}: {source['lastStatus']}"
                    + (f" - {source['lastError']}" if source.get("lastError") else ""))
            return True
        if result.status == 202:
            time.sleep(5)
            continue
        log(f"  Aviso: coleta não executada (HTTP {result.status} - {result.text()}). "
            "Suba o profile analytics e rode 'status'.")
        return False
    log("  Aviso: havia uma coleta em andamento por muito tempo; rode 'status' depois.")
    return False


def summary(admin_token):
    now_brt = datetime.now(BRT)
    to_day = now_brt.date()
    from_day = to_day - timedelta(days=WINDOW_DAYS - 1)

    users = dict((row[0], int(row[1])) for row in psql(
        "db_user", "SELECT CASE type WHEN 0 THEN 'admin' ELSE 'cliente' END, count(*) "
                   "FROM tb_user GROUP BY 1;"))
    demo_users = int(psql("db_user", "SELECT count(*) FROM tb_user WHERE email LIKE '%@demo.example.com';")[0][0])
    products = psql("db_product", "SELECT count(*) FILTER (WHERE active), count(*) FILTER (WHERE NOT active) "
                                  "FROM tb_product;")[0]
    by_status = psql("db_order", f"""
SELECT status, count(*), coalesce(sum(total_amount), 0)
FROM tb_order WHERE idempotency_key LIKE '{KEY_PREFIX}%' OR idempotency_key LIKE '{PENDING_KEY_PREFIX}%'
GROUP BY status ORDER BY status;""")
    window = psql("db_order", f"""
SELECT count(*), coalesce(sum(total_amount), 0), coalesce(sum(units), 0)
FROM tb_order o
JOIN LATERAL (SELECT sum(quantity) AS units FROM tb_order_item i WHERE i.order_id = o.id) u ON true
WHERE status = 'CONFIRMED' AND currency = 'BRL'
  AND (confirmed_at AT TIME ZONE 'America/Sao_Paulo')::date BETWEEN '{from_day}' AND '{to_day}';""")[0]
    regions = psql("db_order", f"""
SELECT CASE WHEN shipping_state IN ('RS','SC','PR') THEN 'SUL'
            WHEN shipping_state IN ('SP','RJ','MG','ES') THEN 'SUDESTE'
            WHEN shipping_state IN ('DF','GO','MT','MS') THEN 'CENTRO_OESTE'
            WHEN shipping_state IN ('AM','PA','AC','RO','RR','AP','TO') THEN 'NORTE'
            ELSE 'NORDESTE' END AS region, count(*), sum(total_amount)
FROM tb_order WHERE status = 'CONFIRMED' AND currency = 'BRL'
  AND (confirmed_at AT TIME ZONE 'America/Sao_Paulo')::date BETWEEN '{from_day}' AND '{to_day}'
GROUP BY 1 ORDER BY 3 DESC;""")
    first_last = psql("db_order", f"""
SELECT min(order_date AT TIME ZONE 'America/Sao_Paulo')::date, max(order_date AT TIME ZONE 'America/Sao_Paulo')::date
FROM tb_order WHERE idempotency_key LIKE '{KEY_PREFIX}%';""")[0]

    log("")
    log("================ RESUMO ================")
    log(f"Usuários: {users.get('cliente', 0)} clientes ({demo_users} de demonstração) e {users.get('admin', 0)} admin")
    log(f"Produtos: {products[0]} ativos, {products[1]} inativos")
    log(f"Pedidos do seed ({first_last[0]} a {first_last[1]}):")
    total_orders = 0
    for status, count, amount in by_status:
        total_orders += int(count)
        log(f"  {status:<16} {int(count):>4}   R$ {float(amount):>10,.2f}")
    log(f"  {'TOTAL':<16} {total_orders:>4}")
    log(f"Banco (order-api), CONFIRMED em BRL de {from_day} a {to_day}: "
        f"{window[0]} pedidos, R$ {float(window[1]):,.2f}, {window[2]} unidades")
    for region, count, amount in regions:
        log(f"  {region:<13} {int(count):>4} pedidos   R$ {float(amount):>10,.2f}")

    query = urllib.parse.urlencode({"from": from_day.isoformat(), "to": to_day.isoformat()})
    result = http("GET", gw(f"/ws/admin/analytics/overview?{query}"), token=admin_token)
    if result.status != 200:
        log(f"Analytics indisponível (HTTP {result.status}); conferência não realizada.")
        return
    overview = result.json()
    log(f"Analytics overview ({from_day} a {to_day}): {overview['confirmedOrders']} pedidos, "
        f"R$ {float(overview['totalRevenue']):,.2f}, ticket médio R$ {float(overview['averageTicket']):,.2f}, "
        f"{overview['unitsSold']} unidades")
    matches = (int(overview["confirmedOrders"]) == int(window[0])
               and abs(float(overview["totalRevenue"]) - float(window[1])) < 0.005
               and int(overview["unitsSold"]) == int(window[2]))
    log("Conferência banco x analytics: " + ("OK" if matches else
        "DIVERGENTE (a coleta pode não ter rodado ainda; rode 'status' após a próxima coleta)"))

    regions_result = http("GET", gw(f"/ws/admin/analytics/sales/by-region?{query}"), token=admin_token)
    if regions_result.status == 200:
        for region in regions_result.json().get("regions", []):
            log(f"  analytics {region['region']:<13} {region['orders']:>4} pedidos   "
                f"R$ {float(region['revenue']):>10,.2f}")
    inventory = http("GET", gw("/ws/admin/analytics/inventory"), token=admin_token)
    if inventory.status == 200:
        s = inventory.json().get("summary") or {}
        log(f"Estoque (último snapshot): {s.get('inventoryCount')} itens, {s.get('quantityOnHand')} un. físicas, "
            f"{s.get('quantityReserved')} reservadas, {s.get('outOfStockCount')} sem estoque, "
            f"{s.get('lowStockCount')} com estoque baixo")


# --------------------------------------------------------------------------------------
# Subcomandos
# --------------------------------------------------------------------------------------
def prepare(require_catalog=True):
    settings = load_settings()
    wait_for_gateway()
    admin_token = admin_login(settings)
    customers_data = load_json("customers.json")
    catalog = load_json("products.json")
    customers = customers_data["customers"]
    ensure_customers(customers, settings["customer_password"])
    return settings, admin_token, customers_data, catalog, customers


def command_apply():
    started = time.time()
    settings, admin_token, customers_data, catalog_data, customers = prepare()
    catalog = catalog_data["products"]
    service_token = read_service_token()

    ensure_products(admin_token, catalog)
    by_name = {p["name"]: p for p in catalog}
    plan = build_plan(catalog, customers)
    ensure_inventory(admin_token, catalog, sold_units_of(plan))

    # Em reexecução incompleta, produtos descontinuados podem precisar estar ativos para criar
    # pedidos faltantes; são desativados de novo logo depois.
    if seed_order_count() < len(plan):
        for product in catalog:
            if not product["active"] and not product["currentlyActive"]:
                set_product_active(admin_token, product["id"], True, product["name"])
                product["currentlyActive"] = True

    deviations = run_orders(plan, by_name, admin_token, service_token)

    log("Catálogo: desativando produtos descontinuados e os 24 exemplos da migração V5...")
    deactivated = 0
    for product in catalog:
        if not product["active"]:
            if product["currentlyActive"]:
                set_product_active(admin_token, product["id"], False, product["name"])
                deactivated += 1
    existing = index_by_name(list_all_products(admin_token))
    for name in catalog_data.get("deactivateExisting", []):
        found = existing.get(name)
        if found is None:
            log(f"  Aviso: produto de exemplo '{name}' não encontrado; ignorado.")
        elif found["active"]:
            set_product_active(admin_token, found["id"], False, name)
            deactivated += 1
    log(f"  {deactivated} produtos desativados.")

    wait_for_expiration([o for o in plan if o["status"] == "EXPIRED"], admin_token)
    for order in plan:
        if order["status"] == "EXPIRED" and order["actual"] != "EXPIRED":
            deviations.append((order["key"], "EXPIRED", order["actual"]))

    ensure_carts(customers_data.get("carts", []), {c["email"]: c for c in customers}, by_name)
    backdate_orders(plan, datetime.now(BRT))
    run_collection(admin_token)

    if deviations:
        log(f"Aviso: {len(deviations)} pedidos com status diferente do planejado:")
        for key, planned, actual in deviations[:20]:
            log(f"  {key}: planejado {planned}, atual {actual}")
    summary(admin_token)
    log(f"Concluído em {int(time.time() - started)} s.")


def command_status():
    settings = load_settings()
    wait_for_gateway()
    summary(admin_login(settings))


def command_pending():
    settings, admin_token, customers_data, catalog_data, customers = prepare()
    catalog = catalog_data["products"]
    existing = index_by_name(list_all_products(admin_token))
    for product in catalog:
        if product["name"] not in existing:
            raise SeedError("Catálogo da demonstração ausente; rode 'apply' primeiro.")
        product["id"] = int(existing[product["name"]]["id"])
    by_name = {p["name"]: p for p in catalog}
    safe = [p for p in catalog if p["active"] and p["productType"] == "MULTI_UNIT"
            and p["finalStock"] >= SAFE_STOCK_FOR_UNCONFIRMED]
    rng = random.Random()
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
    log("Criando 3 pedidos PENDING_PAYMENT (a reserva expira em 10 minutos)...")
    for number, customer in enumerate(rng.sample(customers, 3), start=1):
        chosen = weighted_sample(rng, safe, [p["popularity"] for p in safe], rng.randint(1, 3))
        order = {"key": f"{PENDING_KEY_PREFIX}{stamp}-{number}", "customer": customer,
                 "items": {p["name"]: 1 for p in chosen}}
        data = create_order(order, by_name)
        log(f"  Pedido {data['id']} de {customer['name']} ({customer['address']['state']}): "
            f"{data['status']}, total R$ {float(data['totalAmount']):,.2f}, expira em {data.get('reservationExpiresAt')}")


HELP = """Seed do ambiente de DEMONSTRAÇÃO (nunca use em ambiente real).

Uso, a partir da raiz do repositório (Git Bash ou PowerShell):
  docker compose -f docker/docker-compose.yml --profile seed run --rm demo-seed <comando>

Comandos:
  apply    cria/completa usuários, catálogo, estoque, ~130 pedidos em 90 dias e carrinhos
           (idempotente; leva ~12 min por esperar o TTL de 10 min das reservas)
  status   mostra os totais no banco e confere com o analytics
  pending  cria 3 pedidos PENDING_PAYMENT na hora (expiram em 10 min), para a apresentação
  help     esta ajuda

Variáveis (ambiente do shell):
  SEED_ADMIN_EMAIL, SEED_ADMIN_PASSWORD  obrigatórias: administrador existente
  SEED_CUSTOMER_PASSWORD                  opcional: senha dos clientes de demonstração
"""


def main(argv):
    command = argv[1] if len(argv) > 1 else "help"
    commands = {"apply": command_apply, "status": command_status, "pending": command_pending}
    if command not in commands:
        log(HELP)
        return 0 if command in ("help", "-h", "--help") else 2
    try:
        commands[command]()
    except SeedError as error:
        log(f"ERRO: {error}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
