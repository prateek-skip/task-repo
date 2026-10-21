package com.example.catalog.adapter.out.id;

import static com.example.catalog.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.catalog.support.MutableClock;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    private final MutableClock clock = new MutableClock(NOW);
    private final UuidV7Generator generator = new UuidV7Generator(clock);

    @Test
    void setsVersionAndVariantBits() {
        UUID id = generator.newId();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsTheMillisecondTimestamp() {
        UUID id = generator.newId();

        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(NOW.toEpochMilli());
    }

    @Test
    void laterIdsSortAfterEarlierOnes() {
        UUID earlier = generator.newId();
        clock.advance(Duration.ofMillis(1));
        UUID later = generator.newId();

        assertThat(later.toString()).isGreaterThan(earlier.toString());
    }

    @Test
    void idsAreUniqueWithinTheSameMillisecond() {
        Set<UUID> ids = new HashSet<>();
        IntStream.range(0, 10_000).forEach(i -> ids.add(generator.newId()));

        assertThat(ids).hasSize(10_000);
    }
}
