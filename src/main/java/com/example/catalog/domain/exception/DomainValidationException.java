package com.example.catalog.domain.exception;

/**
 * A value, or a combination of values, breaks a business rule. Carries the API field it concerns
 * so the caller can be told exactly which input to fix.
 */
public class DomainValidationException extends RuntimeException {

    private final String field;

    public DomainValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
