# 0006. Delete is a soft delete

**Status:** Accepted

## Context

Products are referenced by orders, analytics and caches. Removing a row would leave those references dangling, and a reused SKU would make order history ambiguous.

## Decision

`DELETE` sets the status to `ARCHIVED` and returns `204`. Archived products are hidden from every read, reject every write, and deleting them again is a no-op. Clients cannot set `ARCHIVED` directly.

## Consequences

- History stays consistent, and a retried delete is always safe.
- An archived SKU stays reserved.
- Rows accumulate. At this scale that is negligible, and the status-leading indexes keep archived rows out of the listing range scans.
- Legal erasure requires a separate administrative job.

## Alternatives considered

- **Hard delete.** Simple, but breaks references and frees the SKU for reuse.
- **A separate archive table.** Keeps the main table lean, but every delete becomes a move between tables, for no benefit at this size.
