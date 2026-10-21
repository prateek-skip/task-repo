package com.example.catalog.adapter.out.cache;

import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON serialization for cached values. JSON rather than Java serialization keeps entries
 * readable when debugging and keeps the domain free of {@code Serializable}.
 */
public final class CacheSerialization {

    // During a rolling deploy, old and new instances share the cache. Ignoring unknown fields
    // lets either version read entries the other one wrote.
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private CacheSerialization() {
    }

    public static JavaType type(Class<?> rawType, Class<?>... parameters) {
        return MAPPER.getTypeFactory().constructParametricType(rawType, parameters);
    }

    public static JavaType type(Class<?> type) {
        return MAPPER.constructType(type);
    }

    /** A serializer bound to one concrete type, so no class names need to be stored in Redis. */
    public static RedisSerializer<Object> serializerFor(JavaType type) {
        return new JacksonJsonRedisSerializer<>(MAPPER, type);
    }
}
