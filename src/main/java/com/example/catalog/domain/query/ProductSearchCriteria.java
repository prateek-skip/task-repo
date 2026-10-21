package com.example.catalog.domain.query;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.catalog.domain.exception.DomainValidationException;
import com.example.catalog.domain.exception.InvalidCursorException;
import com.example.catalog.domain.model.CategoryId;
import com.example.catalog.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/** A validated listing request: filters, sort, page size and position. Built with {@link #builder()}. */
public final class ProductSearchCriteria {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private final CategoryId category;
    private final BigDecimal minPrice;
    private final BigDecimal maxPrice;
    private final SortOption sort;
    private final int limit;
    private final Cursor after;

    private ProductSearchCriteria(Builder builder) {
        this.category = builder.category;
        this.minPrice = builder.minPrice;
        this.maxPrice = builder.maxPrice;
        this.sort = builder.sort;
        this.limit = builder.limit;
        this.after = builder.after;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Listings show sellable products only. Inactive ones are reachable by id. */
    public ProductStatus status() {
        return ProductStatus.ACTIVE;
    }

    public Optional<CategoryId> category() {
        return Optional.ofNullable(category);
    }

    public Optional<BigDecimal> minPrice() {
        return Optional.ofNullable(minPrice);
    }

    public Optional<BigDecimal> maxPrice() {
        return Optional.ofNullable(maxPrice);
    }

    public SortOption sort() {
        return sort;
    }

    public int limit() {
        return limit;
    }

    public Optional<Cursor> after() {
        return Optional.ofNullable(after);
    }

    /** Cursor pointing just past the given row, i.e. at the start of the following page. */
    public Cursor cursorAfter(ProductSummary lastRow) {
        return new Cursor(sort.cursorValueOf(lastRow), lastRow.id(), fingerprint());
    }

    /**
     * Identifies the filters and sort a cursor belongs to. The limit is left out so a client can
     * change page size mid-scroll without losing its place.
     */
    public String fingerprint() {
        return sha256Prefix(filterKey());
    }

    /** Canonical identity of this exact page. Equivalent inputs such as 50 and 50.00 share a key. */
    public String cacheKey() {
        String position = after == null ? "-" : after.sortValue() + "," + after.lastId();
        return filterKey() + "|limit=" + limit + "|after=" + position;
    }

    private String filterKey() {
        return "category=" + (category == null ? "*" : category)
                + "|min=" + canonical(minPrice)
                + "|max=" + canonical(maxPrice)
                + "|sort=" + sort.code();
    }

    // Reusing a cursor with other filters would silently skip or repeat rows, so it is refused.
    private void requireCursorFits(Cursor cursor) {
        if (!cursor.fingerprint().equals(fingerprint())) {
            throw new InvalidCursorException(
                    "cursor was issued for different filters or sort; start again from the first page");
        }
        sort.requireValidCursorValue(cursor.sortValue());
    }

    private static String canonical(BigDecimal amount) {
        return amount == null ? "*" : amount.stripTrailingZeros().toPlainString();
    }

    private static String sha256Prefix(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, 12));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is guaranteed by the Java platform", e);
        }
    }

    public static final class Builder {

        private CategoryId category;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private SortOption sort = SortOption.DEFAULT;
        private int limit = DEFAULT_LIMIT;
        private Cursor after;

        private Builder() {
        }

        public Builder category(CategoryId category) {
            this.category = category;
            return this;
        }

        public Builder minPrice(BigDecimal minPrice) {
            this.minPrice = minPrice;
            return this;
        }

        public Builder maxPrice(BigDecimal maxPrice) {
            this.maxPrice = maxPrice;
            return this;
        }

        public Builder sort(SortOption sort) {
            this.sort = sort == null ? SortOption.DEFAULT : sort;
            return this;
        }

        public Builder limit(Integer limit) {
            this.limit = limit == null ? DEFAULT_LIMIT : limit;
            return this;
        }

        public Builder after(Cursor after) {
            this.after = after;
            return this;
        }

        public ProductSearchCriteria build() {
            if (limit < 1 || limit > MAX_LIMIT) {
                throw new DomainValidationException("limit", "must be between 1 and " + MAX_LIMIT);
            }
            requireNonNegative("minPrice", minPrice);
            requireNonNegative("maxPrice", maxPrice);
            if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
                throw new DomainValidationException("minPrice", "must not be greater than maxPrice");
            }
            ProductSearchCriteria criteria = new ProductSearchCriteria(this);
            if (after != null) {
                criteria.requireCursorFits(after);
            }
            return criteria;
        }

        private static void requireNonNegative(String field, BigDecimal value) {
            if (value != null && value.signum() < 0) {
                throw new DomainValidationException(field, "must not be negative");
            }
        }
    }
}
