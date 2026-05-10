# Evaluate arun0009lib

Reference implementation: a Spring Boot service that uses the
`io.github.arun0009:idempotent-rds` library to make `POST /orders/idempotent`
idempotent across replicas.

## Run

`compose.yaml` defines four services: `postgres`, two app replicas (`app1`,
`app2`) sharing one image, and `traefik` as the load balancer. The `app` profile
is active by default (via `.env`).

### Default — build and run everything in Docker

```zsh
docker compose up
```

### Live rebuild — `docker compose watch`

`compose.yaml` declares a `develop.watch` block on both app services that
rebuilds the image and recreates the containers whenever `src/`, the Gradle
build files, or the `Dockerfile` change. Start the stack with `watch` instead
of `up`:

```zsh
docker compose watch
```

### Postgres only — run the app from your IDE

Start just the database:

```zsh
docker compose up postgres
```

Then run the app from your IDE or:

```zsh
gradle bootRun
```
