package com.example.catalog.adapter.out.persistence;

import com.example.catalog.domain.port.ProductSearchRepository;
import com.example.catalog.domain.query.PageResult;
import com.example.catalog.domain.query.ProductSearchCriteria;
import com.example.catalog.domain.query.ProductSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Listing queries. Each page is one statement: filters, keyset position and order map onto a
 * composite index, and the projection reads only the columns a listing shows.
 */
public class JpaProductSearchAdapter implements ProductSearchRepository {

    private final EntityManager entityManager;

    public JpaProductSearchAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public PageResult<ProductSummary> search(ProductSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ProductSummaryRow> query = cb.createQuery(ProductSummaryRow.class);
        Root<ProductEntity> product = query.from(ProductEntity.class);
        Join<ProductEntity, CategoryEntity> category = product.join("category");

        Specification<ProductEntity> filter = ProductSpecifications.matching(criteria);
        if (criteria.after().isPresent()) {
            filter = filter.and(KeysetPredicates.after(criteria.sort(), criteria.after().get()));
        }

        query.select(cb.construct(ProductSummaryRow.class,
                        product.get("id"),
                        product.get("sku"),
                        product.get("name"),
                        category.get("slug"),
                        product.get("price"),
                        product.get("currency"),
                        product.get("status"),
                        product.get("createdAt")))
                .where(filter.toPredicate(product, query, cb))
                .orderBy(KeysetPredicates.orderBy(criteria.sort(), product, cb));

        List<ProductSummary> rows = entityManager.createQuery(query)
                .setMaxResults(criteria.limit() + 1)
                .getResultList()
                .stream()
                .map(PersistenceMapper::toSummary)
                .toList();
        return PageResult.fromOverfetch(rows, criteria.limit(), criteria::cursorAfter);
    }
}
