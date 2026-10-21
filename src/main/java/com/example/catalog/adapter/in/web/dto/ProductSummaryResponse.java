package com.example.catalog.adapter.in.web.dto;

import java.util.UUID;

public record ProductSummaryResponse(
        UUID id,
        String sku,
        String name,
        String category,
        String price,
        String currency,
        String status) {
}
