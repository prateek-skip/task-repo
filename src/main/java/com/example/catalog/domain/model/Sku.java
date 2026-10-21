package com.example.catalog.domain.model;

import com.example.catalog.domain.exception.DomainValidationException;
import java.util.regex.Pattern;

/** Stock keeping unit: the merchant-facing identifier of a product. Unique and never changes. */
public record Sku(String value) {

    /** Exposed as a constant so request validation can reuse the exact same rule. */
    public static final String FORMAT = "^[A-Z0-9-]{3,64}$";

    private static final Pattern PATTERN = Pattern.compile(FORMAT);

    public Sku {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new DomainValidationException("sku", "must match " + FORMAT);
        }
    }

    public static Sku of(String value) {
        return new Sku(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
