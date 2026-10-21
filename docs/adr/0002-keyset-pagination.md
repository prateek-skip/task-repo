# 0002. Keyset pagination with opaque cursors

**Status:** Accepted

## Context

Clients page through listings, often deeply (infinite scroll). With `OFFSET`, the database reads and discards every earlier row, so deep pages get slower in proportion to their depth.

## Decision

Paginate by keyset. Each sort is completed by `id`, making the order total. The next page is the rows strictly after the last row's `(sort key, id)`. The query fetches `limit + 1` rows to learn whether a next page exists. The position is returned as an opaque Base64URL cursor containing a format version, the sort value, the last id, and a fingerprint of the filters and sort. A cursor whose fingerprint does not match the request is rejected with `400`.

## Consequences

- Every page costs the same: an index range scan of `limit + 1` rows.
- No total count and no jumping to page N.
- Clients cannot construct cursors, so the format can change freely.
- If an item's sort key changes mid-scroll, it can move between pages. This is inherent to paginating live data.

## Alternatives considered

- **Offset pagination.** Simple and supports page numbers, but cost grows with depth, and inserts during a scroll cause duplicates.
- **Transparent cursors** (for example `?afterPrice=…&afterId=…`). Same performance, but freezes the format into the public contract and lets clients mix filters with incompatible positions.
