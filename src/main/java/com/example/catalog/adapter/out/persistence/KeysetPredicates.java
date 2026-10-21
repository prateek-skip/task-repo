package com.example.catalog.adapter.out.persistence;

import com.example.catalog.domain.query.Cursor;
import com.example.catalog.domain.query.SortOption;
import com.example.catalog.domain.query.SortOption.Direction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Translates a sort and a cursor into ORDER BY and "after this row" predicates. The switches are
 * exhaustive over the sort key, so adding a sort option fails to compile until it is handled here.
 */
final class KeysetPredicates {

    private KeysetPredicates() {
    }

    static Specification<ProductEntity> after(SortOption sort, Cursor cursor) {
        UUID lastId = cursor.lastId().value();
        return (root, query, cb) -> switch (sort.key()) {
            case PRICE -> after(cb, root.get("price"), new BigDecimal(cursor.sortValue()),
                    root.get("id"), lastId, sort.direction());
            case CREATED_AT -> after(cb, root.get("createdAt"), Instant.parse(cursor.sortValue()),
                    root.get("id"), lastId, sort.direction());
        };
    }

    static List<Order> orderBy(SortOption sort, Root<ProductEntity> root, CriteriaBuilder cb) {
        Path<Object> key = switch (sort.key()) {
            case PRICE -> root.get("price");
            case CREATED_AT -> root.get("createdAt");
        };
        Path<Object> id = root.get("id");
        return sort.direction() == Direction.ASCENDING
                ? List.of(cb.asc(key), cb.asc(id))
                : List.of(cb.desc(key), cb.desc(id));
    }

    // JPA Criteria has no row-value comparison, so (key, id) > (value, lastId) is expanded.
    // The leading key >= value is logically redundant, but it gives the planner a range start
    // on the composite index; the OR then only filters ties at the page boundary.
    private static <T extends Comparable<? super T>> Predicate after(
            CriteriaBuilder cb, Path<T> key, T value, Path<UUID> id, UUID lastId, Direction direction) {
        if (direction == Direction.ASCENDING) {
            return cb.and(
                    cb.greaterThanOrEqualTo(key, value),
                    cb.or(cb.greaterThan(key, value), cb.greaterThan(id, lastId)));
        }
        return cb.and(
                cb.lessThanOrEqualTo(key, value),
                cb.or(cb.lessThan(key, value), cb.lessThan(id, lastId)));
    }
}
