# 0005. UUIDv7 identifiers

**Status:** Accepted

## Context

Products need identifiers that are unique across instances, safe to expose in URLs, and cheap for the primary-key index.

## Decision

Generate RFC 9562 version 7 UUIDs in the application, behind an `IdGenerator` port: a 48-bit millisecond timestamp followed by random bits.

## Consequences

- Inserts land near the right edge of the primary-key B-tree, unlike random v4 ids that touch pages all over it.
- Ids are not enumerable, unlike a sequence.
- No database round trip is needed to obtain an id, and tests can supply fixed ids.
- Ids reveal their approximate creation time, which is acceptable for catalog data.

## Alternatives considered

- **`bigserial`.** Compact and ordered, but enumerable, and it ties id generation to the database.
- **UUIDv4.** Unique and opaque, but random insert positions fragment the index.
