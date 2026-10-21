package com.example.catalog.domain.query;

import com.example.catalog.domain.exception.DomainValidationException;
import com.example.catalog.domain.exception.InvalidCursorException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The orderings a listing supports. Each names its sort key and direction and knows how to read
 * that key from a row, so pagination works the same way for every sort.
 *
 * <p>Every ordering is completed by product id. That makes it total: a cursor points at exactly
 * one position even when many products share a price or a creation time.
 */
public enum SortOption {

    PRICE_ASC("price_asc", Key.PRICE, Direction.ASCENDING),
    PRICE_DESC("price_desc", Key.PRICE, Direction.DESCENDING),
    NEWEST("newest", Key.CREATED_AT, Direction.DESCENDING);

    public static final SortOption DEFAULT = NEWEST;

    public enum Key { PRICE, CREATED_AT }

    public enum Direction { ASCENDING, DESCENDING }

    private final String code;
    private final Key key;
    private final Direction direction;

    SortOption(String code, Key key, Direction direction) {
        this.code = code;
        this.key = key;
        this.direction = direction;
    }

    public String code() {
        return code;
    }

    public Key key() {
        return key;
    }

    public Direction direction() {
        return direction;
    }

    public static SortOption fromCode(String code) {
        return Arrays.stream(values())
                .filter(option -> option.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new DomainValidationException("sort", "must be one of " + codes()));
    }

    /** The row's sort-key value, in the canonical text form a cursor stores. */
    public String cursorValueOf(ProductSummary row) {
        return switch (key) {
            case PRICE -> row.price().amount().toPlainString();
            case CREATED_AT -> row.createdAt().toString();
        };
    }

    /** Rejects a cursor value that this ordering could not have produced. */
    void requireValidCursorValue(String value) {
        try {
            switch (key) {
                case PRICE -> new BigDecimal(value);
                case CREATED_AT -> Instant.parse(value);
            }
        } catch (RuntimeException e) {
            throw new InvalidCursorException("cursor is malformed");
        }
    }

    private static String codes() {
        return Arrays.stream(values()).map(SortOption::code).collect(Collectors.joining(", "));
    }
}
