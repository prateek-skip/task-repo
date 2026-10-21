package com.example.catalog.adapter.out.cache;

import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Generation counter shared by every instance through one Redis key. */
public class RedisSearchCacheGeneration implements SearchCacheGeneration {

    static final String KEY = "catalog:product-search:generation";

    private static final Logger log = LoggerFactory.getLogger(RedisSearchCacheGeneration.class);

    private final StringRedisTemplate redis;

    public RedisSearchCacheGeneration(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public OptionalLong current() {
        try {
            String value = redis.opsForValue().get(KEY);
            return OptionalLong.of(value == null ? 0L : Long.parseLong(value));
        } catch (RuntimeException e) {
            log.warn("Could not read search cache generation; bypassing cache: {}", e.toString());
            return OptionalLong.empty();
        }
    }

    @Override
    public void advance() {
        try {
            redis.opsForValue().increment(KEY);
        } catch (RuntimeException e) {
            // Missed bumps are bounded by the search TTL, so the write still succeeds.
            log.warn("Could not advance search cache generation; listings may lag until TTL: {}", e.toString());
        }
    }
}
