package com.example.catalog.adapter.out.persistence;

import com.example.catalog.domain.exception.ConcurrentUpdateException;
import com.example.catalog.domain.exception.DuplicateSkuException;
import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.port.ProductRepository;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

/** {@link ProductRepository} backed by PostgreSQL through Spring Data JPA. */
public class JpaProductRepositoryAdapter implements ProductRepository {

    private static final String SKU_UNIQUE_CONSTRAINT = "uq_products_sku";

    private final SpringDataProductRepository rows;

    public JpaProductRepositoryAdapter(SpringDataProductRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        return rows.findWithCategoryById(id.value()).map(PersistenceMapper::toDomain);
    }

    @Override
    public Product insert(Product product) {
        try {
            // Flush now so a duplicate SKU surfaces here, as a domain error, not at commit time.
            ProductEntity saved = rows.saveAndFlush(PersistenceMapper.toNewEntity(product));
            return PersistenceMapper.toDomain(saved, product.details().category());
        } catch (DataIntegrityViolationException e) {
            if (violates(e, SKU_UNIQUE_CONSTRAINT)) {
                throw new DuplicateSkuException(product.sku());
            }
            throw e;
        }
    }

    @Override
    public Product update(Product product) {
        ProductEntity entity = rows.findById(product.id().value())
                .orElseThrow(() -> new ConcurrentUpdateException(product.id()));
        // The product was read at an older version than the row now holds. Writing it would
        // silently discard the other change.
        if (!Objects.equals(entity.getVersion(), product.version())) {
            throw new ConcurrentUpdateException(product.id());
        }
        PersistenceMapper.copyDetails(product, entity);
        try {
            // The UPDATE carries "WHERE version = ?", which catches a writer that slipped in
            // between the read above and this flush.
            ProductEntity saved = rows.saveAndFlush(entity);
            return PersistenceMapper.toDomain(saved, product.details().category());
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrentUpdateException(product.id());
        }
    }

    private static boolean violates(DataIntegrityViolationException e, String constraint) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraint.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
