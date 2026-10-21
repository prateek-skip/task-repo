package com.example.catalog.adapter.out.cache;

import com.example.catalog.domain.port.ProductSearchRepository;
import com.example.catalog.domain.query.PageResult;
import com.example.catalog.domain.query.ProductSearchCriteria;
import com.example.catalog.domain.query.ProductSummary;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Cache-aside decorator for listings. Keys combine the current generation with the canonical
 * query, so any product write invalidates every cached page in one step. Writes are rare, which
 * makes that cheaper and safer than working out which pages a change affects.
 */
public class CachingProductSearchRepository implements ProductSearchRepository {

    private final ProductSearchRepository delegate;
    private final FailOpenCache cache;
    private final SearchCacheGeneration generation;

    public CachingProductSearchRepository(ProductSearchRepository delegate, FailOpenCache cache, SearchCacheGeneration generation) {
        this.delegate = delegate;
        this.cache = cache;
        this.generation = generation;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PageResult<ProductSummary> search(ProductSearchCriteria criteria) {
        // First pages carry most of the traffic (category landing pages). Deeper pages are already
        // a cheap index range scan and rarely repeat, so caching them would mostly churn memory.
        if (criteria.after().isPresent()) {
            return delegate.search(criteria);
        }
        OptionalLong current = generation.current();
        if (current.isEmpty()) {
            return delegate.search(criteria);
        }
        String key = current.getAsLong() + ":" + criteria.cacheKey();
        Optional<PageResult<ProductSummary>> cached = cache.get(key, PageResult.class).map(page -> (PageResult<ProductSummary>) page);
        if (cached.isPresent()) {
            return cached.get();
        }
        PageResult<ProductSummary> page = delegate.search(criteria);
        cache.put(key, page);
        return page;
    }
}
