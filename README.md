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

All endpoints require HTTP Basic Auth. The IETF draft is silent on tenant
isolation, but a multi-tenant API must not replay one tenant's response for
another, so the **username is the tenant marker** — the password is not
validated, any non-blank username is accepted, and the username is prefixed
onto the stored idempotency key (`<username>:<Idempotency-Key>`).
Unauthenticated requests get `401` before any idempotency lookup runs, so a
cached response can never leak to an unauthenticated caller.

The `arun0009lib` implementation is the reference implementation and is available at http://localhost:8080/v3/api-docs

### `POST /orders` — plain endpoint

```zsh
curl -u alice:x -X POST 'http://localhost:8080/orders?delayMillis=100' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 0c5b8d7e-4f1c-4f6a-9f9b-1f4e1c5d6b7a' \
  -d '{"customerOrderReference":"customer-123"}'
```

A second call with the same `customerOrderReference` returns `409 Conflict` —
the plain endpoint is not idempotent and rejects duplicates.

### `POST /orders/idempotent` — idempotent endpoint

```zsh
curl -u alice:x -X POST 'http://localhost:8080/orders/idempotent?delayMillis=5000' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 0c5b8d7e-4f1c-4f6a-9f9b-1f4e1c5d6b7a' \
  -d '{"customerOrderReference":"customer-456"}'
```

Duplicate calls with the same `Idempotency-Key` replay the original response,
both while the first call is in flight and after it has completed. A missing
or blank `Idempotency-Key` returns `400`.

### `GET /orders` — list

```zsh
curl -u alice:x http://localhost:8080/orders
```

Returns only the orders created by the authenticated tenant.
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
[draft-ietf-httpapi-idempotency-key-header-07](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html)
(the latest published revision, 2025-10-15).

| Requirement | RFC 2119 | § | `arun0009lib` | `aws-powertools` |
|---|---|---|---|---|
| `Idempotency-Key` is a Structured Header String | MUST | 2.1 | ✅ | ✅ |
| Key not reused with different payload | MUST NOT | 2.2 | ❌ not validated | ❌ replays original, ignores payload |
| Use a UUID or similar random identifier | RECOMMENDED | 2.2 | ✅ (client-side) | ✅ (client-side) |
| Key expiration policy published | SHOULD | 2.3 | ✅ `PT1H` | ✅ 1 hour (DynamoDB TTL) |
| Idempotency fingerprint | MAY | 2.4 | ❌ not implemented | ❌ not implemented |
| Resource publishes idempotency specification | MUST | 2.5.2 | ⚠️ Swagger only | ⚠️ undocumented |
| Server enforces idempotency | SHOULD | 2.5.2 | ✅ | ✅ |
| First-time request processed normally | SHOULD | 2.6 | ✅ | ✅ |
| Completed duplicate replays original | SHOULD | 2.6 | ✅ | ✅ |
| Concurrent duplicate → resource conflict error | SHOULD | 2.6 | ❌ blocks until first completes, then replays 200 | ✅ 409 immediately |
| Missing `Idempotency-Key` → `400` | SHOULD | 2.7 | ✅ 400 | ⚠️ 200 — falls back to SHA-256 of body |
| Key reuse, different payload → `422` | SHOULD | 2.7 | ❌ not validated | ❌ replays original, ignores payload change |
| HA / multi-instance | — | — | ✅ 2 replicas behind Traefik | ✅ Lambda concurrency |

### Key takeaways

- **Neither implementation validates payload fingerprints** (§2.4 / §2.7), so
  reusing a key with a different body silently replays the original response
  instead of returning `422`.
- **Missing `Idempotency-Key`**: §2.7 says `400`. `arun0009lib` complies;
  `aws-powertools` falls back to a SHA-256 of the body, effectively making the
  key optional.
- **Concurrent duplicates**: §2.6 says the server SHOULD respond with a
  resource-conflict error. `aws-powertools` does this (`409`); `arun0009lib`
  blocks instead, which is arguably better UX but diverges from the spec.

# Comparison against extra (non-IETF) requirements

The IETF draft is silent on authentication and multi-tenant isolation, but
both are mandatory for a real financial API. These rows cover the additional
constraints enforced by both implementations.

| Requirement | RFC 2119 | `arun0009lib` | `aws-powertools` |
|---|---|---|---|
| Endpoints require authentication | MUST | ✅ HTTP Basic via Spring Security | ✅ HTTP Basic checked in the Lambda handler |
| Missing/invalid credentials → `401` | MUST | ✅ `401 WWW-Authenticate: Basic` | ✅ `401 WWW-Authenticate: Basic` |
| 401 returned before any idempotency lookup | MUST | ✅ Security filter runs before the `@Idempotent` aspect | ✅ Handler short-circuits before `OrdersService` is called |
| Username acts as tenant marker | MUST | ✅ `Authentication#getName()` | ✅ Parsed by `BasicAuth#tenantOrNull` |
| Idempotency scoped per tenant | MUST | ✅ `TenantKeyFilter` rewrites header to `<user>:<key>` | ✅ Handler builds `<user>:<key>` before calling `@IdempotencyKey` |
| Same key + body across tenants → independent records | MUST | ✅ each tenant gets a distinct response | ✅ each tenant gets a distinct response |
| Order data scoped per tenant | SHOULD | ✅ composite unique index on `(tenant, customer_order_reference)` | ✅ composite DynamoDB key `(tenant, customerOrderReference)` |
| `GET /orders` filtered by tenant | SHOULD | ✅ `findAllByTenantOrderByIdAsc` | ✅ DynamoDB `Query` keyed by tenant |
| API documentation declares the auth scheme | SHOULD | ✅ `basicAuth` security scheme in OpenAPI | ❌ no spec published |

### Key takeaways

- **Both implementations refuse to leak cached responses.** Because the 401
  is returned before the idempotency layer runs, an unauthenticated caller
  can never receive a previously-cached response — even if they guess a
  valid `Idempotency-Key`.
- **Tenant isolation is achieved purely by key composition.** Neither the
  arun0009 library nor AWS Lambda Powertools has built-in tenant scoping;
  prefixing the stored key with the username is enough to make collisions
  between tenants impossible.
- **Password is intentionally not validated.** The username is the tenant
  marker; the password slot exists only so that standard Basic-Auth clients
  (curl, browsers, httpx, IntelliJ HTTP Client) keep working. Real
  deployments would plug in an identity provider here.

# Testing

## `test_idempotency.http`

In Intellij you can evaluate the http calls yourself.

## `test_idempotency.py`

A pytest suite with one test per draft-07 normative requirement. The
RFC 2119 keyword and section number appear next to each test in the output,
and known divergences are marked `xfail` so the suite stays exit-0 while
still surfacing every mismatch.

Pass `--impl` to label the run and pick up the right xfail set. By default
the suite hits `http://localhost:8080`; override with `IDEMPOTENCY_BASE_URL`
to test a deployed implementation.

```zsh
# Against a local implementation (arun0009lib via docker compose):
uv run test_idempotency.py --impl=arun0009lib -v

# Against the deployed aws-powertools implementation:
IDEMPOTENCY_BASE_URL=$(cat aws-powertools/url.txt) \
  uv run test_idempotency.py --impl=aws-powertools -v
```
