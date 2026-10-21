package com.example.catalog.domain.port;

import com.example.catalog.domain.query.PageResult;
import com.example.catalog.domain.query.ProductSearchCriteria;
import com.example.catalog.domain.query.ProductSummary;

/**
 * Read-side store for listings. Kept apart from {@link ProductRepository} so it can be served by a
 * replica or a search index later without touching the write path.
 */
public interface ProductSearchRepository {

    /** One page of products matching the criteria, in the criteria's sort order. */
    PageResult<ProductSummary> search(ProductSearchCriteria criteria);
}
