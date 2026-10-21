package com.example.catalog.application.query;

import com.example.catalog.domain.query.Cursor;
import com.example.catalog.domain.query.SortOption;
import java.math.BigDecimal;

/**
 * A listing request as the client expressed it. Any field may be null, meaning "not filtered" or
 * "use the default".
 */
public record SearchProductsQuery(
        String categorySlug,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        SortOption sort,
        Integer limit,
        Cursor cursor) {
}
