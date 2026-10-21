package com.example.catalog.domain.model;

import com.example.catalog.domain.exception.DomainValidationException;
import java.time.Instant;
import java.util.Objects;

/**
 * The product aggregate. Immutable: every change returns a new instance, which makes instances
 * safe to share across threads and to cache.
 *
 * <p>{@code version} is the optimistic-locking version. The domain carries it unchanged and the
 * store increments it on every successful write.
 */
public record Product(
        ProductId id,
        Sku sku,
        ProductDetails details,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public Product {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Product create(ProductId id, Sku sku, ProductDetails details, Instant now) {
        requireClientAssignable(details.status());
        return new Product(id, sku, details, 0L, now, now);
    }

    /** Replaces every client-editable field. Identity, SKU and creation time never change. */
    public Product update(ProductDetails newDetails, Instant now) {
        if (isArchived()) {
            throw new IllegalStateException("Archived product " + id + " is read-only");
        }
        requireClientAssignable(newDetails.status());
        return new Product(id, sku, newDetails, version, createdAt, now);
    }

    /** Soft delete. Idempotent, so retrying a delete is always safe. */
    public Product archive(Instant now) {
        if (isArchived()) {
            return this;
        }
        return new Product(id, sku, details.withStatus(ProductStatus.ARCHIVED), version, createdAt, now);
    }

    public boolean isArchived() {
        return details.status() == ProductStatus.ARCHIVED;
    }

    private static void requireClientAssignable(ProductStatus status) {
        if (!status.isAssignableByClient()) {
            throw new DomainValidationException("status", "must be ACTIVE or INACTIVE; delete the product to archive it");
        }
    }
}
