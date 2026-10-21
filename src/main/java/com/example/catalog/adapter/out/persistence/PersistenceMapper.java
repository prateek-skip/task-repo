package com.example.catalog.adapter.out.persistence;

import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.model.CategoryId;
import com.example.catalog.domain.model.Money;
import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.model.ProductDetails;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.model.Sku;
import com.example.catalog.domain.query.ProductSummary;
import java.util.HashMap;

/** Translation between table rows and domain objects, in one place. */
final class PersistenceMapper {

    private PersistenceMapper() {
    }

    static Product toDomain(ProductEntity entity) {
        return toDomain(entity, toDomain(entity.getCategory()));
    }

    static Product toDomain(ProductEntity entity, Category category) {
        ProductDetails details = new ProductDetails(
                entity.getName(),
                entity.getDescription(),
                category,
                Money.of(entity.getPrice(), entity.getCurrency()),
                entity.getStatus(),
                entity.getAttributes());
        return new Product(
                ProductId.of(entity.getId()),
                Sku.of(entity.getSku()),
                details,
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static Category toDomain(CategoryEntity entity) {
        return new Category(CategoryId.of(entity.getId()), entity.getSlug(), entity.getName());
    }

    static ProductSummary toSummary(ProductSummaryRow row) {
        return new ProductSummary(
                ProductId.of(row.id()),
                Sku.of(row.sku()),
                row.name(),
                row.categorySlug(),
                Money.of(row.price(), row.currency()),
                row.status(),
                row.createdAt());
    }

    static ProductEntity toNewEntity(Product product) {
        ProductEntity entity = new ProductEntity(product.id().value(), product.sku().value(), product.createdAt());
        copyDetails(product, entity);
        return entity;
    }

    static void copyDetails(Product product, ProductEntity entity) {
        ProductDetails details = product.details();
        entity.setName(details.name());
        entity.setDescription(details.description());
        entity.setCategoryId(details.category().id().value());
        entity.setPrice(details.price().amount());
        entity.setCurrency(details.price().currencyCode());
        entity.setStatus(details.status());
        // Hibernate compares JSON values to detect changes, so it gets its own mutable copy.
        entity.setAttributes(new HashMap<>(details.attributes()));
        entity.setUpdatedAt(product.updatedAt());
    }
}
