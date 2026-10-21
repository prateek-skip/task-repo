package com.example.catalog.adapter.in.web;

import com.example.catalog.application.command.AttributesPatch;
import com.example.catalog.application.command.PatchField;
import com.example.catalog.application.command.PatchProductCommand;
import com.example.catalog.domain.exception.DomainValidationException;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Reads an RFC 7396 JSON Merge Patch document into a command. The body arrives as a map so that a
 * missing key (keep the value) and an explicit null (remove it) stay distinguishable.
 */
@Component
public class ProductPatchMapper {

    private static final Set<String> PATCHABLE_FIELDS =
            Set.of("name", "description", "category", "price", "currency", "status", "attributes");

    public PatchProductCommand toCommand(ProductId id, OptionalLong expectedVersion, Map<String, Object> patch) {
        rejectUnknownFields(patch);
        return new PatchProductCommand(
                id,
                expectedVersion,
                text(patch, "name"),
                text(patch, "description"),
                text(patch, "category"),
                decimal(patch, "price"),
                text(patch, "currency"),
                status(patch),
                attributes(patch));
    }

    // Unknown fields are refused rather than ignored: a typo would otherwise look like success.
    private static void rejectUnknownFields(Map<String, Object> patch) {
        for (String field : patch.keySet()) {
            if (field.equals("sku")) {
                throw new DomainValidationException("sku", "cannot be changed");
            }
            if (!PATCHABLE_FIELDS.contains(field)) {
                throw new DomainValidationException(field, "is not a patchable field");
            }
        }
    }

    private static PatchField<String> text(Map<String, Object> patch, String field) {
        if (!patch.containsKey(field)) {
            return PatchField.unchanged();
        }
        Object value = patch.get(field);
        if (value == null) {
            return PatchField.clear();
        }
        if (value instanceof String text) {
            return PatchField.set(text);
        }
        throw new DomainValidationException(field, "must be a string");
    }

    // Accepts "129.99" or 129.99. Numbers go through their decimal text, never through double
    // arithmetic, so the amount the client wrote is the amount stored.
    private static PatchField<BigDecimal> decimal(Map<String, Object> patch, String field) {
        if (!patch.containsKey(field)) {
            return PatchField.unchanged();
        }
        Object value = patch.get(field);
        if (value == null) {
            return PatchField.clear();
        }
        if (value instanceof String || value instanceof Number) {
            try {
                return PatchField.set(new BigDecimal(value.toString()));
            } catch (NumberFormatException e) {
                // reported below
            }
        }
        throw new DomainValidationException(field, "must be a decimal number");
    }

    private static PatchField<ProductStatus> status(Map<String, Object> patch) {
        PatchField<String> raw = text(patch, "status");
        try {
            return raw.map(ProductStatus::valueOf);
        } catch (IllegalArgumentException e) {
            throw new DomainValidationException("status", "must be ACTIVE or INACTIVE");
        }
    }

    private static PatchField<AttributesPatch> attributes(Map<String, Object> patch) {
        if (!patch.containsKey("attributes")) {
            return PatchField.unchanged();
        }
        Object value = patch.get("attributes");
        if (value == null) {
            return PatchField.clear();
        }
        if (!(value instanceof Map<?, ?> entries)) {
            throw new DomainValidationException("attributes", "must be an object");
        }
        Map<String, String> upserts = new HashMap<>();
        Set<String> removals = new HashSet<>();
        entries.forEach((key, entry) -> {
            String name = String.valueOf(key);
            if (entry == null) {
                removals.add(name);
            } else if (entry instanceof String text) {
                upserts.put(name, text);
            } else {
                throw new DomainValidationException("attributes." + name, "must be a string or null");
            }
        });
        return PatchField.set(new AttributesPatch(upserts, removals));
    }
}
