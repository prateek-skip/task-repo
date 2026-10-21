package com.example.catalog.adapter.in.web.dto;

import com.example.catalog.domain.model.ProductDetails;
import com.example.catalog.domain.model.Sku;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Body of create and full replace. The constraints reuse the domain's constants so the rules
 * cannot drift apart. They report every bad field at once and document the contract in OpenAPI;
 * the domain still re-checks, because a patch never passes through this type.
 *
 * <p>{@code currency} defaults to USD and {@code status} to ACTIVE when omitted.
 */
public record ProductRequest(
        @NotNull @Pattern(regexp = Sku.FORMAT) String sku,
        @NotBlank @Size(max = ProductDetails.NAME_MAX_LENGTH) String name,
        @Size(max = ProductDetails.DESCRIPTION_MAX_LENGTH) String description,
        @NotBlank String category,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Pattern(regexp = "^(ACTIVE|INACTIVE)$") String status,
        @Size(max = ProductDetails.MAX_ATTRIBUTES) Map<String, String> attributes) {
}
