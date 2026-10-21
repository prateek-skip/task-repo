package com.example.catalog.application.port.in;

import com.example.catalog.application.query.SearchProductsQuery;
import com.example.catalog.domain.query.PageResult;
import com.example.catalog.domain.query.ProductSummary;

public interface SearchProductsUseCase {

    PageResult<ProductSummary> search(SearchProductsQuery query);
}
