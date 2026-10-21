package com.example.catalog.domain.query;

import static com.example.catalog.support.TestData.NOW;
import static com.example.catalog.support.TestData.PRODUCT_ID;
import static com.example.catalog.support.TestData.RUNNING;
import static com.example.catalog.support.TestData.SKU;
import static com.example.catalog.support.TestData.TOPS;
import static com.example.catalog.support.TestData.usd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.catalog.domain.exception.DomainValidationException;
import com.example.catalog.domain.exception.InvalidCursorException;
import com.example.catalog.domain.model.ProductStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductSearchCriteriaTest {

    private static final ProductSummary ROW =
            new ProductSummary(PRODUCT_ID, SKU, "Pegasus 41", "running-shoes", usd("129.99"), ProductStatus.ACTIVE, NOW);

    @Test
    void defaultsToNewestFirstWithTwentyRows() {
        ProductSearchCriteria criteria = ProductSearchCriteria.builder().build();

        assertThat(criteria.sort()).isEqualTo(SortOption.NEWEST);
        assertThat(criteria.limit()).isEqualTo(20);
        assertThat(criteria.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void rejectsLimitOutsideBounds() {
        assertThatThrownBy(() -> ProductSearchCriteria.builder().limit(0).build())
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> ProductSearchCriteria.builder().limit(101).build())
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsInvertedPriceRange() {
        assertThatThrownBy(() -> ProductSearchCriteria.builder()
                .minPrice(new BigDecimal("50")).maxPrice(new BigDecimal("10")).build())
                .isInstanceOf(DomainValidationException.class)
                .extracting("field").isEqualTo("minPrice");
    }

    @Test
    void equivalentPricesShareCacheKeyAndFingerprint() {
        ProductSearchCriteria a = ProductSearchCriteria.builder().minPrice(new BigDecimal("50")).build();
        ProductSearchCriteria b = ProductSearchCriteria.builder().minPrice(new BigDecimal("50.00")).build();

        assertThat(a.cacheKey()).isEqualTo(b.cacheKey());
        assertThat(a.fingerprint()).isEqualTo(b.fingerprint());
    }

    @Test
    void fingerprintIgnoresLimitButNotFiltersOrSort() {
        ProductSearchCriteria base = ProductSearchCriteria.builder().category(RUNNING.id()).limit(10).build();

        assertThat(ProductSearchCriteria.builder().category(RUNNING.id()).limit(50).build().fingerprint())
                .isEqualTo(base.fingerprint());
        assertThat(ProductSearchCriteria.builder().category(TOPS.id()).limit(10).build().fingerprint())
                .isNotEqualTo(base.fingerprint());
        assertThat(ProductSearchCriteria.builder().category(RUNNING.id()).sort(SortOption.PRICE_ASC).build().fingerprint())
                .isNotEqualTo(base.fingerprint());
    }

    @Test
    void acceptsCursorIssuedForSameQuery() {
        ProductSearchCriteria first = ProductSearchCriteria.builder().category(RUNNING.id()).sort(SortOption.PRICE_ASC).build();
        Cursor cursor = first.cursorAfter(ROW);

        ProductSearchCriteria next = ProductSearchCriteria.builder()
                .category(RUNNING.id()).sort(SortOption.PRICE_ASC).limit(5).after(cursor).build();

        assertThat(next.after()).contains(cursor);
        assertThat(cursor.sortValue()).isEqualTo("129.99");
    }

    @Test
    void rejectsCursorFromDifferentFilters() {
        Cursor cursor = ProductSearchCriteria.builder().category(RUNNING.id()).build().cursorAfter(ROW);

        assertThatThrownBy(() -> ProductSearchCriteria.builder().category(TOPS.id()).after(cursor).build())
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void rejectsCursorWithUnparseableSortValue() {
        ProductSearchCriteria criteria = ProductSearchCriteria.builder().sort(SortOption.PRICE_ASC).build();
        Cursor forged = new Cursor("not-a-price", PRODUCT_ID, criteria.fingerprint());

        assertThatThrownBy(() -> ProductSearchCriteria.builder().sort(SortOption.PRICE_ASC).after(forged).build())
                .isInstanceOf(InvalidCursorException.class);
    }
}
