package com.example.catalog.adapter.out.cache;

import java.util.OptionalLong;

/**
 * A counter that is part of every cached search key. Advancing it makes every cached page
 * unreachable at once, with no key scan; the old entries simply expire.
 */
public interface SearchCacheGeneration {

    /** The current generation, or empty if it cannot be read, in which case callers skip the cache. */
    OptionalLong current();

    void advance();
}
