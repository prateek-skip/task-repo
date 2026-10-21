package com.example.catalog.application.command;

import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.model.Sku;
import java.util.OptionalLong;

/**
 * Full replacement. The SKU is carried so a replace that tries to change it is rejected instead
 * of silently ignored.
 *
 * @param expectedVersion the version the client last saw (from If-Match), if it sent one
 */
public record UpdateProductCommand(ProductId id, Sku sku, ProductFields fields, OptionalLong expectedVersion) {
}
