package com.example.catalog.support;

import com.example.catalog.application.command.ProductFields;
import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.model.CategoryId;
import com.example.catalog.domain.model.Money;
import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.model.ProductDetails;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.model.ProductStatus;
import com.example.catalog.domain.model.Sku;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Shared fixtures. Category ids match the seeded reference data. */
public final class TestData {

    public static final Category RUNNING =
            new Category(CategoryId.of(UUID.fromString("0192f3a0-0000-7000-8000-000000000001")), "running-shoes", "Running Shoes");
    public static final Category TOPS =
            new Category(CategoryId.of(UUID.fromString("0192f3a0-0000-7000-8000-000000000004")), "tops", "Tops");

    public static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
    public static final ProductId PRODUCT_ID = ProductId.of(UUID.fromString("0192f3a1-0000-7000-8000-00000000abcd"));
    public static final Sku SKU = Sku.of("NK-PEG-41-BLK");

    private TestData() {
    }

    public static Money usd(String amount) {
        return Money.of(new BigDecimal(amount), "USD");
    }

    public static ProductDetails details() {
        return new ProductDetails("Pegasus 41", "Responsive daily trainer.", RUNNING, usd("129.99"),
                ProductStatus.ACTIVE, Map.of("colour", "black", "gender", "unisex"));
    }

    public static Product product() {
        return Product.create(PRODUCT_ID, SKU, details(), NOW);
    }

    public static ProductFields fields() {
        return new ProductFields("Pegasus 41", "Responsive daily trainer.", RUNNING.slug(), usd("129.99"),
                ProductStatus.ACTIVE, Map.of("colour", "black", "gender", "unisex"));
    }
}
