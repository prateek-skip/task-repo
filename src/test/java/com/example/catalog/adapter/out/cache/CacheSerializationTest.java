package com.example.catalog.adapter.out.cache;

import static com.example.catalog.support.TestData.NOW;
import static com.example.catalog.support.TestData.PRODUCT_ID;
import static com.example.catalog.support.TestData.SKU;
import static com.example.catalog.support.TestData.product;
import static com.example.catalog.support.TestData.usd;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.model.ProductStatus;
import com.example.catalog.domain.query.PageResult;
import com.example.catalog.domain.query.ProductSearchCriteria;
import com.example.catalog.domain.query.ProductSummary;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

/** The cached shapes must survive a round trip through Redis exactly. */
class CacheSerializationTest {

    @Test
    void productRoundTrips() {
        RedisSerializer<Object> serializer = CacheSerialization.serializerFor(CacheSerialization.type(Product.class));
        Product product = product();

        assertThat(serializer.deserialize(serializer.serialize(product))).isEqualTo(product);
    }

    @Test
    void searchPageRoundTripsIncludingCursor() {
        RedisSerializer<Object> serializer =
                CacheSerialization.serializerFor(CacheSerialization.type(PageResult.class, ProductSummary.class));
        ProductSummary row = new ProductSummary(PRODUCT_ID, SKU, "Pegasus 41", "running-shoes", usd("129.99"), ProductStatus.ACTIVE, NOW);
        PageResult<ProductSummary> page =
                PageResult.fromOverfetch(List.of(row, row), 1, ProductSearchCriteria.builder().limit(1).build()::cursorAfter);

        Object restored = serializer.deserialize(serializer.serialize(page));

        assertThat(restored).isEqualTo(page);
        assertThat(((PageResult<?>) restored).items().getFirst()).isInstanceOf(ProductSummary.class);
    }

    @Test
    void toleratesFieldsItDoesNotKnow() {
        RedisSerializer<Object> serializer = CacheSerialization.serializerFor(CacheSerialization.type(Product.class));
        String json = new String(serializer.serialize(product()), StandardCharsets.UTF_8);
        String fromNewerVersion = json.replaceFirst("\\{", "{\"addedLater\":true,");

        assertThat(serializer.deserialize(fromNewerVersion.getBytes(StandardCharsets.UTF_8))).isEqualTo(product());
    }
}
