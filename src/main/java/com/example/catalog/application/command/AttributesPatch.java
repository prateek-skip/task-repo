package com.example.catalog.application.command;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Key-by-key change to the attribute map, as RFC 7396 defines for nested objects: listed keys are
 * set, keys sent as null are removed, and keys not mentioned are kept. A client can change one
 * attribute without resending the others.
 */
public record AttributesPatch(Map<String, String> upserts, Set<String> removals) {

    public AttributesPatch {
        upserts = Map.copyOf(upserts);
        removals = Set.copyOf(removals);
    }

    public Map<String, String> applyTo(Map<String, String> current) {
        Map<String, String> merged = new TreeMap<>(current);
        removals.forEach(merged::remove);
        merged.putAll(upserts);
        return merged;
    }
}
