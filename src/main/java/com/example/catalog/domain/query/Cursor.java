package com.example.catalog.domain.query;

import com.example.catalog.domain.exception.InvalidCursorException;
import com.example.catalog.domain.model.ProductId;

/**
 * Position just after the last row of a page: that row's sort-key value and id, plus a
 * fingerprint of the query that produced it.
 *
 * <p>The next page is "rows strictly after (value, id)". With a composite index on the sort key
 * and id, that is an index range scan of one page, however deep the client has scrolled. OFFSET
 * would read and discard every earlier row instead.
 */
public record Cursor(String sortValue, ProductId lastId, String fingerprint) {

    public Cursor {
        if (sortValue == null || lastId == null || fingerprint == null) {
            throw new InvalidCursorException("cursor is malformed");
        }
    }
}
