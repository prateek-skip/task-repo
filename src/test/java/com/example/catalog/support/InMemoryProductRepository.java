package com.example.catalog.support;

import com.example.catalog.domain.exception.ConcurrentUpdateException;
import com.example.catalog.domain.exception.DuplicateSkuException;
import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.port.ProductRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Fake with the same contract as the database adapter: unique SKUs and version checks. */
public class InMemoryProductRepository implements ProductRepository {

    private final Map<ProductId, Product> rows = new LinkedHashMap<>();
    private int reads;

    @Override
    public Optional<Product> findById(ProductId id) {
        reads++;
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public Product insert(Product product) {
        if (rows.values().stream().anyMatch(existing -> existing.sku().equals(product.sku()))) {
            throw new DuplicateSkuException(product.sku());
        }
        rows.put(product.id(), product);
        return product;
    }

    @Override
    public Product update(Product product) {
        Product stored = rows.get(product.id());
        if (stored == null || stored.version() != product.version()) {
            throw new ConcurrentUpdateException(product.id());
        }
        Product saved = new Product(product.id(), product.sku(), product.details(),
                product.version() + 1, product.createdAt(), product.updatedAt());
        rows.put(saved.id(), saved);
        return saved;
    }

    public Product stored(ProductId id) {
        return rows.get(id);
    }

    public int reads() {
        return reads;
    }
}
