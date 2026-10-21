package com.example.catalog.config;

import com.example.catalog.adapter.out.cache.CachingCategoryRepository;
import com.example.catalog.adapter.out.cache.CachingProductRepository;
import com.example.catalog.adapter.out.cache.CachingProductSearchRepository;
import com.example.catalog.adapter.out.cache.FailOpenCache;
import com.example.catalog.adapter.out.cache.SearchCacheGeneration;
import com.example.catalog.adapter.out.id.UuidV7Generator;
import com.example.catalog.adapter.out.persistence.JpaCategoryRepositoryAdapter;
import com.example.catalog.adapter.out.persistence.JpaProductRepositoryAdapter;
import com.example.catalog.adapter.out.persistence.JpaProductSearchAdapter;
import com.example.catalog.adapter.out.persistence.SpringDataCategoryRepository;
import com.example.catalog.adapter.out.persistence.SpringDataProductRepository;
import com.example.catalog.application.service.ProductCommandService;
import com.example.catalog.application.service.ProductQueryService;
import com.example.catalog.domain.port.CategoryRepository;
import com.example.catalog.domain.port.IdGenerator;
import com.example.catalog.domain.port.ProductRepository;
import com.example.catalog.domain.port.ProductSearchRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.util.Objects;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/**
 * Composition root. The only class that knows which adapter implements which port and in what
 * order the decorators stack. Each port has exactly one bean, so nothing else needs qualifiers,
 * and removing a cache layer is a one-line change here.
 */
@Configuration(proxyBeanMethods = false)
public class CatalogWiring {

    @Bean
    ProductRepository productRepository(
            SpringDataProductRepository rows, CacheManager caches, SearchCacheGeneration generation, MeterRegistry meters) {
        ProductRepository database = new JpaProductRepositoryAdapter(rows);
        return new CachingProductRepository(database, cache(caches, CacheConfig.PRODUCTS, meters), generation);
    }

    @Bean
    ProductSearchRepository productSearchRepository(
            EntityManagerFactory entityManagers, CacheManager caches, SearchCacheGeneration generation, MeterRegistry meters) {
        ProductSearchRepository database =
                new JpaProductSearchAdapter(SharedEntityManagerCreator.createSharedEntityManager(entityManagers));
        return new CachingProductSearchRepository(database, cache(caches, CacheConfig.PRODUCT_SEARCH, meters), generation);
    }

    @Bean
    CategoryRepository categoryRepository(SpringDataCategoryRepository rows, CatalogCacheProperties properties, Clock clock) {
        return new CachingCategoryRepository(new JpaCategoryRepositoryAdapter(rows), properties.categoryTtl(), clock);
    }

    @Bean
    IdGenerator idGenerator(Clock clock) {
        return new UuidV7Generator(clock);
    }

    @Bean
    ProductCommandService productCommandService(
            ProductRepository products, CategoryRepository categories, IdGenerator ids, Clock clock) {
        return new ProductCommandService(products, categories, ids, clock);
    }

    @Bean
    ProductQueryService productQueryService(
            ProductRepository products, ProductSearchRepository search, CategoryRepository categories) {
        return new ProductQueryService(products, search, categories);
    }

    private static FailOpenCache cache(CacheManager caches, String name, MeterRegistry meters) {
        return new FailOpenCache(Objects.requireNonNull(caches.getCache(name), name), meters);
    }
}
