# 0007. Partial updates with JSON Merge Patch

**Status:** Accepted

## Context

Clients often change one or two fields: a price, a status, one attribute. With `PUT` alone they must read and resend the whole resource, and "clear this field" cannot be told apart from "I did not send this field".

## Decision

Support `PATCH` with `application/merge-patch+json` (RFC 7396). An absent field is kept, `null` clears it, and a value replaces it; nested attributes merge key by key. Each field of the command is a sealed `PatchField<T>` with the cases `Unchanged`, `Clear` and `Set`. The use case merges the patch into the current state and validates the result through the same rules as a create, under the same optimistic lock and optional `If-Match` as `PUT`. Unknown fields and the SKU are refused.

## Consequences

- Clients send only what changes, and can remove optional data explicitly.
- A patch can never leave a product invalid.
- Merge Patch cannot express "set this field to JSON null", which the catalog never needs.

## Alternatives considered

- **JSON Patch (RFC 6902).** More expressive (`add`, `remove`, `test`, array operations) but more than a catalog client needs and easier to misuse. It can be added later as a second media type on the same endpoint.
- **`PUT` only.** Simpler, but forces read-modify-write on clients and loses the absent-versus-null distinction.
