package com.example.catalog.adapter.out.id;

import com.example.catalog.domain.port.IdGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * RFC 9562 version 7 UUIDs: a millisecond timestamp followed by random bits.
 *
 * <p>Time-ordered ids keep B-tree inserts near the right edge of the primary-key index, unlike
 * random v4 ids that touch pages all over it. The random part keeps ids unguessable, unlike a
 * sequence, and no database round trip is needed to get one.
 */
public final class UuidV7Generator implements IdGenerator {

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public UuidV7Generator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public UUID newId() {
        long timestamp = clock.millis() & 0xFFFF_FFFF_FFFFL;
        long randA = random.nextInt(1 << 12);
        long randB = random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL;
        long mostSignificant = (timestamp << 16) | (0x7L << 12) | randA;
        long leastSignificant = (0x2L << 62) | randB;
        return new UUID(mostSignificant, leastSignificant);
    }
}
