# Product Catalog Service: Design

This document explains how the service is built and why. The README covers the API; this covers the reasoning behind it, the trade-offs that were accepted, and how the design grows.

## 1. Workload and the forces it creates

| Fact from the brief | What it implies |
|---|---|
| A few hundred thousand products | Roughly 300k rows of about 0.5 KB each: around 150 MB of table and 60 MB of indexes. The whole working set fits in memory on a small database instance. Sharding would add cost and no benefit. |
| Reads vastly outnumber writes | Optimise reads aggressively and let writes pay for it: extra index maintenance, cache invalidation, and bounded staleness on listings. |
| Clients list and filter by category and price range, with pagination | Index design and the pagination strategy are the core of the design. Deep pages must cost the same as the first page. |

Every decision below traces back to one of these three rows.

## 2. Architecture

### Layers and the dependency rule

```
adapter.in.web ──► application ──► domain ◄── adapter.out.{persistence, cache, id}
                                     ▲
                        config ──────┘ (wires everything)
```

- **domain** is plain Java. It holds the `Product` aggregate, value objects (`Money`, `Sku`, `ProductId`), the read-side types (`ProductSearchCriteria`, `SortOption`, `Cursor`, `PageResult`), domain exceptions, and the outbound ports (`ProductRepository`, `ProductSearchRepository`, `CategoryRepository`, `IdGenerator`). It has no Spring, JPA or Jackson imports, so its rules can be read and tested in isolation.
- **application** holds one interface per use case and two implementations: `ProductCommandService` for writes and `ProductQueryService` for reads. It owns transaction boundaries and nothing else; business rules stay in the domain.
- **adapters** translate between the outside world and the ports. The web adapter speaks HTTP and JSON. The persistence adapter speaks JPA and SQL. The cache adapter decorates the persistence ports. The id adapter generates UUIDv7 values.
- **config** is the composition root. It is the only code that knows which adapter implements which port and in what order decorators stack.

The rule is enforced by `ArchitectureTest`, which fails the build if, for example, a controller depends on a service class instead of a use-case interface, or the domain imports a framework.

### Command/query split

Writes and reads go through different services and different ports. The write side works with the full `Product` aggregate. The read side returns `ProductSummary` projections that omit the description and attributes, which keeps list pages small on the wire, in the cache and in the database read. Because the two paths are already separate, the read path can later be pointed at a replica or a search index without touching any write code.

The query service is deliberately not transactional. Each read is a single statement, and an open transaction would hold a pooled connection even when the answer comes from the cache. The persistence adapter opens one only on a cache miss.

### Request flows

```
Read one:   Controller → GetProductUseCase → CachingProductRepository ─hit→ return
                                                        └─miss→ JpaProductRepositoryAdapter → PostgreSQL → cache it

List:       Controller → SearchProductsUseCase → resolve category slug (in-process)
                       → CachingProductSearchRepository ─hit (first pages only)→ return
                                     └─miss→ JpaProductSearchAdapter → one keyset query

Write:      Controller → Command service (transaction)
                       → load from PostgreSQL (the cache is bypassed in write transactions)
                       → check If-Match → domain builds the new state and validates it
                       → insert / update with version check → commit
                       → after commit: evict product entry, advance listing generation
```

## 3. Datastore: PostgreSQL

The access patterns are structured range queries over a fixed schema, which is what a B-tree serves best. A composite index on `(status, category_id, price, id)` answers "category plus price range, ordered by price" with one index range scan. PostgreSQL also gives transactional writes, a database-enforced unique SKU, and versioned schema changes through Flyway.

Free-form attributes such as colour or material go into a `jsonb` column. That gives document-style flexibility for the variable part while keeping the relational guarantees for the fields the service filters and sorts on.

| Alternative | Why not |
|---|---|
| MongoDB | Flexible schema is its main draw, but the product shape is stable and `jsonb` covers the variable part. It would not make the hot query faster and would add a second consistency model. |
| DynamoDB | A global secondary index on category and price handles the main filter. Combining further filters, alternate sort orders and pagination tokens then moves query planning into application code, and popular categories risk hot partitions. |
| OpenSearch as primary store | The right tool for full-text search and facets, which the brief does not ask for. Kept as a future read model fed from PostgreSQL (section 10). |

### Indexes

One composite index per (filter, sort) combination the API exposes:

| Index | Serves |
|---|---|
| `(status, category_id, price, id)` | category + price range, sorted by price in either direction |
| `(status, price, id)` | price range across all categories |
| `(status, category_id, created_at DESC, id DESC)` | newest in a category |
| `(status, created_at DESC, id DESC)` | newest across all categories |

Every index ends in `id`, so the keyset comparison on `(sort key, id)` is fully index-served. `status` leads every index because every listing filters on it with an equality. A partial index (`WHERE status = 'ACTIVE'`) would be smaller, but PostgreSQL can only use it when the literal is visible to the planner, and JPA sends `status` as a bind parameter, so a generic plan would skip the index. Descending price reuses the ascending index scanned backwards.

One combination has no perfect index: filtering on price while sorting by newest. The `created_at` index is walked and price is checked per row. That is cheap for wide price ranges and slower for very narrow ones. If analytics showed that combination to be hot, it would get its own index or move to the search read model.

The expected plan was checked on a synthetic 300k-row table, with the query prepared as a generic plan the way JPA sends it:

```
Limit (actual rows=21)
  ->  Nested Loop
        ->  Index Scan using ix_products_status_cat_price_id on products p (actual rows=21)
              Index Cond: (status = $1) AND (category_id = $2) AND (price >= $3) AND (price <= $4) AND (price >= $5)
              Filter: ((price > $5) OR (id > $6))
              Rows Removed by Filter: 2
        ->  Index Scan using categories_pkey on categories c
```

This is a page deep inside the category. The cursor's lower bound becomes an index condition, and only two tie rows at the page boundary are read and discarded.

## 4. Pagination: keyset with opaque cursors

`OFFSET n` makes the database read and throw away `n` rows, so page 10,000 costs ten thousand times page one. Keyset pagination instead continues from the last row the client saw:

```sql
WHERE status = ? AND category_id = ? AND price BETWEEN ? AND ?
  AND price >= :lastPrice AND (price > :lastPrice OR id > :lastId)
ORDER BY price, id
FETCH FIRST :limit + 1 ROWS ONLY
```

- **Total order.** Every sort is completed by `id`, so the position is unique even when hundreds of products share a price.
- **Index-friendly predicate.** JPA Criteria has no row-value comparison, so `(price, id) > (?, ?)` is expanded. The leading `price >= ?` is logically redundant but gives the planner a range start; the `OR` only filters ties at the boundary.
- **No COUNT.** The query asks for `limit + 1` rows. The extra row proves a next page exists and is then dropped.
- **Opaque cursor.** The token is Base64URL of `v1|sortValue|lastId|fingerprint`. Clients pass it back unchanged, so the format can change; the version prefix lets a future format reject old tokens cleanly.
- **Fingerprint.** A hash of the filters and sort. A cursor reused with different filters is rejected with `400` instead of silently skipping or repeating rows. `limit` is excluded on purpose, so clients can change page size mid-scroll.

Trade-offs accepted: there is no "jump to page N" and no total count. That fits catalog browsing, which is infinite scroll or "load more". If a UI ever needs page numbers, bounded offset for the first few pages is the answer; if it needs a count, a separate, cached, approximate count endpoint.

A cursor is not signed. A forged cursor can only make its own client see an odd page, never someone else's data, and malformed values are rejected with `400`. Signing with an HMAC is a small addition if cursors ever carry anything sensitive.

## 5. Caching

### Three layers, each cheap

1. **HTTP.** Responses carry `ETag` (the product version) and `Cache-Control: public, max-age=30`. Browsers and a CDN absorb repeat reads; a matching `If-None-Match` gets `304` with no body. Caches drop their copy of a URI after a successful write to it, so a writer does not read its own stale data.
2. **Redis**, shared by all instances, holding single products and first pages of listings.
3. **In-process** snapshot of categories. They are a handful of rows that change only with a migration, and nearly every request looks one up, so a local copy beats a network round trip.

### Where the cache lives

Caching is a decorator around the repository ports (`CachingProductRepository`, `CachingProductSearchRepository`, `CachingCategoryRepository`), applied in the composition root. The use cases and the JPA adapters do not know a cache exists. Removing or replacing a layer is a one-line change in `CatalogWiring`.

The decorators use Spring's `Cache` abstraction programmatically rather than `@Cacheable` annotations. That keeps generation-based keys readable as plain code instead of SpEL, makes the fail-open behaviour explicit, and makes the decorators unit-testable with an in-memory cache.

### Invalidation

- **Single product:** the entry is evicted when the write commits. After commit, not before: evicting earlier would let a concurrent reader put the old row back before the new one is visible.
- **Listings:** every cached page key starts with a generation number held in Redis. Each write advances it, so all cached pages become unreachable at once with no key scan, and the old entries expire on their own. Writes are rare, so invalidating everything is cheaper and safer than working out which pages a change affects.
- **Only first pages are cached.** They carry most of the traffic (category landing pages). Deeper pages are already a cheap index range scan and rarely repeat, so caching them would mostly churn memory.
- **TTLs are a backstop**, not the mechanism: 10 minutes for products and 60 seconds for listings bound staleness if an invalidation is ever missed.

### Consistency model

| Read | Guarantee |
|---|---|
| Single product | Read-your-own-writes: eviction happens after commit and before the response returns. |
| Listing | Usually fresh immediately, because writes advance the generation. At most 60 seconds stale. |
| Pagination over changing data | If a price changes mid-scroll, that product can move between pages. Every pagination scheme has this; offset additionally duplicates rows on inserts. Accepted and documented. |
| Writes | Never served from cache. Inside a read-write transaction the decorator reads PostgreSQL directly, so decisions are made on the source of truth. |

One race remains, and it is the standard cache-aside one. A reader that fetched the old row just before a commit can write it into the cache just after the eviction. That entry lives until its TTL. It needs a read and a write on the same product within milliseconds, and the TTL bounds it. If it ever mattered, the fix is to store the version with the entry and refuse to cache a version lower than the last one evicted.

### Fail-open

Every cache operation goes through `FailOpenCache`. A failed read becomes a miss and a failed write is skipped. Failures are logged and counted in `catalog.cache.failures`, so a degraded cache is visible on dashboards instead of silent. If the generation counter cannot be read, listings bypass the cache entirely. Readiness checks include PostgreSQL but not Redis, so a Redis outage does not pull every instance out of the load balancer at once.

## 6. Writes and concurrency

- **Optimistic locking.** Each row has a `version`, mapped with JPA `@Version`. Every `UPDATE` includes `WHERE version = ?`. Writes are rare and short, so there is no reason to hold row locks.
- **`If-Match`.** `PUT` and `PATCH` accept the version the client last saw. It is checked against freshly read state before anything is written, and a mismatch returns `412` with the current version. A writer that slips in between the read and the commit is caught by the version predicate and returns `409`.
- **Insert and update are separate port methods.** The service always knows which one it means, so the store never guesses from the id, and an insert costs one statement.
- **Duplicate SKU** is enforced by a named unique constraint. The adapter maps that constraint to `DuplicateSkuException`, so the check is race-free and needs no pre-query.
- **Timestamps** are truncated to microseconds before they are stored. PostgreSQL keeps microseconds, so the value returned from a write is identical to what every later read returns.

### Partial updates: JSON Merge Patch (RFC 7396)

`PATCH` takes `application/merge-patch+json`. An absent field is kept, `null` removes it, and a value replaces it. A plain nullable field cannot tell "absent" from "null", so each field of the command is a `PatchField<T>`: a sealed type with three cases, `Unchanged`, `Clear` and `Set`. Attributes merge key by key, as the RFC specifies for nested objects, so a client can add or remove one attribute without resending the map.

The use case loads the product, applies each field, and builds a new `ProductDetails` through the same constructor a create uses. Clearing a required field therefore fails validation, and a patch can never leave a product invalid. The read-modify-write runs under the same optimistic lock as a full update.

JSON Patch (RFC 6902) was rejected. Its operation list is more power than a catalog client needs and is easy to misuse. It could be added later as a second media type on the same endpoint without changing the use case.

### Delete is a soft delete

Products are referenced by orders, analytics and caches. `DELETE` sets `status = ARCHIVED` and returns `204`. Archived products disappear from every read and reject every write, and repeating the delete is a no-op. An archived SKU stays reserved, so order history never points at two different products with the same SKU. A hard delete for legal erasure would be an administrative job, not a public endpoint.

## 7. Validation and errors

Validation happens at two levels, sharing the same constants:

- **Request DTOs** carry Bean Validation constraints. They report every bad field at once and document the contract in OpenAPI.
- **The domain** re-checks in value-object and `ProductDetails` constructors. It is the last line of defence, and the only one for `PATCH`, whose body never passes through a DTO.

The SKU format, length limits and price precision are defined once in the domain and referenced by the DTO annotations, so the two levels cannot drift apart.

Every error is an RFC 9457 problem with a stable `type` URI, produced in one place, `ApiExceptionHandler`. An unknown category in a request body is `422`, not `404`: the URL addresses a valid resource and the body is well formed; what is missing is something the body refers to. An unknown category used as a listing filter is `404`. A database outage is `503` with `Retry-After`, which tells clients and load balancers the failure is temporary.

## 8. SOLID and patterns

| Principle | Where it shows |
|---|---|
| Single responsibility | Controllers handle HTTP only, use cases orchestrate one operation inside a transaction, the domain enforces rules, mappers translate, decorators cache. |
| Open/closed | A new filter is a new `Specification` plus one line in `matching`. A new sort is a new `SortOption` constant; the exhaustive switches in `KeysetPredicates` fail to compile until it is handled. |
| Liskov substitution | `CachingProductRepository` can stand in for `JpaProductRepositoryAdapter` anywhere a `ProductRepository` is expected. Use-case tests run against an in-memory fake of the same port. |
| Interface segregation | One interface per use case. Separate write and read repository ports. `IdGenerator` and `SearchCacheGeneration` are tiny single-purpose ports. |
| Dependency inversion | The domain owns the port interfaces. Adapters implement them, and `config` wires them. The domain compiles without Spring. |

| Pattern | Use |
|---|---|
| Ports and adapters | Overall structure |
| Repository | `ProductRepository`, `ProductSearchRepository`, `CategoryRepository` |
| Decorator | Caching around each repository |
| Specification | Composable JPA predicates for status, category and price |
| Strategy | `SortOption` supplies its key, direction and cursor value |
| Builder | `ProductSearchCriteria` |
| Value object and static factory | `Money`, `Sku`, `ProductId`, `Product.create` enforce invariants at construction |
| Cache-aside | Read path, with generation-based invalidation for listings |
| Sealed tri-state | `PatchField` for merge-patch semantics |

Deliberately not used: abstract base classes, where composition was enough, and domain events, which nothing consumes yet. Events arrive with the search read model in section 10, through a transactional outbox.

## 9. Capacity reasoning

These are order-of-magnitude estimates for one service instance, to show where the limits are, not measurements:

| Path | Cost | Rough throughput per instance |
|---|---|---|
| Single product, cache hit | One Redis round trip, under 1 ms | Tens of thousands of requests per second; CPU and JSON serialization become the limit |
| Single product, cache miss | One primary-key lookup, 1 to 2 ms | Thousands per second, bounded by the connection pool |
| Listing first page, cache hit | Two Redis round trips | Similar to a product cache hit |
| Listing, cache miss or deep page | Index range scan of `limit + 1` rows, 1 to 3 ms | Low thousands per second |
| Write | Insert or update plus index maintenance and invalidation, about 5 ms | Far above the expected write rate |

With a realistic cache hit ratio above 90%, adding stateless instances is the only scaling lever needed for a long time. At this catalog size PostgreSQL is not the bottleneck. The connection pool is kept small on purpose: a few connections running short indexed queries outperform many connections queueing inside the database.

## 10. Scaling path

1. **Horizontal app scaling.** Instances are stateless; add more behind the load balancer.
2. **Read replicas.** Route the query service's database reads to a replica, for example with a routing `DataSource` keyed on read-only transactions. Handle replication lag by sending a client's reads to the primary briefly after its own write.
3. **Hotter caching.** Add an in-process Caffeine tier in front of Redis, with Redis pub/sub to invalidate it across instances. Add request coalescing and early refresh so a popular product's expiry does not cause a stampede. Put a CDN in front of the public GETs; they already carry `ETag` and `Cache-Control`.
4. **Search read model.** When full-text search, facets or relevance ranking are needed, add OpenSearch. Writes record a `ProductChanged` event in an outbox table in the same transaction, and an indexer publishes it. `ProductSearchRepository` gets an OpenSearch adapter; the web and application layers do not change.
5. **A hundred times the data.** At 30 million rows, still PostgreSQL: partition `products` by category, keep the same indexes per partition, tune vacuum, and move `description` to a side table so hot rows stay narrow. Only beyond that does sharding become a conversation.
6. **Bulk writes.** A bulk import endpoint accepts a file, queues it, and applies batched upserts. An `Idempotency-Key` header on `POST`, stored briefly, makes client retries safe.

Cross-cutting concerns belong at the edge rather than in this service: authentication and authorization (an OAuth2 resource server or an API gateway), rate limiting, TLS and a web application firewall.

## 11. Failure modes

| Failure | Behaviour |
|---|---|
| Redis down | Reads fall through to PostgreSQL, writes still succeed, `catalog.cache.failures` rises. Readiness stays green. |
| PostgreSQL down | Requests return `503` with `Retry-After`. Readiness fails and the load balancer drains the instance. |
| Connection pool exhausted | Acquisition times out after 3 seconds rather than queueing indefinitely; latency and pool metrics show it. |
| Slow query regression | Per-endpoint latency is recorded by Spring MVC's `http.server.requests` timer, which is where p95 alarms attach. |
| Hot product | Served from Redis; an in-process tier is the next step (section 10). |
| Two writers on one product | Optimistic locking: one wins, the other gets `409` or `412` and retries on fresh state. |
| Tampered or stale cursor | `400` with type `invalid-cursor`. |
| Instance shutdown | Graceful shutdown lets in-flight requests finish before the process exits. |

## 12. Operations

- **Health:** `/actuator/health/liveness` and `/actuator/health/readiness`. Readiness includes the database only.
- **Metrics:** `/actuator/prometheus`, including request timers and the cache failure counter.
- **Logs:** the `prod` profile switches console logs to structured JSON (ECS), one object per line, so fields can be indexed without parsing text.
- **Configuration:** database and Redis locations come from environment variables. Cache lifetimes and HTTP max-age live under `catalog.*` in `application.yml`.
- **Delivery path:** a multi-stage container image (build with the Maven wrapper, run on a JRE 21 base as a non-root user, with layered jars for small rebuilds), and a CI pipeline that runs `./mvnw verify` with Testcontainers, builds the image with an immutable tag, and promotes the same image through environments. Flyway runs migrations on startup; migrations are written to be backward compatible, so old and new instances can run side by side during a rolling deploy.

## 13. Testing strategy

| Level | What it proves | Tools |
|---|---|---|
| Domain and application | Value-object rules, product lifecycle, criteria and cursor validation, merge-patch semantics, every use case against in-memory fakes of the ports | JUnit 5, AssertJ |
| Architecture | The dependency rule, framework-free domain, transactions only in use cases, controllers only see use-case interfaces | ArchUnit |
| Web slice | Status codes, problem bodies, ETag and 304, If-Match, the merge-patch media type, parameter validation | `@WebMvcTest`, MockMvc |
| Cache adapter | Read-through, write-transaction bypass, after-commit invalidation, fail-open on cache errors, first-page-only listing cache, Redis serialization round trips | JUnit 5, in-memory cache |
| Persistence | Mappings validated against the Flyway schema, jsonb round trip, unique SKU, optimistic locking, and a page walk for every sort over data full of price and time ties, with no duplicates, no gaps and correct order | `@DataJpaTest`, Testcontainers PostgreSQL |
| Full stack | The product lifecycle over HTTP with real PostgreSQL and Redis, cache population and eviction, listing freshness after writes, cursor walks and the error statuses | `@SpringBootTest`, Testcontainers |

Fakes are preferred over mocks for ports, because they test behaviour rather than call sequences. Mocks are used only at the web slice, to isolate HTTP concerns.

## 14. Out of scope, and how each would be added

| Item | Why not now | How it would be added |
|---|---|---|
| Authentication and rate limiting | Edge concerns | OAuth2 resource server or an API gateway |
| Product variants (size and colour SKUs) | Changes the aggregate | A `product_variants` child table; listings stay keyed on the parent |
| Category hierarchy | Not in the brief | `parent_id` with a closure table or `ltree`; the filter resolves descendants |
| Full-text search and facets | Not in the brief | OpenSearch read model through an outbox (section 10) |
| Multiple currencies | Not in the brief | The column and value object already carry a currency; add a price list keyed by currency |
| Images and media | Not in the brief | Object storage keys on the product, CDN URLs in the response |
| Inventory | A different bounded context | A separate service; the catalog stays about descriptive data |
