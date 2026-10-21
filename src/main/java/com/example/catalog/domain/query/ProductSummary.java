package com.example.catalog.domain.query;

import com.example.catalog.domain.model.Money;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.model.ProductStatus;
import com.example.catalog.domain.model.Sku;
import java.time.Instant;

/**
 * Listing row. Leaves out the description and attributes so list pages stay small on the wire,
 * in the cache and in the database read.
 */
public record ProductSummary(
        ProductId id,
        Sku sku,
        String name,
        String categorySlug,
        Money price,
        ProductStatus status,
        Instant createdAt) {
}
