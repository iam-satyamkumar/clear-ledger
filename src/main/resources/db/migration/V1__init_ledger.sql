CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);


CREATE TABLE accounts (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users (id),
    cached_balance   NUMERIC(19, 4) NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);


CREATE TABLE transfers (
    id                BIGSERIAL PRIMARY KEY,
    from_account_id   BIGINT NOT NULL REFERENCES accounts (id),
    to_account_id     BIGINT NOT NULL REFERENCES accounts (id),
    amount            NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (from_account_id <> to_account_id)
);


CREATE TABLE ledger_entries (
    id            BIGSERIAL PRIMARY KEY,
    account_id    BIGINT NOT NULL REFERENCES accounts (id),
    transfer_id   BIGINT NOT NULL REFERENCES transfers (id),
    amount        NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    direction     VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);


CREATE TABLE idempotency_keys (
    id            BIGSERIAL PRIMARY KEY,
    key_value     VARCHAR(255) NOT NULL UNIQUE,
    transfer_id   BIGINT NOT NULL REFERENCES transfers (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
