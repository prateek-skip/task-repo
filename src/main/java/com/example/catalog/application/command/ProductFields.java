package com.example.catalog.application.command;

import com.example.catalog.domain.model.Money;
import com.example.catalog.domain.model.ProductStatus;
import java.util.Map;

/**
 * The editable fields of a create or full update. The category is still a slug here; resolving
 * it needs a lookup, which is the use case's job.
 */
public record ProductFields(
        String name,
        String description,
        String categorySlug,
        Money price,
        ProductStatus status,
        Map<String, String> attributes) {
}
