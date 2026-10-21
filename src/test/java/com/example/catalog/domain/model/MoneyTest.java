package com.example.catalog.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.catalog.domain.exception.DomainValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalisesScaleSoEqualAmountsAreEqual() {
        assertThat(Money.of(new BigDecimal("10"), "USD")).isEqualTo(Money.of(new BigDecimal("10.00"), "USD"));
        assertThat(Money.of(new BigDecimal("10.5"), "USD").amount().toPlainString()).isEqualTo("10.50");
    }

    @Test
    void acceptsTrailingZerosBeyondTwoDecimals() {
        assertThat(Money.of(new BigDecimal("10.500"), "USD").amount()).isEqualByComparingTo("10.50");
    }

    @Test
    void rejectsMoreThanTwoSignificantDecimals() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("10.005"), "USD"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("decimal places");
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-0.01"), "USD"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void rejectsAmountsTheColumnCannotHold() {
        assertThat(Money.of(new BigDecimal("9999999999.99"), "USD")).isNotNull();
        assertThatThrownBy(() -> Money.of(new BigDecimal("10000000000"), "USD"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsUnknownCurrency() {
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, "XXZ"))
                .isInstanceOf(DomainValidationException.class)
                .extracting("field").isEqualTo("currency");
    }
}
