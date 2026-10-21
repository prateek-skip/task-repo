package com.example.catalog.domain.port;

import java.util.UUID;

/** Source of new identifiers. A port so tests can use predictable ids. */
public interface IdGenerator {

    UUID newId();
}
