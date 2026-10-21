package com.example.catalog.domain.model;

import static com.example.catalog.support.TestData.RUNNING;
import static com.example.catalog.support.TestData.usd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.catalog.domain.exception.DomainValidationException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ProductDetailsTest {

    @Test
    void trimsNameAndStoresBlankDescriptionAsAbsent() {
        ProductDetails details = new ProductDetails("  Pegasus  ", "   ", RUNNING, usd("10"), ProductStatus.ACTIVE, null);

        assertThat(details.name()).isEqualTo("Pegasus");
        assertThat(details.description()).isNull();
        assertThat(details.attributes()).isEmpty();
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new ProductDetails(" ", null, RUNNING, usd("10"), ProductStatus.ACTIVE, null))
                .isInstanceOf(DomainValidationException.class)
                .extracting("field").isEqualTo("name");
    }

    @Test
    void rejectsUnsupportedCurrency() {
        Money euros = Money.of(BigDecimal.TEN, "EUR");

        assertThatThrownBy(() -> new ProductDetails("Pegasus", null, RUNNING, euros, ProductStatus.ACTIVE, null))
                .isInstanceOf(DomainValidationException.class)
                .extracting("field").isEqualTo("currency");
    }

    @Test
    void rejectsTooManyAttributes() {
        Map<String, String> attributes = new HashMap<>();
        IntStream.rangeClosed(0, ProductDetails.MAX_ATTRIBUTES).forEach(i -> attributes.put("key" + i, "value"));

        assertThatThrownBy(() -> new ProductDetails("Pegasus", null, RUNNING, usd("10"), ProductStatus.ACTIVE, attributes))
                .isInstanceOf(DomainValidationException.class)
                .extracting("field").isEqualTo("attributes");
    }

    @Test
    void copiesAttributesSoCallersCannotMutateThem() {
        Map<String, String> attributes = new HashMap<>(Map.of("colour", "black"));
        ProductDetails details = new ProductDetails("Pegasus", null, RUNNING, usd("10"), ProductStatus.ACTIVE, attributes);

        attributes.put("colour", "red");

        assertThat(details.attributes()).containsEntry("colour", "black");
        assertThatThrownBy(() -> details.attributes().put("size", "10")).isInstanceOf(UnsupportedOperationException.class);
    }
}
