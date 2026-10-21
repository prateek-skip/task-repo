# 0001. PostgreSQL as the system of record

**Status:** Accepted

## Context

The catalog holds a few hundred thousand products with a stable core shape and a variable set of attributes. The hot queries filter by category and price range, sort by price or recency, and paginate. Writes must be transactional and SKUs must be unique.

## Decision

Use PostgreSQL. Store the fixed fields as typed columns, the variable attributes as `jsonb`, and serve each (filter, sort) shape the API exposes with a composite B-tree index ending in `id`. Flyway owns the schema; Hibernate only validates against it.

## Consequences

- The hot query is one index range scan; the whole working set fits in memory.
- Uniqueness and transactions come from the database, not from application code.
- Full-text search and faceting are not served well. They will need a separate read model (see [0003](0003-cache-aside-with-generation-invalidation.md) and DESIGN.md section 10).

## Alternatives considered

- **MongoDB.** The flexible schema is not needed beyond what `jsonb` gives, and it would not speed up the hot query.
- **DynamoDB.** Works for one filter and sort per index, but combined filters and alternate sort orders push query planning into the application, and popular categories risk hot partitions.
- **OpenSearch as the primary store.** Strong for search, weak as a transactional source of truth.
