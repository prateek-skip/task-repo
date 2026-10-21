package com.example.catalog.domain.exception;

import com.example.catalog.domain.model.ProductId;

/** The client made its change against a version of the product that is no longer current. */
public class VersionMismatchException extends RuntimeException {

    private final ProductId productId;
    private final long expectedVersion;
    private final long currentVersion;

    public VersionMismatchException(ProductId productId, long expectedVersion, long currentVersion) {
        super("Product " + productId + " is at version " + currentVersion + ", not " + expectedVersion);
        this.productId = productId;
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }

    public ProductId productId() {
        return productId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
