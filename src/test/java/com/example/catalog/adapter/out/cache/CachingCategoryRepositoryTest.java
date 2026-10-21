package com.example.catalog.adapter.out.cache;

import static com.example.catalog.support.TestData.NOW;
import static com.example.catalog.support.TestData.RUNNING;
import static com.example.catalog.support.TestData.TOPS;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.catalog.support.InMemoryCategoryRepository;
import com.example.catalog.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CachingCategoryRepositoryTest {

    private final InMemoryCategoryRepository database = new InMemoryCategoryRepository(RUNNING, TOPS);
    private final MutableClock clock = new MutableClock(NOW);
    private final CachingCategoryRepository repository = new CachingCategoryRepository(database, Duration.ofHours(1), clock);

    @Test
    void servesLookupsFromOneSnapshotUntilItExpires() {
        assertThat(repository.findBySlug("tops")).contains(TOPS);
        assertThat(repository.findBySlug("running-shoes")).contains(RUNNING);
        assertThat(repository.findBySlug("missing")).isEmpty();
        assertThat(database.loads()).isEqualTo(1);

        clock.advance(Duration.ofHours(1));
        repository.findAll();

        assertThat(database.loads()).isEqualTo(2);
    }
}
