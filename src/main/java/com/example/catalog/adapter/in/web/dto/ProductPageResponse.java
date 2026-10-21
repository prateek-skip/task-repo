package com.example.catalog.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** One listing page. {@code nextCursor} is omitted on the last page. */
public record ProductPageResponse(
        List<ProductSummaryResponse> items,
        int limit,
        @JsonInclude(JsonInclude.Include.NON_NULL) String nextCursor) {
}
