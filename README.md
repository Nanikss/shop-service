# Shop Service

[![CI](https://github.com/Nanikss/shop-service/actions/workflows/ci.yml/badge.svg)](https://github.com/Nanikss/shop-service/actions/workflows/ci.yml)

A Spring Boot REST API for the order side of an e-commerce backend: **sign up → browse the catalog → fill a cart → check out → get paid (or not) through a payment webhook**. Most of my work has been on insurance platforms, where money has to add up exactly and outside systems retry and send the same message twice. This project applies the same rules to a shop: correct totals, no overselling, and callbacks that are safe to replay.

**Stack:** Java 17 · Spring Boot 3 (Web, Data JPA, Validation) · H2 / Postgres · JUnit 5 · MockMvc · AssertJ. The only extra dependency is `spring-security-crypto`, used for BCrypt.

## What it does

1. **Auth.** Signup and login with BCrypt-hashed passwords. Login returns an HS256 JWT that I sign and verify with `javax.crypto.Mac`. A small `HandlerInterceptor` checks the bearer token, instead of the full Spring Security filter chain. Endpoints need a token unless they're marked `@PublicEndpoint`, and `@AdminOnly` checks the role claim. Token expiry reads an injected `Clock`.
2. **Catalog.** Categories and products, with prices in integer cents and a stock count. Anyone can list products (paginated, filterable by category). Only admins can create categories or products.
3. **Cart.** Add, update and remove items for each user. A quantity above the current stock is rejected with a 409. Adding to a cart doesn't reserve stock.
4. **Checkout.** Turns the cart into an order in `PENDING_PAYMENT`. Each line keeps a copy of the product's name and price, and the total is in cents. Stock is reserved in the same transaction, guarded by optimistic locking, so two checkouts racing for the last unit can't both succeed.
5. **Payment webhook.** `POST /api/webhooks/payments` stands in for a Stripe-style provider. It checks an HMAC signature, rejects stale events and ignores redeliveries. `payment.succeeded` marks the order `PAID` if the amount matches. `payment.failed` marks it `CANCELLED` and puts the stock back.
6. **Consistent errors.** Validation, auth, not-found and conflict errors all come back as RFC 7807 `ProblemDetail` JSON.

## Design decisions

- **Money as integer cents.** Every amount is a `long` number of cents. With floating point, 0.1 + 0.2 isn't exactly 0.3, and those rounding errors add up across line items. Line totals use `Math.multiplyExact` and `Math.addExact`, so an overflow throws an error instead of silently wrapping around.
- **The price is copied at checkout.** An order line stores the unit price and name at the moment of checkout, not a reference to the product. If the catalog price changes later, what the customer was charged stays the same.
- **Optimistic locking on stock.** `Product` has a `@Version` column. Checkout decrements stock and flushes, and the update runs `WHERE version = ?`. If another checkout changed that product in the meantime, no row is updated, the whole transaction rolls back (order, stock and cart) and the client gets a 409 it can retry. I chose this over `SELECT ... FOR UPDATE` because most checkouts don't compete for the same product, so they never wait on a lock. They only fail when there really is a conflict. `Order` is versioned too, so a "succeeded" and a "failed" event for the same order can't both win.
- **Carts don't reserve stock.** Stock is checked when you add to the cart (so the customer finds out early) and checked again at checkout, which is the step that counts. If carts held stock, a few abandoned carts could block real buyers.
- **Webhook: signature, then timestamp, then idempotency.**
  - *Signature:* `X-Signature` is the hex HMAC-SHA256 of the **raw request bytes**. I check it before parsing anything, and compare with `MessageDigest.isEqual` so the comparison takes the same time however many characters match.
  - *Timestamp:* `created` is inside the signed body, so it can't be changed without breaking the signature. Events more than 5 minutes from the injected clock are rejected, so a captured request can't be replayed later.
  - *Idempotency:* providers deliver "at least once", so the same event will arrive more than once. The event id is the primary key of `processed_event`, and it's inserted in the same transaction as the order change. A redelivery returns `200 DUPLICATE` and does nothing. If two deliveries race, the database's unique key stops the second one.
- **Unknown event types get a 200.** Real providers send many event types. If I answered the ones I don't use with an error, the provider would keep retrying them.

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/signup` | none | Create a customer account |
| POST | `/api/auth/login` | none | Get a bearer token (1 hour) |
| GET | `/api/categories` | none | List categories |
| POST | `/api/categories` | admin | Create a category |
| GET | `/api/products?categoryId=&page=&size=` | none | Paginated product list (size up to 100) |
| GET | `/api/products/{id}` | none | One product with its current stock |
| POST | `/api/products` | admin | Create a product |
| GET | `/api/cart` | user | Cart lines and total |
| POST | `/api/cart/items` | user | Add a quantity of a product |
| PUT | `/api/cart/items/{productId}` | user | Set the quantity |
| DELETE | `/api/cart/items/{productId}` | user | Remove a line |
| POST | `/api/orders/checkout` | user | Create an order from the cart and reserve stock |
| GET | `/api/orders` | user | My orders |
| GET | `/api/orders/{id}` | user | One of my orders (404 for anyone else's) |
| POST | `/api/webhooks/payments` | HMAC | Payment provider callback |

## Run

```bash
mvn spring-boot:run   # http://localhost:8080 (in-memory H2)
```

On startup the app creates an admin account from `shop.admin.*` (`admin@shop.local` / `admin-local-only` locally). Here's the whole flow with curl and `jq`:

```bash
H='Content-Type: application/json'
ADMIN=$(curl -s -X POST localhost:8080/api/auth/login -H "$H" \
  -d '{"email":"admin@shop.local","password":"admin-local-only"}' | jq -r .token)

curl -X POST localhost:8080/api/categories -H "$H" -H "Authorization: Bearer $ADMIN" -d '{"name":"Books"}'
curl -X POST localhost:8080/api/products -H "$H" -H "Authorization: Bearer $ADMIN" \
  -d '{"name":"Clean Architecture","categoryId":1,"priceCents":3499,"stock":10}'

curl -X POST localhost:8080/api/auth/signup -H "$H" -d '{"email":"kumar@example.com","password":"correct-horse"}'
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H "$H" \
  -d '{"email":"kumar@example.com","password":"correct-horse"}' | jq -r .token)

curl -X POST localhost:8080/api/cart/items -H "$H" -H "Authorization: Bearer $TOKEN" -d '{"productId":1,"quantity":2}'
curl -X POST localhost:8080/api/orders/checkout -H "Authorization: Bearer $TOKEN"   # order 1, totalCents 6998
```

To simulate the provider, sign the exact body with the webhook secret:

```bash
BODY="{\"id\":\"evt_1001\",\"type\":\"payment.succeeded\",\"created\":$(date +%s),\"data\":{\"orderId\":1,\"amountCents\":6998}}"
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac 'local-dev-webhook-secret' | sed 's/^.* //')

curl -X POST localhost:8080/api/webhooks/payments -H "$H" -H "X-Signature: $SIG" -d "$BODY"   # {"outcome":"PROCESSED"}
curl -X POST localhost:8080/api/webhooks/payments -H "$H" -H "X-Signature: $SIG" -d "$BODY"   # {"outcome":"DUPLICATE"}
curl localhost:8080/api/orders/1 -H "Authorization: Bearer $TOKEN"                            # "status":"PAID"
```

To use Postgres, run with `SPRING_PROFILES_ACTIVE=postgres` and set `DB_URL`, `DB_USER` and `DB_PASSWORD`. Outside local development, also set `SHOP_JWT_SECRET` (at least 32 bytes), `SHOP_WEBHOOK_SECRET` and `SHOP_ADMIN_PASSWORD`.

## Test

```bash
mvn test   # 27 tests: JWT unit tests + full API tests via MockMvc
```

The tests cover: wrong password and unknown email getting the same 401, token expiry using a controllable clock, the admin role check, cart quantities above stock, checkout totals and price copies, a second buyer blocked from overselling, the optimistic-lock conflict, and the webhook rules (bad or missing signature, stale timestamp, duplicate delivery releasing stock only once, a paid order that can't be cancelled, the wrong amount). In the conflict test I force the interleaving instead of relying on thread timing: Alice's transaction reads the stock, Bob checks out and commits, then Alice's checkout fails with `ObjectOptimisticLockingFailureException` and nothing is oversold.

## Layout

```
domain/      User, Category, Product (@Version stock), CartItem, Order + OrderLine (price copy), ProcessedEvent
repository/  Spring Data JPA repositories (entity graphs, so the API never trips over lazy loading)
service/     AuthService, CatalogService, CartService, OrderService (checkout), PaymentWebhookService
security/    JwtCodec (HS256), Hmac, WebhookSignature, AuthInterceptor, @PublicEndpoint / @AdminOnly
web/         REST controllers, request/response records, ProblemDetail error handling
error/       NotFound / Conflict / Unauthorized / Forbidden exceptions mapped to 404 / 409 / 401 / 403
```

## Interview notes

- **Outbox pattern.** Once an order is paid, a real system has to tell fulfilment and email. Publishing to a broker inside the same transaction is a dual write: the database commit and the message send can fail independently. I'd write an `outbox` row in the payment transaction and have a relay publish it.
- **A real payment provider.** I'd use Stripe's SDK for signature checks (its `t=...,v1=...` header signs the timestamp together with the body) and create a PaymentIntent at checkout. Orders still `PENDING_PAYMENT` after N minutes should expire and release their stock, which needs a scheduled job.
- **Redis for idempotency keys.** The `processed_event` table grows forever. The 5-minute timestamp window means ids only need to be kept for slightly longer than that, so a Redis `SET NX` with a TTL is enough. I'd also add an `Idempotency-Key` header on checkout, so a client retrying after a timeout doesn't create two orders.
- **Hot products.** Optimistic locking is right while conflicts are rare. For a flash sale on one product, most checkouts would fail with a 409. There I'd retry with backoff on the server, or switch to a single atomic `UPDATE product SET stock = stock - ? WHERE id = ? AND stock >= ?`.
- **Production hardening.** Flyway migrations instead of `ddl-auto=update`, refresh tokens and key rotation (a `kid` header) for the JWTs, and rate limiting on login.

Built with AI coding tools; I designed the domain rules and failure handling, and reviewed and tested the code.
