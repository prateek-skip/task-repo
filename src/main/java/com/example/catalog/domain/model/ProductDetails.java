package com.example.catalog.domain.model;

import com.example.catalog.domain.exception.DomainValidationException;
import java.util.Collections;
import java.util.Currency;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Everything about a product that a client may change. Create, replace and patch all end by
 * building one of these, so the three write paths share a single set of rules.
 */
public record ProductDetails(
        String name,
        String description,
        Category category,
        Money price,
        ProductStatus status,
        Map<String, String> attributes) {

    public static final int NAME_MAX_LENGTH = 255;
    public static final int DESCRIPTION_MAX_LENGTH = 5000;
    public static final int MAX_ATTRIBUTES = 50;
    public static final int ATTRIBUTE_KEY_MAX_LENGTH = 64;
    public static final int ATTRIBUTE_VALUE_MAX_LENGTH = 255;

    // Single currency for now. Money already carries its currency, so adding one is a code
    // change here rather than a schema migration.
    private static final Set<Currency> SUPPORTED_CURRENCIES = Set.of(Currency.getInstance("USD"));

    public ProductDetails {
        name = requireText("name", name, NAME_MAX_LENGTH);
        description = optionalText("description", description, DESCRIPTION_MAX_LENGTH);
        if (category == null) {
            throw new DomainValidationException("category", "must not be null");
        }
        if (price == null) {
            throw new DomainValidationException("price", "must not be null");
        }
        if (!SUPPORTED_CURRENCIES.contains(price.currency())) {
            throw new DomainValidationException("currency", "must be one of " + SUPPORTED_CURRENCIES);
        }
        if (status == null) {
            throw new DomainValidationException("status", "must not be null");
        }
        attributes = normaliseAttributes(attributes);
    }

    public ProductDetails withStatus(ProductStatus newStatus) {
        return new ProductDetails(name, description, category, price, newStatus, attributes);
    }

    private static String requireText(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field, "must not be blank");
        }
        String trimmed = value.strip();
        if (trimmed.length() > maxLength) {
            throw new DomainValidationException(field, "must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    // Blank and absent mean the same thing, so only one of them is ever stored.
    private static String optionalText(String field, String value, int maxLength) {
        return value == null || value.isBlank() ? null : requireText(field, value, maxLength);
    }

    private static Map<String, String> normaliseAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        if (attributes.size() > MAX_ATTRIBUTES) {
            throw new DomainValidationException("attributes", "must have at most " + MAX_ATTRIBUTES + " entries");
        }
        // Sorted, unmodifiable copy: stable output order and no aliasing with the caller's map.
        Map<String, String> copy = new TreeMap<>();
        attributes.forEach((key, value) -> {
            if (key == null || key.isBlank() || key.length() > ATTRIBUTE_KEY_MAX_LENGTH) {
                throw new DomainValidationException("attributes",
                        "keys must be non-blank and at most " + ATTRIBUTE_KEY_MAX_LENGTH + " characters");
            }
            if (value == null || value.length() > ATTRIBUTE_VALUE_MAX_LENGTH) {
                throw new DomainValidationException("attributes." + key,
                        "must be present and at most " + ATTRIBUTE_VALUE_MAX_LENGTH + " characters");
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }
}
