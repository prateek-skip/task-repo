package com.example.catalog.domain.model;

import java.util.Objects;
import java.util.UUID;

public record CategoryId(UUID value) {

    public CategoryId {
        Objects.requireNonNull(value, "value");
    }

    public static CategoryId of(UUID value) {
        return new CategoryId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
