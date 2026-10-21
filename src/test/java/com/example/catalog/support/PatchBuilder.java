package com.example.catalog.support;

import com.example.catalog.application.command.AttributesPatch;
import com.example.catalog.application.command.PatchField;
import com.example.catalog.application.command.PatchProductCommand;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.util.OptionalLong;

/** Every field starts as "unchanged", so a test names only the fields it is about. */
public final class PatchBuilder {

    private final ProductId id;
    private OptionalLong expectedVersion = OptionalLong.empty();
    private PatchField<String> name = PatchField.unchanged();
    private PatchField<String> description = PatchField.unchanged();
    private PatchField<String> category = PatchField.unchanged();
    private PatchField<BigDecimal> price = PatchField.unchanged();
    private PatchField<String> currency = PatchField.unchanged();
    private PatchField<ProductStatus> status = PatchField.unchanged();
    private PatchField<AttributesPatch> attributes = PatchField.unchanged();

    private PatchBuilder(ProductId id) {
        this.id = id;
    }

    public static PatchBuilder patch(ProductId id) {
        return new PatchBuilder(id);
    }

    public PatchBuilder expectedVersion(long version) {
        this.expectedVersion = OptionalLong.of(version);
        return this;
    }

    public PatchBuilder name(PatchField<String> name) {
        this.name = name;
        return this;
    }

    public PatchBuilder description(PatchField<String> description) {
        this.description = description;
        return this;
    }

    public PatchBuilder category(PatchField<String> category) {
        this.category = category;
        return this;
    }

    public PatchBuilder price(PatchField<BigDecimal> price) {
        this.price = price;
        return this;
    }

    public PatchBuilder status(PatchField<ProductStatus> status) {
        this.status = status;
        return this;
    }

    public PatchBuilder attributes(PatchField<AttributesPatch> attributes) {
        this.attributes = attributes;
        return this;
    }

    public PatchProductCommand build() {
        return new PatchProductCommand(id, expectedVersion, name, description, category, price, currency, status, attributes);
    }
}
