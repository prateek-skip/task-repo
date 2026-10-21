package com.example.catalog.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.catalog.domain.exception.DomainValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SkuTest {

    @ParameterizedTest
    @ValueSource(strings = {"ABC", "NK-PEG-41-BLK", "123-456"})
    void acceptsUppercaseAlphanumericWithDashes(String value) {
        assertThat(Sku.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "AB", "nk-peg", "NK PEG", "NK_PEG"})
    void rejectsAnythingElse(String value) {
        assertThatThrownBy(() -> Sku.of(value)).isInstanceOf(DomainValidationException.class);
    }
}
