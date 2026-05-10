# Idempotency Evaluation

Idempotency is critical for financial applications. You would think that libraries like this would be standard and well-used.

This project compares
different ways to make REST operations idempotent by running each candidate
implementation against the same wire-level contract and the same black-box
test harness.

[![Watch the video for the basic concept](https://img.youtube.com/vi/IP-rGJKSZ3s/maxresdefault.jpg)](https://www.youtube.com/watch?v=IP-rGJKSZ3s)

## The contract

Every implementation MUST conform to these REST calls. The optional
`delayMillis` query parameter slows the handler so duplicates can be tested
while the first call is still in flight.

The `arun0009lib` implementation is the reference implementation and is available at http://localhost:8080/v3/api-docs

### `POST /orders` — plain endpoint

```zsh
curl -X POST 'http://localhost:8080/orders?delayMillis=100' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 0c5b8d7e-4f1c-4f6a-9f9b-1f4e1c5d6b7a' \
  -d '{"customerOrderReference":"customer-123"}'
```

A second call with the same `customerOrderReference` returns `409 Conflict` —
the plain endpoint is not idempotent and rejects duplicates.

### `POST /orders/idempotent` — idempotent endpoint

```zsh
curl -X POST 'http://localhost:8080/orders/idempotent?delayMillis=5000' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 0c5b8d7e-4f1c-4f6a-9f9b-1f4e1c5d6b7a' \
  -d '{"customerOrderReference":"customer-456"}'
```

Duplicate calls with the same `Idempotency-Key` replay the original response,
both while the first call is in flight and after it has completed. A missing
or blank `Idempotency-Key` returns `400`.

### `GET /orders` — list

```zsh
curl http://localhost:8080/orders
```
Each implementation should have two or more instances of the service running to demonstrate that idempotency works in high availability.

# Implementations

## arun0009lib

```zsh
cd arun0009lib
docker compose up
```

It binds to `http://localhost:8080`.

This is how it responds
![arun0009lib demo timeline](/arun0009lib/arun0009lib.png)

# Testing

## `test_idempotency.http`

In Intellij you can evaluate the http calls yourself.

## `test_idempotency.py`

You can test automatically with the Python script

```zsh
uv run test_idempotency.py
```
