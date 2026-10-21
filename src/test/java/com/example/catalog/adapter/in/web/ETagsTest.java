package com.example.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.ErrorResponseException;

class ETagsTest {

    @Test
    void absentOrWildcardMeansNoPrecondition() {
        assertThat(ETags.expectedVersion(null)).isEmpty();
        assertThat(ETags.expectedVersion("*")).isEmpty();
    }

    @Test
    void readsTheVersionFromAStrongTag() {
        assertThat(ETags.expectedVersion("\"12\"")).hasValue(12);
        assertThat(ETags.of(12)).isEqualTo("\"12\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12", "W/\"12\"", "\"abc\"", "\"1\", \"2\""})
    void anyOtherTagFailsThePrecondition(String header) {
        assertThatThrownBy(() -> ETags.expectedVersion(header))
                .isInstanceOf(ErrorResponseException.class)
                .extracting(e -> ((ErrorResponseException) e).getStatusCode().value()).isEqualTo(412);
    }
}
