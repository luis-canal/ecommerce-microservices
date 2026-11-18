CREATE TABLE payment (
    id                          BIGSERIAL PRIMARY KEY,
    order_id                    BIGINT         NOT NULL,
    customer_id                 BIGINT         NOT NULL,
    amount                      NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency                    VARCHAR(3)     NOT NULL,
    status                      VARCHAR(16)    NOT NULL,
    stripe_checkout_session_id  VARCHAR(255),
    stripe_payment_intent_id    VARCHAR(255),
    checkout_url                TEXT,
    reservation_expires_at      TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ    NOT NULL,
    updated_at                  TIMESTAMPTZ    NOT NULL,
    confirmed_at                TIMESTAMPTZ,
    CONSTRAINT uk_payment_checkout_session UNIQUE (stripe_checkout_session_id),
    CONSTRAINT ck_payment_status CHECK (status IN
        ('CREATED', 'PENDING', 'APPROVED', 'FAILED', 'EXPIRED', 'CANCELLED', 'REFUNDED'))
);

-- No máximo uma tentativa aberta por pedido: impede pagamentos duplicados,
-- inclusive entre requisições simultâneas.
CREATE UNIQUE INDEX uk_payment_open_per_order
    ON payment (order_id)
    WHERE status IN ('CREATED', 'PENDING');

CREATE INDEX idx_payment_order_customer ON payment (order_id, customer_id);

-- Eventos do Stripe já processados (deduplicação dos webhooks).
CREATE TABLE processed_stripe_event (
    event_id      VARCHAR(255) PRIMARY KEY,
    type          VARCHAR(255) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL
);
