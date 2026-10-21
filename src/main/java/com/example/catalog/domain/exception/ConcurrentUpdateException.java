package com.example.catalog.domain.exception;

import com.example.catalog.domain.model.ProductId;

/** Another request changed the product between this request reading it and writing it. */
public class ConcurrentUpdateException extends RuntimeException {

    private final ProductId productId;

    public ConcurrentUpdateException(ProductId productId) {
        super("Product " + productId + " was changed by another request; reload it and retry");
        this.productId = productId;
    }

    public ProductId productId() {
        return productId;
    }
}
