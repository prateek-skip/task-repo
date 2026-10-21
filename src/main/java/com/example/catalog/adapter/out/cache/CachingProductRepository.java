package com.example.catalog.adapter.out.cache;

import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.port.ProductRepository;
import java.util.Optional;

/**
 * Cache-aside decorator for single-product reads. Use cases and the JPA adapter do not know it
 * exists; it is put in front of the database in the composition root.
 */
public class CachingProductRepository implements ProductRepository {

    private final ProductRepository delegate;
    private final FailOpenCache cache;
    private final SearchCacheGeneration searchGeneration;

    public CachingProductRepository(ProductRepository delegate, FailOpenCache cache, SearchCacheGeneration searchGeneration) {
        this.delegate = delegate;
        this.cache = cache;
        this.searchGeneration = searchGeneration;
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        // A write must decide on the source of truth, so reads inside a read-write transaction
        // skip the cache. Only plain reads are served from it.
        if (Transactions.inReadWriteTransaction()) {
            return delegate.findById(id);
        }
        String key = id.toString();
        Optional<Product> cached = cache.get(key, Product.class);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<Product> loaded = delegate.findById(id);
        loaded.ifPresent(product -> cache.put(key, product));
        return loaded;
    }

    @Override
    public Product insert(Product product) {
        Product saved = delegate.insert(product);
        Transactions.afterCommit(searchGeneration::advance);
        return saved;
    }

    @Override
    public Product update(Product product) {
        Product saved = delegate.update(product);
        // After commit, not before: evicting earlier would let a concurrent reader put the old
        // row back into the cache before this change is visible in the database.
        Transactions.afterCommit(() -> {
            cache.evict(saved.id().toString());
            searchGeneration.advance();
        });
        return saved;
    }
}
