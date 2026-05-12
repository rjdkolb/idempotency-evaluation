# aws-powertools

Idempotency implementation on AWS API Gateway HTTP API + Lambda (Java 25),
using [AWS Lambda Powertools for Java](https://docs.powertools.aws.dev/lambda/java/)
`@Idempotent` annotation backed by DynamoDB. Provisioned via AWS SAM.

Two Lambda functions, each with a single responsibility:

- **`OrderHandler`** — `GET /orders` and `POST /orders` (plain, non-idempotent).
- **`IdempotentOrderHandler`** — `POST /orders/idempotent`, using Powertools'
  `@Idempotent` annotation on `OrdersService.createOrderIdempotent()` (AspectJ
  post-compile weaving).

## Layout

- `template.yaml` — SAM template: HTTP API + two Lambda functions + `OrdersTable` + `IdempotencyTable`.
- `samconfig.toml` — non-interactive `sam deploy` config (stack name, region, capabilities).
- `src/main/java/eval/lambda/OrderHandler.java` — `GET /orders`, `POST /orders`.
- `src/main/java/eval/lambda/IdempotentOrderHandler.java` — `POST /orders/idempotent`.
- `src/main/java/eval/lambda/OrdersService.java` — shared service; `@Idempotent` on `createOrderIdempotent()`.
- `build.gradle.kts` — builds `build/dist/lambda.zip` with AspectJ post-compile weaving.
- `deploy.sh` — `./gradlew lambdaZip && sam deploy`, then writes the HTTP API base URL to `url.txt`.
- `destroy.sh` — `sam delete` the stack and remove `url.txt`.

## Prerequisites

- JDK 25 (managed by `mise` from the repo-root `mise.toml`).
- AWS SAM CLI (`brew install aws-sam-cli`).
- AWS credentials with permission to create HTTP API, Lambda, DynamoDB, and IAM resources.

## Deploy

```zsh
./deploy.sh
```

This builds `build/dist/lambda.zip`, runs `sam deploy` against the stack
`aws-powertools-idempotency` in `eu-central-1`, and writes the HTTP API base
URL to `url.txt`.

## Run the test harness against this implementation

```zsh
IDEMPOTENCY_BASE_URL=$(cat url.txt) uv run ../test_idempotency.py
```

## Tear down

```zsh
./destroy.sh
```

## How it responds

![aws-powertools demo timeline](/aws-powertools/aws-powertools.png)

## Notes on contract parity with `arun0009lib`

- **Response shape** matches: `{"customerOrderReference": "...", "supplierOrderReference": "SUP-<uuid>"}`.
- **`POST /orders`** rejects duplicate `customerOrderReference` with `409` (DynamoDB conditional write).
- **`POST /orders/idempotent`** replays the original response for in-flight and completed duplicates.
- **Missing `Idempotency-Key`**: the live reference at `localhost:8080` returns `200` (the reference library derives the key from a body hash when configured with `hashKey = true`). This implementation matches that behavior — when the header is missing or blank, the SHA-256 of the request body is used as the idempotency key. The repo-root README's claim of `400` on missing key reflects the *intended* contract; both implementations currently diverge from it.

### In-flight duplicates: 409 vs blocking

`arun0009lib`'s `idempotent-rds` library **blocks** duplicate in-flight
requests and replays the original response when the first call completes.

Powertools for Java takes a different approach: it **rejects** in-flight
duplicates immediately with `IdempotencyAlreadyInProgressException`, which
this implementation surfaces as `409 Conflict`. Once the first call completes,
subsequent duplicates replay the stored response as `200`.

This is a deliberate design difference — Powertools treats in-flight
duplicates as a client concern rather than blocking server-side.

## HA story

Unlike `arun0009lib` which runs two Spring Boot replicas behind Traefik, this
implementation relies on Lambda's per-invocation concurrency model. The
black-box test harness's "5 concurrent in-flight duplicates" scenario lands
on multiple Lambda execution environments and exercises the same correctness
guarantee.
