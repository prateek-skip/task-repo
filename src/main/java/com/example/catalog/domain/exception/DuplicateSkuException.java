package com.example.catalog.domain.exception;

import com.example.catalog.domain.model.Sku;

public class DuplicateSkuException extends RuntimeException {

    private final Sku sku;

    public DuplicateSkuException(Sku sku) {
        super("A product with SKU " + sku + " already exists");
        this.sku = sku;
    }

    public Sku sku() {
        return sku;
    }
}
