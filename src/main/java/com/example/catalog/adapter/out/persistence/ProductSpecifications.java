package com.example.catalog.adapter.out.persistence;

import com.example.catalog.domain.model.ProductStatus;
import com.example.catalog.domain.query.ProductSearchCriteria;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * One small predicate per filter, composed from whatever the criteria contains. A new filter is a
 * new method plus one line in {@link #matching}; no existing predicate changes.
 */
final class ProductSpecifications {

    private ProductSpecifications() {
    }

    static Specification<ProductEntity> matching(ProductSearchCriteria criteria) {
        List<Specification<ProductEntity>> filters = new ArrayList<>();
        filters.add(hasStatus(criteria.status()));
        criteria.category().ifPresent(category -> filters.add(inCategory(category.value())));
        criteria.minPrice().ifPresent(min -> filters.add(priceAtLeast(min)));
        criteria.maxPrice().ifPresent(max -> filters.add(priceAtMost(max)));
        return Specification.allOf(filters);
    }

    static Specification<ProductEntity> hasStatus(ProductStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<ProductEntity> inCategory(UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId);
    }

    static Specification<ProductEntity> priceAtLeast(BigDecimal min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    static Specification<ProductEntity> priceAtMost(BigDecimal max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }
}
