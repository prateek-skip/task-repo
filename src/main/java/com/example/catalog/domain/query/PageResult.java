package com.example.catalog.domain.query;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** One page of results and, when more rows exist, the cursor for the next page. */
public record PageResult<T>(List<T> items, int limit, Cursor nextCursor) {

    public PageResult {
        items = List.copyOf(items);
    }

    /**
     * Builds a page from a query that asked for {@code limit + 1} rows. The extra row only proves
     * that a next page exists, which avoids a COUNT query on every request.
     */
    public static <T> PageResult<T> fromOverfetch(List<T> rows, int limit, Function<T, Cursor> cursorAfter) {
        if (rows.size() <= limit) {
            return new PageResult<>(rows, limit, null);
        }
        List<T> page = rows.subList(0, limit);
        return new PageResult<>(page, limit, cursorAfter.apply(page.getLast()));
    }

    public Optional<Cursor> next() {
        return Optional.ofNullable(nextCursor);
    }
}
