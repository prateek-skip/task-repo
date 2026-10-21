package com.example.catalog.application.service;

import static com.example.catalog.support.PatchBuilder.patch;
import static com.example.catalog.support.TestData.NOW;
import static com.example.catalog.support.TestData.RUNNING;
import static com.example.catalog.support.TestData.SKU;
import static com.example.catalog.support.TestData.TOPS;
import static com.example.catalog.support.TestData.fields;
import static com.example.catalog.support.TestData.usd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.catalog.application.command.AttributesPatch;
import com.example.catalog.application.command.CreateProductCommand;
import com.example.catalog.application.command.PatchField;
import com.example.catalog.application.command.ProductFields;
import com.example.catalog.application.command.UpdateProductCommand;
import com.example.catalog.domain.exception.DomainValidationException;
import com.example.catalog.domain.exception.DuplicateSkuException;
import com.example.catalog.domain.exception.InvalidCategoryReferenceException;
import com.example.catalog.domain.exception.ProductNotFoundException;
import com.example.catalog.domain.exception.VersionMismatchException;
import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.model.ProductStatus;
import com.example.catalog.domain.model.Sku;
import com.example.catalog.support.InMemoryCategoryRepository;
import com.example.catalog.support.InMemoryProductRepository;
import com.example.catalog.support.MutableClock;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductCommandServiceTest {

    private final InMemoryProductRepository products = new InMemoryProductRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository(RUNNING, TOPS);
    private final MutableClock clock = new MutableClock(NOW);
    private final ProductCommandService service =
            new ProductCommandService(products, categories, UUID::randomUUID, clock);

    private Product existing;

    @BeforeEach
    void createExisting() {
        existing = service.create(new CreateProductCommand(SKU, fields()));
        clock.advance(Duration.ofMinutes(5));
    }

    @Test
    void createResolvesCategoryAndStampsTimes() {
        assertThat(existing.details().category()).isEqualTo(RUNNING);
        assertThat(existing.version()).isZero();
        assertThat(existing.createdAt()).isEqualTo(NOW);
    }

    @Test
    void createTruncatesTimestampsToDatabasePrecision() {
        clock.advance(Duration.ofNanos(1_234_567));

        Product product = service.create(new CreateProductCommand(Sku.of("NK-TRUNC-1"), fields()));

        assertThat(product.createdAt().getNano() % 1_000).isZero();
    }

    @Test
    void createRejectsUnknownCategory() {
        ProductFields unknownCategory = new ProductFields("Shoe", null, "no-such-category", usd("10"), ProductStatus.ACTIVE, Map.of());

        assertThatThrownBy(() -> service.create(new CreateProductCommand(Sku.of("NK-NEW-1"), unknownCategory)))
                .isInstanceOf(InvalidCategoryReferenceException.class);
    }

    @Test
    void createRejectsDuplicateSku() {
        assertThatThrownBy(() -> service.create(new CreateProductCommand(SKU, fields())))
                .isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void replaceUpdatesFieldsAndBumpsVersion() {
        ProductFields replacement = new ProductFields("Pegasus 41 Trail", null, TOPS.slug(), usd("139.00"), ProductStatus.INACTIVE, Map.of());

        Product updated = service.update(new UpdateProductCommand(existing.id(), SKU, replacement, OptionalLong.of(0)));

        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.details().name()).isEqualTo("Pegasus 41 Trail");
        assertThat(updated.details().category()).isEqualTo(TOPS);
        assertThat(updated.updatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void replaceRejectsStaleIfMatchBeforeWriting() {
        assertThatThrownBy(() -> service.update(new UpdateProductCommand(existing.id(), SKU, fields(), OptionalLong.of(7))))
                .isInstanceOf(VersionMismatchException.class);
        assertThat(products.stored(existing.id()).version()).isZero();
    }

    @Test
    void replaceCannotChangeSku() {
        assertThatThrownBy(() -> service.update(new UpdateProductCommand(existing.id(), Sku.of("NK-OTHER"), fields(), OptionalLong.empty())))
                .isInstanceOf(DomainValidationException.class)
                .extracting("field").isEqualTo("sku");
    }

    @Test
    void writesToUnknownProductAreNotFound() {
        ProductId unknown = ProductId.of(UUID.randomUUID());

        assertThatThrownBy(() -> service.update(new UpdateProductCommand(unknown, SKU, fields(), OptionalLong.empty())))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> service.delete(unknown)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void deleteArchivesAndIsIdempotent() {
        service.delete(existing.id());
        service.delete(existing.id());

        Product stored = products.stored(existing.id());
        assertThat(stored.isArchived()).isTrue();
        assertThat(stored.version()).isEqualTo(1);
    }

    @Test
    void archivedProductCannotBeChanged() {
        service.delete(existing.id());

        assertThatThrownBy(() -> service.patch(patch(existing.id()).name(PatchField.set("Revived")).build()))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Nested
    class Patch {

        @Test
        void absentFieldsKeepTheirValues() {
            Product patched = service.patch(patch(existing.id()).name(PatchField.set("Pegasus 42")).build());

            assertThat(patched.details().name()).isEqualTo("Pegasus 42");
            assertThat(patched.details().description()).isEqualTo(existing.details().description());
            assertThat(patched.details().price()).isEqualTo(existing.details().price());
            assertThat(patched.details().attributes()).isEqualTo(existing.details().attributes());
        }

        @Test
        void nullClearsAnOptionalField() {
            Product patched = service.patch(patch(existing.id()).description(PatchField.clear()).build());

            assertThat(patched.details().description()).isNull();
        }

        @Test
        void nullOnARequiredFieldIsRejected() {
            assertThatThrownBy(() -> service.patch(patch(existing.id()).name(PatchField.clear()).build()))
                    .isInstanceOf(DomainValidationException.class)
                    .extracting("field").isEqualTo("name");
            assertThatThrownBy(() -> service.patch(patch(existing.id()).price(PatchField.clear()).build()))
                    .isInstanceOf(DomainValidationException.class)
                    .extracting("field").isEqualTo("price");
        }

        @Test
        void mergedResultIsValidatedLikeACreate() {
            assertThatThrownBy(() -> service.patch(patch(existing.id()).price(PatchField.set(new BigDecimal("-1"))).build()))
                    .isInstanceOf(DomainValidationException.class);
            assertThatThrownBy(() -> service.patch(patch(existing.id()).status(PatchField.set(ProductStatus.ARCHIVED)).build()))
                    .isInstanceOf(DomainValidationException.class)
                    .extracting("field").isEqualTo("status");
        }

        @Test
        void priceChangeKeepsCurrency() {
            Product patched = service.patch(patch(existing.id()).price(PatchField.set(new BigDecimal("99.5"))).build());

            assertThat(patched.details().price()).isEqualTo(usd("99.50"));
        }

        @Test
        void attributesMergeKeyByKey() {
            AttributesPatch change = new AttributesPatch(Map.of("surface", "trail"), Set.of("gender"));

            Product patched = service.patch(patch(existing.id()).attributes(PatchField.set(change)).build());

            assertThat(patched.details().attributes()).containsExactlyInAnyOrderEntriesOf(Map.of("colour", "black", "surface", "trail"));
        }

        @Test
        void categoryIsResolvedFromSlug() {
            Product patched = service.patch(patch(existing.id()).category(PatchField.set(TOPS.slug())).build());

            assertThat(patched.details().category()).isEqualTo(TOPS);
            assertThatThrownBy(() -> service.patch(patch(existing.id()).category(PatchField.set("nope")).build()))
                    .isInstanceOf(InvalidCategoryReferenceException.class);
        }

        @Test
        void staleIfMatchIsRejected() {
            assertThatThrownBy(() -> service.patch(patch(existing.id()).expectedVersion(3).name(PatchField.set("x")).build()))
                    .isInstanceOf(VersionMismatchException.class)
                    .extracting("currentVersion").isEqualTo(0L);
        }
    }
}
