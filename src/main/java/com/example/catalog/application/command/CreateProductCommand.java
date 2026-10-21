package com.example.catalog.application.command;

import com.example.catalog.domain.model.Sku;

public record CreateProductCommand(Sku sku, ProductFields fields) {
}
