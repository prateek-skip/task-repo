package com.example.catalog.adapter.out.persistence;

import com.example.catalog.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Projection target for listing queries. Selecting only these columns skips the description and
 * attributes, and avoids building and dirty-checking full entities for read-only rows.
 */
public record ProductSummaryRow(
        UUID id,
        String sku,
        String name,
        String categorySlug,
        BigDecimal price,
        String currency,
        ProductStatus status,
        Instant createdAt) {
}
