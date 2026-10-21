package com.example.catalog.domain.model;

public enum ProductStatus {

    /** Sellable and shown in listings. */
    ACTIVE,

    /** Hidden from listings but still readable and editable by id, e.g. while being prepared. */
    INACTIVE,

    /** The result of a delete. Hidden everywhere and read-only. */
    ARCHIVED;

    /** Archiving is a lifecycle transition reached through delete, never a value a client sets. */
    public boolean isAssignableByClient() {
        return this != ARCHIVED;
    }
}
