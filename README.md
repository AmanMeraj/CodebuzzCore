# Codebuzz Core

A multitenant backend for managing sales leads from Meta (Facebook and Instagram) Lead Ads. Each company on the platform gets its own isolated data, users, teams and plan. Built with Spring Boot 4 and Java 17.

## What it does

- Receives Meta Lead Ads webhooks, verifies the HMAC SHA256 signature, stores each event and turns it into a lead
- Assigns leads to team members with round robin distribution
- Tracks the full lead lifecycle: status history, notes, follow up tasks, call outcomes, losses and sales
- Syncs Meta campaigns, ad sets, ads and insights for reporting
- Handles users, teams, roles and permissions per company
- Applies plan limits and feature entitlements, and records usage
- Sends in app and push notifications, and keeps an audit log
- Tracks workforce duty sessions and leave records

## Tech stack

| Area | Choice |
| --- | --- |
| Language and framework | Java 17, Spring Boot 4 |
| Persistence | Spring Data JPA, Hibernate, MySQL 8.4 |
| Migrations | Flyway |
| Security | Spring Security, stateless JWT, AES GCM for stored access tokens |
| API docs | springdoc OpenAPI (Swagger UI) |
| Build and run | Maven wrapper, Docker, Docker Compose, Caddy |

## Architecture notes

- Tenant isolation: requests carry a tenant context and data access is scoped to the current company.
- Auth: access tokens last 15 minutes and refresh tokens 30 days. Permissions are checked per endpoint. There are 17 permission codes and 5 system roles (Owner, Manager, Employee, Viewer, Platform).
- Webhooks: incoming events are stored first and processed afterwards, with idempotency keys so retries are safe.
- Background work: a database backed job queue with row locking, attempt history and a retry limit, plus an outbox for notifications. Scheduled workers poll and process jobs.
- Schema: 48 tables created through Flyway migrations in `src/main/resources/db/migration`.

## API overview

All routes are under `/api/v1` unless noted.

| Path | Purpose |
| --- | --- |
| `/auth` | Login, refresh, password reset |
| `/meta` | Connect Meta, pick pages, forms and ad accounts |
| `/webhooks/meta` | Meta webhook endpoint (outside `/api/v1`) |
| `/campaigns` | Cached campaigns and insights |
| `/notifications` | In app notifications and push devices |
| `/subscription` | Plan, features and usage |
| `/audit` | Tenant audit log |
| `/support` | Support tickets |

Leads, users, teams and workforce endpoints sit directly under `/api/v1`. Swagger UI is available at `/swagger-ui.html` in development and is disabled in the production Compose setup.

## Getting started

### Requirements

- Java 17
- Docker (for MySQL)

### Run locally

1. Copy the example environment file and fill in the values.

   ```
   cp env.example .env
   ```

   Generate the two keys with `openssl rand -base64 32` and use them for `JWT_SECRET` and `TOKEN_ENCRYPTION_KEY`. Meta values come from your Meta developer app.

2. Start MySQL (bound to localhost only).

   ```
   docker compose -f docker-compose.dev.yml up -d
   ```

3. Run the app. Export the variables from `.env` first, or set them in your IDE run configuration.

   ```
   ./mvnw spring-boot:run
   ```

The app starts on port 8080 and Flyway creates the schema on first start.

### Run tests

```
./mvnw test
```

## Deployment

The production stack runs MySQL, the app and Caddy, which provides automatic HTTPS. Only Caddy is exposed to the internet. MySQL and the app stay on the internal Docker network.

1. Set `DOMAIN` and the other values in `.env`.
2. Start the stack.

   ```
   docker compose up -d --build
   ```

With `REQUIRE_SECRETS=true` the app refuses to start if `JWT_SECRET` or `TOKEN_ENCRYPTION_KEY` is missing.

## Configuration

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Database connection |
| `MYSQL_ROOT_PASSWORD` | MySQL root password (Compose) |
| `JWT_SECRET` | Signs access and refresh tokens |
| `TOKEN_ENCRYPTION_KEY` | Encrypts stored access tokens |
| `META_APP_ID`, `META_APP_SECRET` | Meta app credentials |
| `META_WEBHOOK_VERIFY_TOKEN` | Webhook verification token |
| `META_CONFIG_ID`, `META_GRAPH_VERSION` | Meta login configuration and API version |
| `META_OAUTH_REDIRECT_URI` | OAuth callback URL |
| `FCM_SERVER_KEY` | Firebase push notifications |
| `REQUIRE_SECRETS` | Fail fast if secrets are missing |
| `DEV_EXPOSE_RESET_TOKEN` | Development only. Keep false in production |
| `DOMAIN` | Public domain used by Caddy |

## Security

- Never commit `.env` or IDE files. Both are in `.gitignore`.
- Use a different secret for every environment and rotate any value that has been shared.
- Keep `DEV_EXPOSE_RESET_TOKEN=false` outside local development.

## Project layout

```
src/main/java/com/codebuzzers/codebuzzcore
  identity      users, teams, roles, tenants, authentication
  security      JWT filter, security config, login rate limiter
  meta          Meta connection, pages, forms, Graph API client
  webhook       webhook controller and inbox processing
  lead          lead ingestion, assignment, activity, sales and losses
  campaign      campaign, ad set and ad sync
  billing       plans, features, limits, usage
  job           background job queue and workers
  notification  notifications, push devices, outbox
  workforce     duty sessions and leave
  audit         audit log
  support       support tickets
  common        shared utilities and error handling
```
