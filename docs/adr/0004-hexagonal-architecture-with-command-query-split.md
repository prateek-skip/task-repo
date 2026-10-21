# 0004. Hexagonal architecture with a command/query split

**Status:** Accepted

## Context

The read and write paths have different scaling needs and will likely be served by different infrastructure later (replicas, a search index). Business rules should be testable without a container, and infrastructure choices should be replaceable.

## Decision

Split the code into a plain-Java domain that owns its ports, an application layer with one interface per use case, adapters that implement the ports, and a composition root that wires them. Separate write use cases (`ProductCommandService`, working on the aggregate) from read use cases (`ProductQueryService`, working on lightweight projections). Enforce the dependency rule with ArchUnit.

## Consequences

- Domain rules and use cases are tested with in-memory fakes, in milliseconds.
- The read path can move to a replica or a search index by swapping one adapter.
- Caching, persistence and HTTP each live in one place.
- There are more types than in a layered "controller, service, repository" design, and mapping between layers is explicit code.

## Alternatives considered

- **Classic layered architecture with JPA entities as the model.** Less code, but persistence annotations and lazy-loading behaviour leak into business logic, and caching tends to spread across services.
- **Full CQRS with separate stores and events.** Valuable once a search read model exists; premature before that.
