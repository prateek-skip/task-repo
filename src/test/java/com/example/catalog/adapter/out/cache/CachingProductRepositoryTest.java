package com.example.catalog.adapter.out.cache;

import static com.example.catalog.support.TestData.NOW;
import static com.example.catalog.support.TestData.details;
import static com.example.catalog.support.TestData.product;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.catalog.domain.model.Product;
import com.example.catalog.support.InMemoryProductRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

class CachingProductRepositoryTest {

    private final InMemoryProductRepository database = new InMemoryProductRepository();
    private final ConcurrentMapCache store = new ConcurrentMapCache("products");
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final InMemorySearchCacheGeneration generation = new InMemorySearchCacheGeneration();
    private final CachingProductRepository repository =
            new CachingProductRepository(database, new FailOpenCache(store, meters), generation);

    private Product stored;

    @BeforeEach
    void seed() {
        stored = database.insert(product());
    }

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void readsThroughOnceThenServesFromCache() {
        assertThat(repository.findById(stored.id())).contains(stored);
        assertThat(repository.findById(stored.id())).contains(stored);

        assertThat(database.reads()).isEqualTo(1);
    }

    @Test
    void readsInsideAWriteTransactionGoToTheDatabase() {
        repository.findById(stored.id());
        TransactionSynchronizationManager.setActualTransactionActive(true);

        repository.findById(stored.id());

        assertThat(database.reads()).isEqualTo(2);
    }

    @Test
    void updateInvalidatesOnlyAfterCommit() {
        repository.findById(stored.id());
        TransactionSynchronizationManager.initSynchronization();

        repository.update(stored.update(details(), NOW.plusSeconds(1)));

        assertThat(store.get(stored.id().toString())).as("still cached before commit").isNotNull();
        assertThat(generation.value()).isZero();

        TransactionSynchronizationUtils.invokeAfterCommit(TransactionSynchronizationManager.getSynchronizations());

        assertThat(store.get(stored.id().toString())).isNull();
        assertThat(generation.value()).isEqualTo(1);
    }

    @Test
    void cacheFailureFallsBackToTheDatabase() {
        CachingProductRepository withBrokenCache =
                new CachingProductRepository(database, new FailOpenCache(new BrokenCache(), meters), generation);

        assertThat(withBrokenCache.findById(stored.id())).contains(stored);
        assertThat(meters.counter("catalog.cache.failures", "cache", "broken").count()).isEqualTo(2);
    }

    private static final class BrokenCache extends ConcurrentMapCache {

        BrokenCache() {
            super("broken");
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            throw new IllegalStateException("connection refused");
        }

        @Override
        public void put(Object key, Object value) {
            throw new IllegalStateException("connection refused");
        }
    }
}
