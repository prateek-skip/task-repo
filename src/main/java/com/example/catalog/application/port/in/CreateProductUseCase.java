package com.example.catalog.application.port.in;

import com.example.catalog.application.command.CreateProductCommand;
import com.example.catalog.domain.model.Product;

public interface CreateProductUseCase {

    Product create(CreateProductCommand command);
}
