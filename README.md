# ClearLedger

A small Spring Boot ledger API: move money between accounts with double-entry bookkeeping, optimistic locking, and idempotent transfers.

## Stack

- Java 17, Spring Boot 3.4.5
- PostgreSQL 16, Flyway, JPA (Hibernate `ddl-auto: validate`)
- Docker Compose (local dev), Testcontainers (integration tests)

## Prerequisites

- Java 17
- Docker Desktop (for local Postgres and for `./mvnw test`)


## Quick start

```bash
# 1. Copy local env (optional — application.yml defaults match .env.example)
cp .env.example .env

# 2. Start Postgres
docker compose up -d
docker compose ps   # wait until STATUS is "healthy"

# 3. Run the app (Flyway applies V1 on first start)
./mvnw spring-boot:run
```

App runs at `http://localhost:8080`.

### Seed demo data (new database only)

```bash
docker compose exec postgres psql -U clearledger -d clearledger
```

```sql
INSERT INTO users (email, password_hash, created_at)
VALUES
  ('alice@test.com', 'not-a-real-password', NOW()),
  ('bob@test.com', 'not-a-real-password', NOW());

INSERT INTO accounts (user_id, cached_balance, created_at)
VALUES
  (1, 1000.0000, NOW()),
  (2, 500.0000, NOW());
```

Type `\q` to exit `psql`.

### Create a transfer

```bash
curl -X POST http://localhost:8080/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-key-1" \
  -d '{"fromAccountId":1,"toAccountId":2,"amount":100}'
```

Reuse the same `Idempotency-Key` to simulate a retry — the transfer is not applied twice.

Health check: `GET http://localhost:8080/actuator/health`

## Tests

```bash
./mvnw test
```

Integration tests use Testcontainers (ephemeral Postgres). Docker must be running, but `docker compose up` is not required for tests.

## API

| Method | Path | Notes |
|--------|------|-------|
| `POST` | `/transfers` | Requires `Idempotency-Key` header; body: `fromAccountId`, `toAccountId`, `amount` |
| `GET` | `/actuator/health` | Health check |

### Example request body

```json
{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 100
}
```

## Project layout

| Path | Purpose |
|------|---------|
| `src/main/resources/db/migration/` | Flyway SQL migrations (schema) |
| `docker-compose.yml` | Local Postgres for development |
| `.env.example` | Template for DB credentials (copy to `.env`) |

## Useful commands

| Command | Description |
|---------|-------------|
| `docker compose up -d` | Start Postgres in the background |
| `docker compose down` | Stop Postgres (keeps data volume) |
| `docker compose down -v` | Stop Postgres and **wipe all data** |
| `./mvnw spring-boot:run` | Start the application |
| `./mvnw test` | Run all tests |
