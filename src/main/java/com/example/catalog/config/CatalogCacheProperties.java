package com.example.catalog.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Cache lifetimes. Each TTL is a staleness bound, not the main invalidation mechanism. */
@ConfigurationProperties("catalog.cache")
public record CatalogCacheProperties(
        @DefaultValue("10m") Duration productTtl,
        @DefaultValue("60s") Duration searchTtl,
        @DefaultValue("1h") Duration categoryTtl) {
}
