# python-test

Async Python script that probes the two POST endpoints to compare behaviour
under duplicate and concurrent requests.

## Run

The script declares its own dependencies inline (PEP 723), so `uv` handles the
venv transparently — no setup step:

```zsh
uv run test_idempotency.py
```

The harness must be running on `localhost:8080` (`docker compose up --build`
from the parent dir).

## What it does

For each endpoint (`/orders` and `/orders/idempotent`):

1. Sends two sequential requests with the same `customerOrderReference`.
2. Sends five concurrent requests with the same `customerOrderReference`.

For each scenario it prints the distribution of HTTP status codes and how many
distinct successful response bodies were returned. The plain endpoint should
yield `409 Conflict` on duplicates; the idempotent endpoint should return the
same `200` body every time.
