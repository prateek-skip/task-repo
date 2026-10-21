package com.example.catalog.application.service;

import static com.example.catalog.support.TestData.NOW;
import static com.example.catalog.support.TestData.RUNNING;
import static com.example.catalog.support.TestData.TOPS;
import static com.example.catalog.support.TestData.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.catalog.application.query.SearchProductsQuery;
import com.example.catalog.domain.exception.CategoryNotFoundException;
import com.example.catalog.domain.exception.ProductNotFoundException;
import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.query.PageResult;
import com.example.catalog.domain.query.ProductSearchCriteria;
import com.example.catalog.domain.query.SortOption;
import com.example.catalog.support.InMemoryCategoryRepository;
import com.example.catalog.support.InMemoryProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProductQueryServiceTest {

    private final InMemoryProductRepository products = new InMemoryProductRepository();
    private final AtomicReference<ProductSearchCriteria> lastSearch = new AtomicReference<>();
    private final ProductQueryService service = new ProductQueryService(
            products,
            criteria -> {
                lastSearch.set(criteria);
                return new PageResult<>(List.of(), criteria.limit(), null);
            },
            new InMemoryCategoryRepository(RUNNING, TOPS));

    @Test
    void archivedProductsAreNotFound() {
        Product archived = products.insert(product()).archive(NOW.plusSeconds(1));
        products.update(archived);

        assertThatThrownBy(() -> service.get(archived.id())).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void searchResolvesCategorySlugToItsId() {
        service.search(new SearchProductsQuery("tops", new BigDecimal("10"), null, SortOption.PRICE_DESC, 5, null));

        ProductSearchCriteria criteria = lastSearch.get();
        assertThat(criteria.category()).contains(TOPS.id());
        assertThat(criteria.sort()).isEqualTo(SortOption.PRICE_DESC);
        assertThat(criteria.limit()).isEqualTo(5);
    }

    @Test
    void unknownCategoryFilterIsNotFound() {
        assertThatThrownBy(() -> service.search(new SearchProductsQuery("no-such", null, null, null, null, null)))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void listsCategoriesByName() {
        assertThat(service.listCategories()).containsExactly(RUNNING, TOPS);
    }
}
