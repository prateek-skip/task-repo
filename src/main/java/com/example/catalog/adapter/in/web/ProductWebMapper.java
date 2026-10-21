package com.example.catalog.adapter.in.web;

import com.example.catalog.adapter.in.web.dto.CategoryResponse;
import com.example.catalog.adapter.in.web.dto.ProductPageResponse;
import com.example.catalog.adapter.in.web.dto.ProductRequest;
import com.example.catalog.adapter.in.web.dto.ProductResponse;
import com.example.catalog.adapter.in.web.dto.ProductSummaryResponse;
import com.example.catalog.application.command.CreateProductCommand;
import com.example.catalog.application.command.ProductFields;
import com.example.catalog.application.command.UpdateProductCommand;
import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.model.Money;
import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.model.ProductDetails;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.model.ProductStatus;
import com.example.catalog.domain.model.Sku;
import com.example.catalog.domain.query.PageResult;
import com.example.catalog.domain.query.ProductSummary;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/** Translation between the wire format and application types. No rules, only shape. */
@Component
public class ProductWebMapper {

    static final String DEFAULT_CURRENCY = "USD";
    static final ProductStatus DEFAULT_STATUS = ProductStatus.ACTIVE;

    private final CursorCodec cursorCodec;

    public ProductWebMapper(CursorCodec cursorCodec) {
        this.cursorCodec = cursorCodec;
    }

    public CreateProductCommand toCreateCommand(ProductRequest request) {
        return new CreateProductCommand(Sku.of(request.sku()), toFields(request));
    }

    public UpdateProductCommand toUpdateCommand(ProductId id, ProductRequest request, OptionalLong expectedVersion) {
        return new UpdateProductCommand(id, Sku.of(request.sku()), toFields(request), expectedVersion);
    }

    public ProductResponse toResponse(Product product) {
        ProductDetails details = product.details();
        return new ProductResponse(
                product.id().value(),
                product.sku().value(),
                details.name(),
                details.description(),
                details.category().slug(),
                details.price().amount().toPlainString(),
                details.price().currencyCode(),
                details.status().name(),
                details.attributes(),
                product.version(),
                product.createdAt(),
                product.updatedAt());
    }

    public ProductPageResponse toPageResponse(PageResult<ProductSummary> page) {
        return new ProductPageResponse(
                page.items().stream().map(ProductWebMapper::toSummaryResponse).toList(),
                page.limit(),
                page.next().map(cursorCodec::encode).orElse(null));
    }

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.slug(), category.name());
    }

    private static ProductSummaryResponse toSummaryResponse(ProductSummary summary) {
        return new ProductSummaryResponse(
                summary.id().value(),
                summary.sku().value(),
                summary.name(),
                summary.categorySlug(),
                summary.price().amount().toPlainString(),
                summary.price().currencyCode(),
                summary.status().name());
    }

    private static ProductFields toFields(ProductRequest request) {
        String currency = request.currency() == null ? DEFAULT_CURRENCY : request.currency();
        ProductStatus status = request.status() == null ? DEFAULT_STATUS : ProductStatus.valueOf(request.status());
        return new ProductFields(
                request.name(),
                request.description(),
                request.category(),
                Money.of(request.price(), currency),
                status,
                request.attributes());
    }
}
