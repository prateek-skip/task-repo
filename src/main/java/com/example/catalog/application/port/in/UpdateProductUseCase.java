package com.example.catalog.application.port.in;

import com.example.catalog.application.command.UpdateProductCommand;
import com.example.catalog.domain.model.Product;

/** Full replacement of a product's editable fields. */
public interface UpdateProductUseCase {

    Product update(UpdateProductCommand command);
}
