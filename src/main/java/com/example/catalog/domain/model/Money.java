package com.example.catalog.domain.model;

import com.example.catalog.domain.exception.DomainValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/** A non-negative amount in one currency. Exact decimal arithmetic only; never floating point. */
public record Money(BigDecimal amount, Currency currency) {

    public static final int SCALE = 2;

    // Matches the numeric(12,2) column: ten integer digits.
    private static final BigDecimal UPPER_BOUND = new BigDecimal("10000000000");

    public Money {
        if (amount == null) {
            throw new DomainValidationException("price", "must not be null");
        }
        if (currency == null) {
            throw new DomainValidationException("currency", "must not be null");
        }
        if (amount.signum() < 0) {
            throw new DomainValidationException("price", "must not be negative");
        }
        // Reject rather than round: silently changing a price is worse than refusing it.
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new DomainValidationException("price", "must have at most " + SCALE + " decimal places");
        }
        if (amount.compareTo(UPPER_BOUND) >= 0) {
            throw new DomainValidationException("price", "must be less than " + UPPER_BOUND.toPlainString());
        }
        // BigDecimal equality is scale-sensitive (10.0 != 10.00), so every amount gets one scale.
        amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        if (currencyCode == null) {
            throw new DomainValidationException("currency", "must not be null");
        }
        Currency currency;
        try {
            currency = Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException e) {
            throw new DomainValidationException("currency", "must be an ISO 4217 currency code");
        }
        return new Money(amount, currency);
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }
}
