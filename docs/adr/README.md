# Architecture decision records

Each record captures one decision: the context, what was decided, what it costs, and what was rejected. They are short on purpose; [DESIGN.md](../DESIGN.md) has the long form.

| # | Decision |
|---|---|
| [0001](0001-postgresql-as-system-of-record.md) | PostgreSQL as the system of record |
| [0002](0002-keyset-pagination.md) | Keyset pagination with opaque cursors |
| [0003](0003-cache-aside-with-generation-invalidation.md) | Cache-aside in Redis, with generation-based invalidation for listings |
| [0004](0004-hexagonal-architecture-with-command-query-split.md) | Hexagonal architecture with a command/query split |
| [0005](0005-uuidv7-identifiers.md) | UUIDv7 identifiers |
| [0006](0006-soft-delete.md) | Delete is a soft delete |
| [0007](0007-json-merge-patch.md) | Partial updates with JSON Merge Patch |
