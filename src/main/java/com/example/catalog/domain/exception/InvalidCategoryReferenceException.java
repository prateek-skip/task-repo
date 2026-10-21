package com.example.catalog.domain.exception;

/**
 * A product body refers to a category that does not exist. Distinct from
 * {@link CategoryNotFoundException}: the request itself is well formed and addressed to a valid
 * resource, it just cannot be processed.
 */
public class InvalidCategoryReferenceException extends RuntimeException {

    private final String slug;

    public InvalidCategoryReferenceException(String slug) {
        super("Category '" + slug + "' does not exist");
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }
}
