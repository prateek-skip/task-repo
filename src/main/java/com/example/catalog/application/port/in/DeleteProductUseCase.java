package com.example.catalog.application.port.in;

import com.example.catalog.domain.model.ProductId;

public interface DeleteProductUseCase {

    void delete(ProductId id);
}
