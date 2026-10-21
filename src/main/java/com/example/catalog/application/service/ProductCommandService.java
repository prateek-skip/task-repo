package com.example.catalog.application.service;

import com.example.catalog.application.command.AttributesPatch;
import com.example.catalog.application.command.CreateProductCommand;
import com.example.catalog.application.command.PatchField;
import com.example.catalog.application.command.PatchProductCommand;
import com.example.catalog.application.command.ProductFields;
import com.example.catalog.application.command.UpdateProductCommand;
import com.example.catalog.application.port.in.CreateProductUseCase;
import com.example.catalog.application.port.in.DeleteProductUseCase;
import com.example.catalog.application.port.in.PatchProductUseCase;
import com.example.catalog.application.port.in.UpdateProductUseCase;
import com.example.catalog.domain.exception.DomainValidationException;
import com.example.catalog.domain.exception.InvalidCategoryReferenceException;
import com.example.catalog.domain.exception.ProductNotFoundException;
import com.example.catalog.domain.exception.VersionMismatchException;
import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.model.Money;
import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.model.ProductDetails;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.port.CategoryRepository;
import com.example.catalog.domain.port.IdGenerator;
import com.example.catalog.domain.port.ProductRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.OptionalLong;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write-side use cases. Each one is a single transaction: read the current state, let the domain
 * decide the new state, store it. Business rules live in the domain, not here.
 */
public class ProductCommandService
        implements CreateProductUseCase, UpdateProductUseCase, PatchProductUseCase, DeleteProductUseCase {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final IdGenerator ids;
    private final Clock clock;

    public ProductCommandService(ProductRepository products, CategoryRepository categories, IdGenerator ids, Clock clock) {
        this.products = products;
        this.categories = categories;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Product create(CreateProductCommand command) {
        Product product = Product.create(ProductId.of(ids.newId()), command.sku(), toDetails(command.fields()), now());
        return products.insert(product);
    }

    @Override
    @Transactional
    public Product update(UpdateProductCommand command) {
        Product current = loadForWrite(command.id(), command.expectedVersion());
        if (!current.sku().equals(command.sku())) {
            throw new DomainValidationException("sku", "cannot be changed");
        }
        return products.update(current.update(toDetails(command.fields()), now()));
    }

    @Override
    @Transactional
    public Product patch(PatchProductCommand command) {
        Product current = loadForWrite(command.id(), command.expectedVersion());
        return products.update(current.update(merge(current.details(), command), now()));
    }

    @Override
    @Transactional
    public void delete(ProductId id) {
        Product product = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        // Deleting an archived product is a no-op, so a retried DELETE still succeeds.
        if (!product.isArchived()) {
            products.update(product.archive(now()));
        }
    }

    // Archived products behave as deleted for every write. The If-Match check happens here, on
    // freshly read state, so a stale client gets 412 before anything is written.
    private Product loadForWrite(ProductId id, OptionalLong expectedVersion) {
        Product current = products.findById(id)
                .filter(product -> !product.isArchived())
                .orElseThrow(() -> new ProductNotFoundException(id));
        expectedVersion.ifPresent(expected -> {
            if (expected != current.version()) {
                throw new VersionMismatchException(id, expected, current.version());
            }
        });
        return current;
    }

    private ProductDetails toDetails(ProductFields fields) {
        return new ProductDetails(
                fields.name(),
                fields.description(),
                requireCategory(fields.categorySlug()),
                fields.price(),
                fields.status(),
                fields.attributes());
    }

    // Each field is kept, cleared or replaced, then the whole result goes through the same
    // ProductDetails rules as a create. Clearing a required field therefore fails validation.
    private ProductDetails merge(ProductDetails current, PatchProductCommand patch) {
        BigDecimal amount = patch.price().applyTo(current.price().amount());
        String currency = patch.currency().applyTo(current.price().currencyCode());
        return new ProductDetails(
                patch.name().applyTo(current.name()),
                patch.description().applyTo(current.description()),
                patch.categorySlug().map(this::requireCategory).applyTo(current.category()),
                Money.of(amount, currency),
                patch.status().applyTo(current.status()),
                mergeAttributes(current.attributes(), patch.attributes()));
    }

    private static Map<String, String> mergeAttributes(Map<String, String> current, PatchField<AttributesPatch> patch) {
        return switch (patch) {
            case PatchField.Unchanged<AttributesPatch> unchanged -> current;
            case PatchField.Clear<AttributesPatch> clear -> Map.of();
            case PatchField.Set<AttributesPatch> set -> set.value().applyTo(current);
        };
    }

    private Category requireCategory(String slug) {
        if (slug == null) {
            throw new DomainValidationException("category", "must not be null");
        }
        return categories.findBySlug(slug).orElseThrow(() -> new InvalidCategoryReferenceException(slug));
    }

    // PostgreSQL keeps microseconds. Truncating here means the value returned from a write is
    // identical to what every later read returns.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
