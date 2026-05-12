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

## aws-powertools

AWS API Gateway HTTP API + Lambda (Java 25), using AWS Lambda Powertools'
`@Idempotent` annotation backed by DynamoDB. Provisioned via AWS SAM.

```zsh
cd aws-powertools
./deploy.sh
```

`deploy.sh` writes the deployed HTTP API base URL to `aws-powertools/url.txt`.
To tear down:

```zsh
cd aws-powertools
./destroy.sh
```

This is how it responds:
![aws-powertools demo timeline](/aws-powertools/aws-powertools.png)

HA is provided implicitly by Lambda's concurrent execution environments
(rather than two replicas behind Traefik, as in `arun0009lib`).

# Comparison against the IETF Idempotency-Key spec

Both implementations are evaluated against
[draft-ietf-httpapi-idempotency-key-header-01](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-01.html).

| IETF spec requirement | Spec | `arun0009lib` | `aws-powertools` |
|---|---|---|---|
| **Idempotency-Key header** (§2) | Client sends a unique string per request | ✅ | ✅ |
| **Missing key → 400** (§3.1) | SHOULD reply 400 | ⚠️ 200 — `hashKey=true` falls back to body hash | ⚠️ 200 — falls back to SHA-256 of body |
| **Completed duplicate → replay** (§3.1) | SHOULD return original result | ✅ replays 200 | ✅ replays 200 |
| **In-flight duplicate → 409** (§3.1) | MUST respond with conflict | ❌ blocks until first completes, then replays 200 | ✅ 409 immediately |
| **Key reuse, different payload → 422** (§3.1) | SHOULD reply 422 | ❌ not validated | ❌ replays original, ignores payload change |
| **Payload fingerprint** (§2.1) | MAY use to detect mismatched payloads | ❌ not implemented | ❌ not implemented |
| **Key expiration** (§2) | MAY enforce, SHOULD publish policy | ✅ `PT1H` | ✅ 1 hour (DynamoDB TTL) |
| **HA / multi-instance** | — | ✅ 2 replicas behind Traefik | ✅ Lambda concurrency |

### Key takeaways

- **Neither implementation validates payload fingerprints**, so reusing a key
  with a different body silently replays the original response instead of
  returning `422`.
- **Missing `Idempotency-Key`**: the spec says `400`, but both implementations
  fall back to a body hash — effectively making the key optional.
- **In-flight duplicates**: the spec says the server MUST respond with a
  conflict error. `aws-powertools` does this (`409`); `arun0009lib` blocks
  instead, which is arguably better UX but diverges from the spec.

# Testing

## `test_idempotency.http`

In Intellij you can evaluate the http calls yourself.

## `test_idempotency.py`

You can test automatically with the Python script. By default it hits
`http://localhost:8080`; override with `IDEMPOTENCY_BASE_URL` to test a
deployed implementation.

```zsh
# Against a local implementation (e.g. arun0009lib via docker compose):
uv run test_idempotency.py

# Against the deployed aws-powertools implementation:
IDEMPOTENCY_BASE_URL=$(cat aws-powertools/url.txt) uv run test_idempotency.py
```
