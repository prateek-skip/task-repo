package com.example.catalog.adapter.in.web;

import com.example.catalog.adapter.in.web.dto.ProductPageResponse;
import com.example.catalog.adapter.in.web.dto.ProductRequest;
import com.example.catalog.adapter.in.web.dto.ProductResponse;
import com.example.catalog.application.port.in.CreateProductUseCase;
import com.example.catalog.application.port.in.DeleteProductUseCase;
import com.example.catalog.application.port.in.GetProductUseCase;
import com.example.catalog.application.port.in.PatchProductUseCase;
import com.example.catalog.application.port.in.SearchProductsUseCase;
import com.example.catalog.application.port.in.UpdateProductUseCase;
import com.example.catalog.application.query.SearchProductsQuery;
import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.query.ProductSearchCriteria;
import com.example.catalog.domain.query.SortOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** HTTP only: parse, delegate to one use case, shape the response. */
@RestController
@RequestMapping(path = "/api/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductController {

    static final String MERGE_PATCH_JSON = "application/merge-patch+json";

    private final CreateProductUseCase createProduct;
    private final UpdateProductUseCase updateProduct;
    private final PatchProductUseCase patchProduct;
    private final DeleteProductUseCase deleteProduct;
    private final GetProductUseCase getProduct;
    private final SearchProductsUseCase searchProducts;
    private final ProductWebMapper mapper;
    private final ProductPatchMapper patchMapper;
    private final CursorCodec cursorCodec;
    private final CacheControl publicCache;

    public ProductController(
            CreateProductUseCase createProduct,
            UpdateProductUseCase updateProduct,
            PatchProductUseCase patchProduct,
            DeleteProductUseCase deleteProduct,
            GetProductUseCase getProduct,
            SearchProductsUseCase searchProducts,
            ProductWebMapper mapper,
            ProductPatchMapper patchMapper,
            CursorCodec cursorCodec,
            @Value("${catalog.http.max-age:30s}") Duration maxAge) {
        this.createProduct = createProduct;
        this.updateProduct = updateProduct;
        this.patchProduct = patchProduct;
        this.deleteProduct = deleteProduct;
        this.getProduct = getProduct;
        this.searchProducts = searchProducts;
        this.mapper = mapper;
        this.patchMapper = patchMapper;
        this.cursorCodec = cursorCodec;
        // Short shared caching lets browsers and a CDN absorb repeat reads. Caches drop their copy
        // when the same URI sees a successful write, so a writer does not read its own stale data.
        this.publicCache = CacheControl.maxAge(maxAge).cachePublic();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        Product product = createProduct.create(mapper.toCreateCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(product.id().value())
                .toUri();
        return ResponseEntity.created(location)
                .eTag(ETags.of(product.version()))
                .body(mapper.toResponse(product));
    }

    // Returning the ETag is enough for conditional GETs: Spring answers a matching If-None-Match
    // with 304 and no body.
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> get(@PathVariable UUID id) {
        Product product = getProduct.get(ProductId.of(id));
        return ResponseEntity.ok()
                .eTag(ETags.of(product.version()))
                .cacheControl(publicCache)
                .body(mapper.toResponse(product));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> replace(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ProductRequest request) {
        Product product = updateProduct.update(
                mapper.toUpdateCommand(ProductId.of(id), request, ETags.expectedVersion(ifMatch)));
        return ResponseEntity.ok().eTag(ETags.of(product.version())).body(mapper.toResponse(product));
    }

    @PatchMapping(path = "/{id}", consumes = MERGE_PATCH_JSON)
    public ResponseEntity<ProductResponse> patch(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody Map<String, Object> patch) {
        Product product = patchProduct.patch(
                patchMapper.toCommand(ProductId.of(id), ETags.expectedVersion(ifMatch), patch));
        return ResponseEntity.ok().eTag(ETags.of(product.version())).body(mapper.toResponse(product));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteProduct.delete(ProductId.of(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<ProductPageResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal maxPrice,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "20") @Min(1) @Max(ProductSearchCriteria.MAX_LIMIT) int limit,
            @RequestParam(required = false) String cursor) {
        SearchProductsQuery query = new SearchProductsQuery(
                category,
                minPrice,
                maxPrice,
                SortOption.fromCode(sort),
                limit,
                cursor == null ? null : cursorCodec.decode(cursor));
        return ResponseEntity.ok()
                .cacheControl(publicCache)
                .body(mapper.toPageResponse(searchProducts.search(query)));
    }
}
