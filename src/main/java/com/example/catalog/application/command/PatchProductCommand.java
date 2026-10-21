package com.example.catalog.application.command;

import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.util.OptionalLong;

/**
 * Partial update. Each field says whether to keep, clear or set it. The merged result is
 * validated exactly like a create, so a patch can never leave a product invalid.
 */
public record PatchProductCommand(
        ProductId id,
        OptionalLong expectedVersion,
        PatchField<String> name,
        PatchField<String> description,
        PatchField<String> categorySlug,
        PatchField<BigDecimal> price,
        PatchField<String> currency,
        PatchField<ProductStatus> status,
        PatchField<AttributesPatch> attributes) {
}
