package com.example.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.catalog.domain.exception.InvalidCursorException;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.query.Cursor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CursorCodecTest {

    private final CursorCodec codec = new CursorCodec();

    @Test
    void roundTripsAndIsUrlSafe() {
        Cursor cursor = new Cursor("2026-10-21T10:00:00.123456Z", ProductId.of(UUID.randomUUID()), "Ab_-x9");

        String token = codec.encode(cursor);

        assertThat(token).matches("[A-Za-z0-9_-]+");
        assertThat(codec.decode(token)).isEqualTo(cursor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not base64!", "dGhyZWV8cGFydHM", ""})
    void rejectsGarbage(String token) {
        assertThatThrownBy(() -> codec.decode(token)).isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void rejectsUnknownFormatVersion() {
        String token = Base64.getUrlEncoder().encodeToString(
                ("v0|1.00|" + UUID.randomUUID() + "|fp").getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(token)).isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void rejectsOversizedTokens() {
        assertThatThrownBy(() -> codec.decode("A".repeat(600))).isInstanceOf(InvalidCursorException.class);
    }
}
