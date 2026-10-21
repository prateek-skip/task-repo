package com.example.catalog.domain.exception;

import com.example.catalog.domain.model.ProductId;

public class ProductNotFoundException extends RuntimeException {

    private final ProductId productId;

    public ProductNotFoundException(ProductId productId) {
        super("Product " + productId + " was not found");
        this.productId = productId;
    }

    public ProductId productId() {
        return productId;
    }
}
