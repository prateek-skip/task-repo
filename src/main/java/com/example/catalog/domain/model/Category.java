package com.example.catalog.domain.model;

import java.util.Objects;

/** Reference data. The slug is the stable, human-readable identifier used in the API. */
public record Category(CategoryId id, String slug, String name) {

    public Category {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(name, "name");
    }
}
