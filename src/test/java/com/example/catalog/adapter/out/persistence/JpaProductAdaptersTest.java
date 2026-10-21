package com.example.catalog.adapter.out.persistence;

import static com.example.catalog.support.TestData.NOW;
import static com.example.catalog.support.TestData.usd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.catalog.adapter.out.id.UuidV7Generator;
import com.example.catalog.domain.exception.ConcurrentUpdateException;
import com.example.catalog.domain.exception.DuplicateSkuException;
import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.model.Product;
import com.example.catalog.domain.model.ProductDetails;
import com.example.catalog.domain.model.ProductId;
import com.example.catalog.domain.model.ProductStatus;
import com.example.catalog.domain.model.Sku;
import com.example.catalog.domain.query.Cursor;
import com.example.catalog.domain.query.PageResult;
import com.example.catalog.domain.query.ProductSearchCriteria;
import com.example.catalog.domain.query.ProductSummary;
import com.example.catalog.domain.query.SortOption;
import com.example.catalog.support.PostgresTestcontainer;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/** Runs against real PostgreSQL with the Flyway schema, so mappings, constraints and keyset SQL are all exercised. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainer.class)
class JpaProductAdaptersTest {

    private static final List<String> PRICES = List.of("10.00", "20.00", "20.00", "30.00", "45.50");

    @Autowired
    private SpringDataProductRepository productRows;
    @Autowired
    private SpringDataCategoryRepository categoryRows;
    @Autowired
    private EntityManager entityManager;

    private final UuidV7Generator ids = new UuidV7Generator(Clock.systemUTC());

    private JpaProductRepositoryAdapter products;
    private JpaProductSearchAdapter search;
    private Category running;
    private Category tops;

    @BeforeEach
    void setUp() {
        products = new JpaProductRepositoryAdapter(productRows);
        search = new JpaProductSearchAdapter(entityManager);
        JpaCategoryRepositoryAdapter categories = new JpaCategoryRepositoryAdapter(categoryRows);
        running = categories.findBySlug("running-shoes").orElseThrow();
        tops = categories.findBySlug("tops").orElseThrow();
    }

    @Test
    void storesAndReloadsEveryField() {
        Product inserted = products.insert(newProduct("NK-ROUND-TRIP", running, "129.99", ProductStatus.ACTIVE, NOW));
        entityManager.clear();

        Product reloaded = products.findById(inserted.id()).orElseThrow();

        assertThat(reloaded).isEqualTo(inserted);
        assertThat(reloaded.details().attributes()).containsEntry("colour", "black");
    }

    @Test
    void duplicateSkuBecomesADomainError() {
        products.insert(newProduct("NK-DUPLICATE", running, "10", ProductStatus.ACTIVE, NOW));

        assertThatThrownBy(() -> products.insert(newProduct("NK-DUPLICATE", tops, "20", ProductStatus.ACTIVE, NOW)))
                .isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void updateBumpsVersionAndRejectsStaleWrites() {
        Product original = products.insert(newProduct("NK-VERSIONED", running, "10", ProductStatus.ACTIVE, NOW));
        ProductDetails renamed = new ProductDetails("Renamed", null, running, usd("11"), ProductStatus.ACTIVE, Map.of());

        Product updated = products.update(original.update(renamed, NOW.plusSeconds(1)));

        assertThat(updated.version()).isEqualTo(1);
        assertThatThrownBy(() -> products.update(original.update(renamed, NOW.plusSeconds(2))))
                .isInstanceOf(ConcurrentUpdateException.class);
    }

    @ParameterizedTest
    @EnumSource(SortOption.class)
    void walkingEveryPageReturnsEachMatchingProductOnceInOrder(SortOption sort) {
        List<Product> seeded = seedCatalogWithTies();
        Set<ProductId> expected = seeded.stream()
                .filter(p -> p.details().category().equals(running))
                .filter(p -> p.details().status() == ProductStatus.ACTIVE)
                .filter(p -> p.details().price().amount().compareTo(new BigDecimal("15")) >= 0)
                .filter(p -> p.details().price().amount().compareTo(new BigDecimal("40")) <= 0)
                .map(Product::id)
                .collect(Collectors.toSet());

        List<ProductSummary> walked = walk(ProductSearchCriteria.builder()
                .category(running.id()).minPrice(new BigDecimal("15")).maxPrice(new BigDecimal("40")).sort(sort).limit(7));

        assertThat(walked).extracting(ProductSummary::id).doesNotHaveDuplicates();
        assertThat(walked).extracting(ProductSummary::id).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(walked).isSortedAccordingTo(sortKeyOrder(sort));
    }

    @Test
    void listingsShowOnlyActiveProducts() {
        products.insert(newProduct("NK-ACTIVE", running, "10", ProductStatus.ACTIVE, NOW));
        products.insert(newProduct("NK-INACTIVE", running, "10", ProductStatus.INACTIVE, NOW));
        Product archived = products.insert(newProduct("NK-ARCHIVED", running, "10", ProductStatus.ACTIVE, NOW));
        products.update(archived.archive(NOW.plusSeconds(1)));

        PageResult<ProductSummary> page = search.search(ProductSearchCriteria.builder().category(running.id()).build());

        assertThat(page.items()).extracting(summary -> summary.sku().value()).containsExactly("NK-ACTIVE");
    }

    // Many products share a price and a creation time, so ordering only works if id breaks ties.
    private List<Product> seedCatalogWithTies() {
        List<Product> seeded = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            Category category = i % 5 == 0 ? tops : running;
            ProductStatus status = i % 11 == 0 ? ProductStatus.INACTIVE : ProductStatus.ACTIVE;
            Instant createdAt = NOW.plusSeconds(i / 6);
            seeded.add(products.insert(newProduct("NK-SEED-" + i, category, PRICES.get(i % PRICES.size()), status, createdAt)));
        }
        return seeded;
    }

    private List<ProductSummary> walk(ProductSearchCriteria.Builder criteria) {
        List<ProductSummary> rows = new ArrayList<>();
        Cursor cursor = null;
        do {
            PageResult<ProductSummary> page = search.search(criteria.after(cursor).build());
            rows.addAll(page.items());
            cursor = page.nextCursor();
        } while (cursor != null);
        return rows;
    }

    private static Comparator<ProductSummary> sortKeyOrder(SortOption sort) {
        Comparator<ProductSummary> ascending = switch (sort.key()) {
            case PRICE -> Comparator.comparing(summary -> summary.price().amount());
            case CREATED_AT -> Comparator.comparing(ProductSummary::createdAt);
        };
        return sort.direction() == SortOption.Direction.ASCENDING ? ascending : ascending.reversed();
    }

    private Product newProduct(String sku, Category category, String price, ProductStatus status, Instant createdAt) {
        ProductDetails details = new ProductDetails("Product " + sku, "Description", category, usd(price), status,
                Map.of("colour", "black"));
        return Product.create(ProductId.of(ids.newId()), Sku.of(sku), details, createdAt);
    }
}
