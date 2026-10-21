package com.example.catalog.domain.port;

import com.example.catalog.domain.exception.ConcurrentUpdateException;
import com.example.catalog.domain.exception.DuplicateSkuException;
import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.model.ProductId;
import java.util.Optional;

/**
 * Write-side store for the product aggregate. Insert and update are separate on purpose: the
 * caller always knows which one it means, and the store never has to guess from the id.
 */
public interface ProductRepository {

    /** Finds a product in any status, archived included. */
    Optional<Product> findById(ProductId id);

    /**
     * Stores a new product and returns it as stored.
     *
     * @throws DuplicateSkuException if another product already uses the SKU
     */
    Product insert(Product product);

    /**
     * Stores a changed product and returns it as stored, with its new version.
     *
     * @throws ConcurrentUpdateException if the stored version is no longer the one the product was read at
     */
    Product update(Product product);
}
