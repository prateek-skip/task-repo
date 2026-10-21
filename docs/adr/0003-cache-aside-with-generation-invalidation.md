# 0003. Cache-aside in Redis, with generation-based invalidation for listings

**Status:** Accepted

## Context

Reads vastly outnumber writes. The same products and the same category landing pages are requested over and over. A short staleness window on listings is acceptable; reading your own write on a single product is expected.

## Decision

- Cache single products and first pages of listings in Redis, through decorators around the repository ports that are applied in the composition root.
- Evict a product's entry after its write commits.
- Prefix every listing key with a generation number stored in Redis, and advance it on every write, which makes all cached pages unreachable at once.
- Keep categories in an in-process snapshot.
- Bypass the cache inside read-write transactions.
- Treat every cache failure as a miss, and count it.
- Add `ETag` and `Cache-Control` to responses so browsers and a CDN absorb repeat reads.

## Consequences

- Use cases and the JPA adapter are unaware of caching. It can be removed or replaced in one place.
- Single products are read-your-own-writes; listings are at most one TTL (60 seconds) stale, and usually fresh.
- A Redis outage degrades to database reads instead of failing requests.
- Every write invalidates every cached listing page. That is cheap because writes are rare.
- The standard cache-aside race can briefly re-cache an old row; TTLs bound it.

## Alternatives considered

- **`@Cacheable` annotations.** Less code, but generation-prefixed keys become SpEL, fail-open behaviour is hidden in configuration, and the decorators are harder to unit test.
- **Write-through on update.** Avoids a cold read, but a delayed write from an older transaction can overwrite a newer entry. Eviction is safer.
- **Targeted listing invalidation.** Precise, but working out which pages a price change affects is complex and error-prone for little gain at this write rate.
