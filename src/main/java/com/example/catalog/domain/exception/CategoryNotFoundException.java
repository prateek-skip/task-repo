package com.example.catalog.domain.exception;

/** A listing was filtered by a category that does not exist. */
public class CategoryNotFoundException extends RuntimeException {

    private final String slug;

    public CategoryNotFoundException(String slug) {
        super("Category '" + slug + "' was not found");
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }
}
