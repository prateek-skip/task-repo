package com.example.catalog.adapter.in.web.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Full product representation. Prices are strings so no client ever rounds them through a float. */
public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        String category,
        String price,
        String currency,
        String status,
        Map<String, String> attributes,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
