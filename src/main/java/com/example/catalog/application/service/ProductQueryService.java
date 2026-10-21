package com.example.catalog.application.service;

import com.example.catalog.application.port.in.GetProductUseCase;
import com.example.catalog.application.port.in.ListCategoriesUseCase;
import com.example.catalog.application.port.in.SearchProductsUseCase;
import com.example.catalog.application.query.SearchProductsQuery;
import com.example.catalog.domain.exception.CategoryNotFoundException;
import com.example.catalog.domain.exception.ProductNotFoundException;
import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.port.CategoryRepository;
import com.example.catalog.domain.port.ProductRepository;
import com.example.catalog.domain.port.ProductSearchRepository;
import com.example.catalog.domain.query.PageResult;
import com.example.catalog.domain.query.ProductSearchCriteria;
import com.example.catalog.domain.query.ProductSummary;
import java.util.List;

/**
 * Read-side use cases.
 *
 * <p>Deliberately not transactional. Every read here is a single statement, and an open
 * transaction would hold a pooled connection even when the answer comes from the cache. The
 * persistence adapter opens one only on a cache miss.
 */
public class ProductQueryService implements GetProductUseCase, SearchProductsUseCase, ListCategoriesUseCase {

    private final ProductRepository products;
    private final ProductSearchRepository search;
    private final CategoryRepository categories;

    public ProductQueryService(ProductRepository products, ProductSearchRepository search, CategoryRepository categories) {
        this.products = products;
        this.search = search;
        this.categories = categories;
    }

    @Override
    public Product get(ProductId id) {
        return products.findById(id)
                .filter(product -> !product.isArchived())
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    public PageResult<ProductSummary> search(SearchProductsQuery query) {
        ProductSearchCriteria.Builder criteria = ProductSearchCriteria.builder()
                .minPrice(query.minPrice())
                .maxPrice(query.maxPrice())
                .sort(query.sort())
                .limit(query.limit())
                .after(query.cursor());
        // Resolve the slug to an id so the query filters on the indexed foreign key.
        if (query.categorySlug() != null) {
            Category category = categories.findBySlug(query.categorySlug())
                    .orElseThrow(() -> new CategoryNotFoundException(query.categorySlug()));
            criteria.category(category.id());
        }
        return search.search(criteria.build());
    }

    @Override
    public List<Category> listCategories() {
        return categories.findAll();
    }
}
