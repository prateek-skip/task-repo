package com.example.catalog.application.port.in;

import com.example.catalog.application.command.PatchProductCommand;
import com.example.catalog.domain.model.Product;

/** Partial update: only the fields named in the command change. */
public interface PatchProductUseCase {

    Product patch(PatchProductCommand command);
}
