package com.example.catalog.config;

import com.example.catalog.adapter.out.cache.CacheSerialization;
import com.example.catalog.adapter.out.cache.RedisSearchCacheGeneration;
import com.example.catalog.adapter.out.cache.SearchCacheGeneration;
import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.query.PageResult;
import com.example.catalog.domain.query.ProductSummary;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.JavaType;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogCacheProperties.class)
public class CacheConfig {

    public static final String PRODUCTS = "products";
    public static final String PRODUCT_SEARCH = "product-search";

    // One cache per value type, each with its own serializer and TTL. Only these caches exist,
    // so a misspelt cache name fails at startup instead of creating an untyped cache.
    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connections, CatalogCacheProperties properties) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .prefixCacheNameWith("catalog:");
        return RedisCacheManager.builder(connections)
                .cacheDefaults(defaults)
                .withCacheConfiguration(PRODUCTS,
                        typed(defaults, CacheSerialization.type(Product.class), properties.productTtl()))
                .withCacheConfiguration(PRODUCT_SEARCH,
                        typed(defaults, CacheSerialization.type(PageResult.class, ProductSummary.class), properties.searchTtl()))
                .disableCreateOnMissingCache()
                .build();
    }

    @Bean
    SearchCacheGeneration searchCacheGeneration(StringRedisTemplate redis) {
        return new RedisSearchCacheGeneration(redis);
    }

    private static RedisCacheConfiguration typed(RedisCacheConfiguration defaults, JavaType type, Duration ttl) {
        return defaults
                .entryTtl(ttl)
                .serializeValuesWith(SerializationPair.fromSerializer(CacheSerialization.serializerFor(type)));
    }
}
