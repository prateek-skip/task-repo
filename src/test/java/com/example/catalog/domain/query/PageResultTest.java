package com.example.catalog.domain.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.catalog.domain.model.ProductId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PageResultTest {

    private static Cursor cursorFor(String value) {
        return new Cursor(value, ProductId.of(UUID.randomUUID()), "fp");
    }

    @Test
    void extraRowSignalsNextPageAndIsDropped() {
        PageResult<String> page = PageResult.fromOverfetch(List.of("a", "b", "c"), 2, PageResultTest::cursorFor);

        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.next()).get().extracting(Cursor::sortValue).isEqualTo("b");
    }

    @Test
    void noExtraRowMeansLastPage() {
        PageResult<String> page = PageResult.fromOverfetch(List.of("a", "b"), 2, PageResultTest::cursorFor);

        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.next()).isEmpty();
    }
}
