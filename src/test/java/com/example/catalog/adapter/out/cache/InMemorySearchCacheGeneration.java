package com.example.catalog.adapter.out.cache;

import java.util.OptionalLong;

class InMemorySearchCacheGeneration implements SearchCacheGeneration {

    private long generation;
    private boolean available = true;

    @Override
    public OptionalLong current() {
        return available ? OptionalLong.of(generation) : OptionalLong.empty();
    }

    @Override
    public void advance() {
        generation++;
    }

    long value() {
        return generation;
    }

    void makeUnavailable() {
        available = false;
    }
}
