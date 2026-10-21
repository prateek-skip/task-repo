package com.example.catalog.application.port.in;

import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.model.ProductId;

public interface GetProductUseCase {

    Product get(ProductId id);
}
