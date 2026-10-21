# Product Catalog Service

A read-optimised backend for an e-commerce product catalog. It exposes a REST API to create, retrieve, update, partially update and delete products, and to list them by category and price range with cursor-based pagination.

The service is shaped by three facts about the workload:

| Fact | Consequence in the design |
|---|---|
| A few hundred thousand products | Small enough for one well-indexed PostgreSQL instance to serve from memory. The scaling work goes into the read path, not into sharding data. |
| Reads vastly outnumber writes | Every read is cacheable: HTTP ETags for clients and CDNs, Redis for the service, composite indexes for the database. Writes pay a little extra to keep caches honest. |
| List, filter and paginate | Composite indexes match each (filter, sort) shape the API exposes, and keyset pagination keeps every page an index range scan regardless of depth. |

## Design at a glance

```
            HTTP (JSON, RFC 9457 problem details, ETag / If-Match)
                              │
                 ┌────────────▼────────────┐
                 │   adapter.in.web        │  controllers, DTOs, cursor codec,
                 │                         │  merge-patch reader
                 └────────────┬────────────┘
                              │ use-case interfaces
                 ┌────────────▼────────────┐
                 │   application           │  command service / query service
                 │                         │  (transaction boundary)
                 └────────────┬────────────┘
                              │ ports (interfaces owned by the domain)
                 ┌────────────▼────────────┐
                 │   domain                │  Product, Money, Sku, SortOption,
                 │   (plain Java)          │  Cursor, invariants
                 └────────────┬────────────┘
                              │ implemented by
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
  adapter.out.cache     adapter.out.persistence   adapter.out.id
  Redis decorators      PostgreSQL via JPA        UUIDv7
```

Hexagonal architecture, with the dependency rule enforced by an ArchUnit test: the domain depends on nothing, the application layer depends on the domain, and adapters depend inward and never on each other. The `config` package is the composition root and the only place that knows which adapter backs which port.

Caching is a **decorator** around the repository ports. Use cases do not know a cache exists, and the JPA adapter does not know either. If Redis is unavailable, reads fall through to PostgreSQL; correctness never depends on the cache.

Key decisions, each with its alternatives and trade-offs, are recorded in [docs/DESIGN.md](docs/DESIGN.md) and as ADRs under [docs/adr](docs/adr):

1. PostgreSQL as the system of record, with a `jsonb` column for free-form product attributes.
2. Keyset (cursor) pagination instead of offset.
3. Cache-aside reads in Redis, invalidated by eviction for single products and by a generation counter for listings.
4. Command/query split in the application layer, so the read path can move to a replica or a search index without touching writes.
5. UUIDv7 identifiers: time-ordered for index locality, unguessable for clients.
6. Soft delete: `DELETE` archives a product so orders and analytics keep a valid reference.
7. Partial updates with JSON Merge Patch (RFC 7396), where an absent field and a `null` field mean different things.

## API

Base path: `/api/v1`. Interactive documentation is served at `/swagger-ui.html`, and the OpenAPI document at `/api-docs`.

| Method | Path | Success | Notes |
|---|---|---|---|
| `POST` | `/products` | `201` + `Location` + `ETag` | Duplicate SKU returns `409`. A body naming an unknown category returns `422`. |
| `GET` | `/products/{id}` | `200` with `ETag`, `Cache-Control` | Send `If-None-Match` to receive `304`. Archived products return `404`. |
| `PUT` | `/products/{id}` | `200` | Full replace. Optional `If-Match` returns `412` on a stale version. The SKU cannot change. |
| `PATCH` | `/products/{id}` | `200` | Partial update, `Content-Type: application/merge-patch+json`. Optional `If-Match`. Any other media type returns `415`. |
| `DELETE` | `/products/{id}` | `204` | Archives the product. Repeating it is safe. |
| `GET` | `/products` | `200` | Filters and pagination below. |
| `GET` | `/categories` | `200` | Reference data. |

### Listing and filtering

```
GET /api/v1/products?category=running-shoes&minPrice=50&maxPrice=150&sort=price_asc&limit=20
```

| Parameter | Values | Default |
|---|---|---|
| `category` | category slug; unknown slugs return `404` | none (all categories) |
| `minPrice`, `maxPrice` | decimal, `minPrice <= maxPrice` | none |
| `sort` | `price_asc`, `price_desc`, `newest` | `newest` |
| `limit` | 1 to 100 | 20 |
| `cursor` | opaque token from a previous response | none (first page) |

```json
{
  "items": [
    { "id": "0192f3a0-…", "sku": "NK-PEG-41-BLK", "name": "Pegasus 41", "category": "running-shoes",
      "price": "129.99", "currency": "USD", "status": "ACTIVE" }
  ],
  "limit": 20,
  "nextCursor": "djF8MTI5Ljk5fDAxOTJm…"
}
```

`nextCursor` is absent on the last page. The cursor encodes the last row's sort key and id plus a fingerprint of the filters and sort. A cursor reused with different filters is rejected with `400` rather than silently returning the wrong page; changing `limit` mid-scroll is allowed. There is deliberately no total count: counting on every page would cost more than the page itself, and catalog browsing does not need it.

Listings show `ACTIVE` products only. `INACTIVE` products stay readable and editable by id.

### Product resource

```json
{
  "sku": "NK-PEG-41-BLK",
  "name": "Pegasus 41",
  "description": "Responsive daily trainer.",
  "category": "running-shoes",
  "price": "129.99",
  "currency": "USD",
  "status": "ACTIVE",
  "attributes": { "colour": "black", "gender": "unisex" }
}
```

`currency` defaults to `USD` and `status` to `ACTIVE`. Responses add `id`, `version`, `createdAt` and `updatedAt`. Prices are strings on the wire so clients never round them through a float. The `version` is also the `ETag`.

### Partial update

```http
PATCH /api/v1/products/0192f3a0-…
Content-Type: application/merge-patch+json
If-Match: "3"

{ "price": "119.99", "description": null, "attributes": { "surface": "trail", "gender": null } }
```

Fields not mentioned keep their value. `null` removes an optional field, or one attribute key. The merged product is validated exactly like a create, so clearing a required field such as `name` returns `400`. The SKU is immutable and unknown fields are refused, so a typo cannot look like a successful update.

### Errors

All errors are RFC 9457 problem details with a stable `type`, so clients can branch on the type instead of parsing messages. Validation failures list each offending field.

```json
{
  "type": "urn:catalog:problem:validation",
  "title": "Request validation failed",
  "status": 400,
  "detail": "2 fields are invalid",
  "errors": [
    { "field": "price", "message": "must be greater than or equal to 0.00" },
    { "field": "sku", "message": "must match \"^[A-Z0-9-]{3,64}$\"" }
  ]
}
```

| Status | Type | When |
|---|---|---|
| `400` | `validation`, `invalid-cursor` | Bad field values, malformed JSON, or a cursor that does not fit the query |
| `404` | `not-found` | Unknown or archived product id, or an unknown category filter |
| `409` | `duplicate-sku`, `concurrent-update` | SKU already in use, or another write won a race |
| `412` | `version-mismatch` | `If-Match` does not name the current version |
| `415` | | `PATCH` without `application/merge-patch+json` |
| `422` | `unknown-category` | The body names a category that does not exist |
| `503` | `unavailable` | The database is unreachable; sent with `Retry-After` |

## Data model

Two tables: `categories` (seeded reference data) and `products`. The schema lives in [src/main/resources/db/migration](src/main/resources/db/migration) and is owned by Flyway; Hibernate only validates against it.

One composite index per (filter, sort) shape, each ending in `id` so the keyset comparison is fully index-served:

| Index | Serves |
|---|---|
| `(status, category_id, price, id)` | category + price range sorted by price, both directions |
| `(status, price, id)` | price range across all categories |
| `(status, category_id, created_at, id)` | newest in a category |
| `(status, created_at, id)` | newest across all categories |

The list query is an index range scan of `limit + 1` rows. Page depth does not change its cost.

## Caching and consistency

| Layer | What it holds | Invalidation |
|---|---|---|
| HTTP (`ETag`, `Cache-Control: public, max-age=30`) | Single products and listings, in browsers and CDNs | Revalidated by ETag; shared caches drop a URI after a successful write to it |
| Redis `products` | One entry per product | Evicted after the write commits |
| Redis `product-search` | First pages of listings | Every write advances a generation counter that is part of the key |
| In-process | All categories | Short TTL; they change only with a migration |

- **Single product reads** are read-your-own-writes: the entry is evicted after commit and before the response returns.
- **Listings** are usually fresh immediately and at most 60 seconds stale.
- **Writes never read from the cache.** Inside a read-write transaction the decorator goes straight to PostgreSQL.
- **Concurrent updates** are caught by optimistic locking: `412` when the client's `If-Match` is stale, `409` when another write lands between read and commit.
- **Cache outage** degrades to database reads, is counted in the `catalog.cache.failures` metric, and never fails a request.

## Scaling path

Stateless service instances in front of one PostgreSQL primary and one Redis have a lot of headroom at this catalog size. When that runs out, the next steps are, in order:

1. **Read replicas**, routed through the query service, which is already separate from the write path.
2. **Two-tier cache** (in-process Caffeine in front of Redis) and a **CDN** for the public GETs, which already carry `ETag` and `Cache-Control`.
3. **A search read model** (OpenSearch) fed through a transactional outbox, once full-text search or facets are needed. Only the search port gets a new adapter.
4. **Partitioning** `products` by category once the table reaches tens of millions of rows.

The reasoning, capacity estimates and failure modes are in [docs/DESIGN.md](docs/DESIGN.md).

## Project layout

```
src/main/java/com/example/catalog
├── domain          model, ports, query types, exceptions   (plain Java)
├── application     use cases, commands, command/query services
├── adapter
│   ├── in/web      controllers, DTOs, cursor codec, merge-patch reader, problem details
│   └── out         persistence (JPA), cache (Redis decorators), id (UUIDv7)
└── config          composition root, cache manager, clock, OpenAPI
src/main/resources/db/migration     Flyway schema, indexes, seed categories
src/test/java                       unit, architecture, web slice, PostgreSQL and full-stack tests
docs                                design write-up and architecture decision records
```

## Running locally

Requires Java 21 and Docker.

```bash
docker run -d --name catalog-db -p 5432:5432 \
  -e POSTGRES_DB=catalog -e POSTGRES_USER=catalog -e POSTGRES_PASSWORD=catalog postgres:17-alpine
docker run -d --name catalog-cache -p 6379:6379 redis:7-alpine

./mvnw spring-boot:run
```

Then open `http://localhost:8080/swagger-ui.html`. Health probes are at `/actuator/health/liveness` and `/actuator/health/readiness`, and metrics at `/actuator/prometheus`.

Tests:

```bash
./mvnw verify
```

The PostgreSQL and full-stack tests start their own PostgreSQL and Redis containers through Testcontainers.

## Stack

Java 21 · Spring Boot 4.1 · Spring Data JPA (Hibernate 7) · Flyway · PostgreSQL 17 · Redis 7 · springdoc-openapi · Micrometer · JUnit 5 · Testcontainers · ArchUnit
