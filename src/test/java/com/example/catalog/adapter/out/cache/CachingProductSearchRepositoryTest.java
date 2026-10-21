package com.example.catalog.adapter.out.cache;

import static com.example.catalog.support.TestData.NOW;
import static com.example.catalog.support.TestData.PRODUCT_ID;
import static com.example.catalog.support.TestData.SKU;
import static com.example.catalog.support.TestData.usd;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.catalog.domain.model.ProductStatus;
import com.example.catalog.domain.query.PageResult;
import com.example.catalog.domain.query.ProductSearchCriteria;
import com.example.catalog.domain.query.ProductSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

class CachingProductSearchRepositoryTest {

    private static final ProductSummary ROW =
            new ProductSummary(PRODUCT_ID, SKU, "Pegasus 41", "running-shoes", usd("129.99"), ProductStatus.ACTIVE, NOW);

    private final AtomicInteger databaseCalls = new AtomicInteger();
    private final InMemorySearchCacheGeneration generation = new InMemorySearchCacheGeneration();
    private final CachingProductSearchRepository repository = new CachingProductSearchRepository(
            criteria -> {
                databaseCalls.incrementAndGet();
                return PageResult.fromOverfetch(List.of(ROW, ROW), 1, criteria::cursorAfter);
            },
            new FailOpenCache(new ConcurrentMapCache("product-search"), new SimpleMeterRegistry()),
            generation);

    private final ProductSearchCriteria firstPage = ProductSearchCriteria.builder().limit(1).build();

    @Test
    void cachesFirstPages() {
        repository.search(firstPage);
        repository.search(firstPage);

        assertThat(databaseCalls).hasValue(1);
    }

    @Test
    void neverCachesDeeperPages() {
        PageResult<ProductSummary> page = repository.search(firstPage);
        ProductSearchCriteria secondPage = ProductSearchCriteria.builder().limit(1).after(page.nextCursor()).build();

        repository.search(secondPage);
        repository.search(secondPage);

        assertThat(databaseCalls).hasValue(3);
    }

    @Test
    void newGenerationMakesCachedPagesUnreachable() {
        repository.search(firstPage);
        generation.advance();
        repository.search(firstPage);

        assertThat(databaseCalls).hasValue(2);
    }

    @Test
    void bypassesCacheWhenGenerationIsUnknown() {
        generation.makeUnavailable();

        repository.search(firstPage);
        repository.search(firstPage);

        assertThat(databaseCalls).hasValue(2);
    }
}
