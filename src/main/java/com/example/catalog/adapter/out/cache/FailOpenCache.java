package com.example.catalog.adapter.out.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

/**
 * Wraps a cache so that its failures never fail a request. A broken cache read becomes a miss and
 * a broken write is skipped, so a Redis outage degrades the service to database reads. Failures
 * are counted, which makes the degraded state visible on dashboards rather than silent.
 */
public final class FailOpenCache {

    private static final Logger log = LoggerFactory.getLogger(FailOpenCache.class);

    private final Cache cache;
    private final Counter failures;

    public FailOpenCache(Cache cache, MeterRegistry meters) {
        this.cache = cache;
        this.failures = Counter.builder("catalog.cache.failures")
                .tag("cache", cache.getName())
                .description("Cache operations that failed and were bypassed")
                .register(meters);
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            return Optional.ofNullable(cache.get(key, type));
        } catch (RuntimeException e) {
            onFailure("get", key, e);
            return Optional.empty();
        }
    }

    public void put(String key, Object value) {
        try {
            cache.put(key, value);
        } catch (RuntimeException e) {
            onFailure("put", key, e);
        }
    }

    public void evict(String key) {
        try {
            cache.evict(key);
        } catch (RuntimeException e) {
            onFailure("evict", key, e);
        }
    }

    private void onFailure(String operation, String key, RuntimeException e) {
        failures.increment();
        log.warn("Cache {} failed on '{}' for key {}; continuing without cache: {}",
                operation, cache.getName(), key, e.toString());
    }
}
