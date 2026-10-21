package com.example.catalog.adapter.out.cache;

import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.port.CategoryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * In-process snapshot of all categories. They are a handful of rows that change only with a
 * migration, and every create and filtered listing looks one up, so a local copy beats a network
 * round trip. The TTL only bounds staleness if a row is ever edited by hand.
 */
public class CachingCategoryRepository implements CategoryRepository {

    private final CategoryRepository delegate;
    private final Duration ttl;
    private final Clock clock;

    private volatile Snapshot snapshot;

    public CachingCategoryRepository(CategoryRepository delegate, Duration ttl, Clock clock) {
        this.delegate = delegate;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        return Optional.ofNullable(current().bySlug().get(slug));
    }

    @Override
    public List<Category> findAll() {
        return current().all();
    }

    // Two threads may both reload after expiry. That is harmless (same data, last write wins)
    // and cheaper than locking on every lookup.
    private Snapshot current() {
        Snapshot existing = snapshot;
        Instant now = clock.instant();
        if (existing != null && now.isBefore(existing.expiresAt())) {
            return existing;
        }
        List<Category> all = List.copyOf(delegate.findAll());
        Map<String, Category> bySlug = all.stream().collect(Collectors.toUnmodifiableMap(Category::slug, Function.identity()));
        Snapshot fresh = new Snapshot(all, bySlug, now.plus(ttl));
        snapshot = fresh;
        return fresh;
    }

    private record Snapshot(List<Category> all, Map<String, Category> bySlug, Instant expiresAt) {
    }
}
