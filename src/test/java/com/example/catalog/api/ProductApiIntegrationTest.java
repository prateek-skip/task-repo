package com.example.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.catalog.config.CacheConfig;
import com.example.catalog.support.PostgresTestcontainer;
import com.example.catalog.support.RedisTestcontainer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** The whole stack: HTTP, use cases, Redis and PostgreSQL, wired exactly as in production. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, RedisTestcontainer.class})
class ProductApiIntegrationTest {

    private static final String MERGE_PATCH = "application/merge-patch+json";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CacheManager caches;

    @Test
    void productLifecycleKeepsCacheAndDatabaseConsistent() throws Exception {
        String id = create("NK-LIFECYCLE-1", "running-shoes", "129.99");
        String url = "/api/v1/products/" + id;
        Cache productCache = caches.getCache(CacheConfig.PRODUCTS);

        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().string("ETag", "\"0\""));
        assertThat(productCache.get(id)).as("first read populates the cache").isNotNull();
        mvc.perform(get(url).header("If-None-Match", "\"0\"")).andExpect(status().isNotModified());

        mvc.perform(put(url).header("If-Match", "\"7\"").contentType(MediaType.APPLICATION_JSON).content(body("NK-LIFECYCLE-1", "Trail", "running-shoes", "139.00")))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(put(url).header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content(body("NK-LIFECYCLE-1", "Trail", "running-shoes", "139.00")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""));
        assertThat(productCache.get(id)).as("write evicts after commit").isNull();
        mvc.perform(get(url)).andExpect(jsonPath("$.name").value("Trail")).andExpect(jsonPath("$.price").value("139.00"));

        mvc.perform(patch(url).contentType(MERGE_PATCH).content("""
                        {"description": null, "attributes": {"colour": null, "surface": "trail"}}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.attributes.surface").value("trail"))
                .andExpect(jsonPath("$.attributes.colour").doesNotExist());

        mvc.perform(get("/api/v1/products").param("category", "running-shoes").param("sort", "price_asc").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')].price").value("139.00"));

        mvc.perform(delete(url)).andExpect(status().isNoContent());
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(delete(url)).andExpect(status().isNoContent());
    }

    @Test
    void listingSeesNewProductsImmediatelyDespiteCaching() throws Exception {
        mvc.perform(get("/api/v1/products").param("category", "jackets")).andExpect(status().isOk());

        String id = create("NK-FRESH-LIST-1", "jackets", "80.00");

        mvc.perform(get("/api/v1/products").param("category", "jackets"))
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')]").exists());
    }

    @Test
    void cursorPaginationWalksToTheEnd() throws Exception {
        for (int i = 0; i < 5; i++) {
            create("NK-PAGE-" + i, "equipment", "1" + i + ".00");
        }

        String first = mvc.perform(get("/api/v1/products").param("category", "equipment").param("sort", "price_asc").param("limit", "3"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.nextCursor");

        mvc.perform(get("/api/v1/products").param("category", "equipment").param("sort", "price_asc").param("limit", "3").param("cursor", cursor))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].price").value("13.00"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        mvc.perform(get("/api/v1/products").param("category", "tops").param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:catalog:problem:invalid-cursor"));
    }

    @Test
    void requestErrorsMapToTheDocumentedStatuses() throws Exception {
        create("NK-CONFLICT-1", "tops", "25.00");

        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body("NK-CONFLICT-1", "Again", "tops", "25.00")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body("NK-NO-CATEGORY", "Shoe", "hats", "25.00")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("category"));
        mvc.perform(get("/api/v1/products").param("category", "hats"))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoriesComeFromSeededReferenceData() throws Exception {
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].slug").value("accessories"));
    }

    private String create(String sku, String category, String price) throws Exception {
        String response = mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body(sku, "Product " + sku, category, price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private static String body(String sku, String name, String category, String price) {
        return """
                {"sku": "%s", "name": "%s", "description": "Integration test product", "category": "%s",
                 "price": "%s", "attributes": {"colour": "black"}}
                """.formatted(sku, name, category, price);
    }
}
