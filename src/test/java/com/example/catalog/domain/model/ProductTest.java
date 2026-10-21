package com.example.catalog.domain.model;

import static com.example.catalog.support.TestData.NOW;
import static com.example.catalog.support.TestData.PRODUCT_ID;
import static com.example.catalog.support.TestData.SKU;
import static com.example.catalog.support.TestData.details;
import static com.example.catalog.support.TestData.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.catalog.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final Instant LATER = NOW.plusSeconds(60);

    @Test
    void newProductStartsAtVersionZeroWithMatchingTimestamps() {
        Product product = product();

        assertThat(product.version()).isZero();
        assertThat(product.createdAt()).isEqualTo(NOW);
        assertThat(product.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void cannotBeCreatedArchived() {
        assertThatThrownBy(() -> Product.create(PRODUCT_ID, SKU, details().withStatus(ProductStatus.ARCHIVED), NOW))
                .isInstanceOf(DomainValidationException.class)
                .extracting("field").isEqualTo("status");
    }

    @Test
    void updateKeepsIdentitySkuAndCreationTime() {
        Product updated = product().update(details().withStatus(ProductStatus.INACTIVE), LATER);

        assertThat(updated.id()).isEqualTo(PRODUCT_ID);
        assertThat(updated.sku()).isEqualTo(SKU);
        assertThat(updated.createdAt()).isEqualTo(NOW);
        assertThat(updated.updatedAt()).isEqualTo(LATER);
        assertThat(updated.details().status()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void archiveIsIdempotent() {
        Product archived = product().archive(LATER);

        assertThat(archived.isArchived()).isTrue();
        assertThat(archived.archive(LATER.plusSeconds(1))).isSameAs(archived);
    }

    @Test
    void archivedProductIsReadOnly() {
        Product archived = product().archive(LATER);

        assertThatThrownBy(() -> archived.update(details(), LATER)).isInstanceOf(IllegalStateException.class);
    }
}
