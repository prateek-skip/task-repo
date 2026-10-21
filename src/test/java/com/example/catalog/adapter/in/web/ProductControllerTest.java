package com.example.catalog.adapter.in.web;

import static com.example.catalog.support.TestData.NOW;
import static com.example.catalog.support.TestData.PRODUCT_ID;
import static com.example.catalog.support.TestData.RUNNING;
import static com.example.catalog.support.TestData.SKU;
import static com.example.catalog.support.TestData.TOPS;
import static com.example.catalog.support.TestData.product;
import static com.example.catalog.support.TestData.usd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.catalog.application.command.PatchField;
import com.example.catalog.application.command.PatchProductCommand;
import com.example.catalog.application.command.UpdateProductCommand;
import com.example.catalog.application.port.in.CreateProductUseCase;
import com.example.catalog.application.port.in.DeleteProductUseCase;
import com.example.catalog.application.port.in.GetProductUseCase;
import com.example.catalog.application.port.in.ListCategoriesUseCase;
import com.example.catalog.application.port.in.PatchProductUseCase;
import com.example.catalog.application.port.in.SearchProductsUseCase;
import com.example.catalog.application.port.in.UpdateProductUseCase;
import com.example.catalog.application.query.SearchProductsQuery;
import com.example.catalog.domain.exception.ConcurrentUpdateException;
import com.example.catalog.domain.exception.DuplicateSkuException;
import com.example.catalog.domain.exception.InvalidCategoryReferenceException;
import com.example.catalog.domain.exception.ProductNotFoundException;
import com.example.catalog.domain.exception.VersionMismatchException;
import com.example.catalog.domain.model.ProductStatus;
import com.example.catalog.domain.query.PageResult;
import com.example.catalog.domain.query.ProductSearchCriteria;
import com.example.catalog.domain.query.ProductSummary;
import com.example.catalog.domain.query.SortOption;
import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {ProductController.class, CategoryController.class})
@Import({ProductWebMapper.class, ProductPatchMapper.class, CursorCodec.class})
class ProductControllerTest {

    private static final String PRODUCT_URL = "/api/v1/products/" + PRODUCT_ID;
    private static final String VALID_BODY = """
            {"sku": "NK-PEG-41-BLK", "name": "Pegasus 41", "description": "Responsive daily trainer.",
             "category": "running-shoes", "price": "129.99", "attributes": {"colour": "black"}}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreateProductUseCase createProduct;
    @MockitoBean
    private UpdateProductUseCase updateProduct;
    @MockitoBean
    private PatchProductUseCase patchProduct;
    @MockitoBean
    private DeleteProductUseCase deleteProduct;
    @MockitoBean
    private GetProductUseCase getProduct;
    @MockitoBean
    private SearchProductsUseCase searchProducts;
    @MockitoBean
    private ListCategoriesUseCase listCategories;

    @Test
    void createReturnsLocationETagAndBody() throws Exception {
        when(createProduct.create(any())).thenReturn(product());

        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(PRODUCT_URL)))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.price").value("129.99"))
                .andExpect(jsonPath("$.category").value("running-shoes"));
    }

    @Test
    void invalidBodyReportsEveryBadField() throws Exception {
        String body = """
                {"sku": "lowercase", "name": " ", "category": "running-shoes", "price": "-1"}
                """;

        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:catalog:problem:validation"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("sku", "name", "price")));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCarriesETagAndAnswersConditionalRequestsWith304() throws Exception {
        when(getProduct.get(PRODUCT_ID)).thenReturn(product());

        mvc.perform(get(PRODUCT_URL))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().string("Cache-Control", "max-age=30, public"));

        mvc.perform(get(PRODUCT_URL).header("If-None-Match", "\"0\""))
                .andExpect(status().isNotModified());
    }

    @Test
    void unknownProductIsAProblem404() throws Exception {
        when(getProduct.get(PRODUCT_ID)).thenThrow(new ProductNotFoundException(PRODUCT_ID));

        mvc.perform(get(PRODUCT_URL))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:catalog:problem:not-found"));
    }

    @Test
    void nonUuidIdIsABadRequest() throws Exception {
        mvc.perform(get("/api/v1/products/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void replacePassesIfMatchVersionToTheUseCase() throws Exception {
        when(updateProduct.update(any())).thenReturn(product());

        mvc.perform(put(PRODUCT_URL).header("If-Match", "\"3\"").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateProductCommand> command = ArgumentCaptor.forClass(UpdateProductCommand.class);
        verify(updateProduct).update(command.capture());
        assertThat(command.getValue().expectedVersion()).isEqualTo(OptionalLong.of(3));
        assertThat(command.getValue().fields().status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void staleIfMatchIs412WithCurrentVersion() throws Exception {
        when(updateProduct.update(any())).thenThrow(new VersionMismatchException(PRODUCT_ID, 3, 5));

        mvc.perform(put(PRODUCT_URL).header("If-Match", "\"3\"").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.currentVersion").value(5));
    }

    @Test
    void malformedIfMatchIs412() throws Exception {
        mvc.perform(put(PRODUCT_URL).header("If-Match", "W/\"3\"").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void patchRequiresMergePatchMediaType() throws Exception {
        mvc.perform(patch(PRODUCT_URL).contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"x\"}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void patchKeepsAbsentAndNullDistinct() throws Exception {
        when(patchProduct.patch(any())).thenReturn(product());

        mvc.perform(patch(PRODUCT_URL)
                        .contentType(ProductController.MERGE_PATCH_JSON)
                        .content("{\"name\": \"Pegasus 42\", \"description\": null}"))
                .andExpect(status().isOk());

        ArgumentCaptor<PatchProductCommand> command = ArgumentCaptor.forClass(PatchProductCommand.class);
        verify(patchProduct).patch(command.capture());
        assertThat(command.getValue().name()).isEqualTo(PatchField.set("Pegasus 42"));
        assertThat(command.getValue().description()).isEqualTo(PatchField.clear());
        assertThat(command.getValue().price()).isEqualTo(PatchField.unchanged());
    }

    @Test
    void patchCannotChangeSku() throws Exception {
        mvc.perform(patch(PRODUCT_URL).contentType(ProductController.MERGE_PATCH_JSON).content("{\"sku\": \"NK-NEW\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sku"));
    }

    @Test
    void duplicateSkuAndLostRacesAre409() throws Exception {
        when(createProduct.create(any())).thenThrow(new DuplicateSkuException(SKU));
        when(updateProduct.update(any())).thenThrow(new ConcurrentUpdateException(PRODUCT_ID));

        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:catalog:problem:duplicate-sku"));
        mvc.perform(put(PRODUCT_URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:catalog:problem:concurrent-update"));
    }

    @Test
    void unknownCategoryInBodyIs422OnTheCategoryField() throws Exception {
        when(createProduct.create(any())).thenThrow(new InvalidCategoryReferenceException("running-shoes"));

        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("category"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete(PRODUCT_URL)).andExpect(status().isNoContent());

        verify(deleteProduct).delete(PRODUCT_ID);
    }

    @Test
    void listMapsParametersAndEncodesNextCursor() throws Exception {
        ProductSummary row = new ProductSummary(PRODUCT_ID, SKU, "Pegasus 41", "running-shoes", usd("129.99"), ProductStatus.ACTIVE, NOW);
        ProductSearchCriteria criteria = ProductSearchCriteria.builder().sort(SortOption.PRICE_ASC).limit(1).build();
        when(searchProducts.search(any())).thenReturn(PageResult.fromOverfetch(List.of(row, row), 1, criteria::cursorAfter));

        mvc.perform(get("/api/v1/products")
                        .param("category", "running-shoes")
                        .param("minPrice", "50")
                        .param("maxPrice", "150")
                        .param("sort", "price_asc")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sku").value("NK-PEG-41-BLK"))
                .andExpect(jsonPath("$.items[0].description").doesNotExist())
                .andExpect(jsonPath("$.limit").value(1))
                .andExpect(jsonPath("$.nextCursor").isString());

        ArgumentCaptor<SearchProductsQuery> query = ArgumentCaptor.forClass(SearchProductsQuery.class);
        verify(searchProducts).search(query.capture());
        assertThat(query.getValue()).isEqualTo(
                new SearchProductsQuery("running-shoes", new BigDecimal("50"), new BigDecimal("150"), SortOption.PRICE_ASC, 1, null));
    }

    @Test
    void lastPageHasNoCursor() throws Exception {
        when(searchProducts.search(any())).thenReturn(new PageResult<>(List.of(), 20, null));

        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void invalidListParametersAre400() throws Exception {
        mvc.perform(get("/api/v1/products").param("limit", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
        mvc.perform(get("/api/v1/products").param("sort", "popular"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
        mvc.perform(get("/api/v1/products").param("cursor", "garbage!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:catalog:problem:invalid-cursor"));
    }

    @Test
    void listsCategories() throws Exception {
        when(listCategories.listCategories()).thenReturn(List.of(RUNNING, TOPS));

        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("running-shoes"))
                .andExpect(jsonPath("$[1].name").value("Tops"));
    }
}
