CREATE TABLE IF NOT EXISTS lootbox_items (
    box_serial UUID PRIMARY KEY,
    box_id VARCHAR(96) NOT NULL,
    status VARCHAR(16) NOT NULL,
    issued_to UUID NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    opened_by UUID NULL,
    opened_at TIMESTAMPTZ NULL,
    CHECK (status IN ('ISSUED', 'OPENING', 'OPENED', 'VOIDED'))
);

CREATE TABLE IF NOT EXISTS lootbox_pools (
    entry_id BIGSERIAL PRIMARY KEY,
    pool_id VARCHAR(96) NOT NULL,
    item_json JSONB NOT NULL,
    weight INT NOT NULL,
    tier VARCHAR(24) NOT NULL,
    amount_min INT NOT NULL,
    amount_max INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    source VARCHAR(24) NOT NULL DEFAULT 'ADMIN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (weight > 0),
    CHECK (amount_min > 0),
    CHECK (amount_max >= amount_min)
);

CREATE TABLE IF NOT EXISTS lootbox_history (
    history_id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL,
    box_id VARCHAR(96) NOT NULL,
    pool_id VARCHAR(96) NOT NULL,
    result_item_json JSONB NOT NULL,
    result_tier VARCHAR(24) NOT NULL,
    result_amount INT NOT NULL,
    delivered_via VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    box_serial UUID NOT NULL REFERENCES lootbox_items(box_serial),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (delivered_via IN ('INVENTORY', 'MAIL')),
    CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_lootbox_pools_pool_enabled
    ON lootbox_pools(pool_id, enabled);

CREATE INDEX IF NOT EXISTS idx_lootbox_history_uuid_created
    ON lootbox_history(uuid, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_lootbox_history_box_created
    ON lootbox_history(box_id, created_at DESC);
