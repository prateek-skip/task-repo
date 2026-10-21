package com.example.catalog.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PatchFieldTest {

    @Test
    void unchangedKeepsClearRemovesSetReplaces() {
        assertThat(PatchField.<String>unchanged().applyTo("old")).isEqualTo("old");
        assertThat(PatchField.<String>clear().applyTo("old")).isNull();
        assertThat(PatchField.set("new").applyTo("old")).isEqualTo("new");
    }

    @Test
    void mapOnlyTransformsSetValues() {
        assertThat(PatchField.set("7").map(Integer::parseInt).applyTo(1)).isEqualTo(7);
        assertThat(PatchField.<String>unchanged().map(Integer::parseInt).applyTo(1)).isEqualTo(1);
        assertThat(PatchField.<String>clear().map(Integer::parseInt).applyTo(1)).isNull();
    }

    @Test
    void attributesMergeKeyByKey() {
        AttributesPatch patch = new AttributesPatch(Map.of("colour", "red", "surface", "trail"), Set.of("gender"));

        Map<String, String> merged = patch.applyTo(Map.of("colour", "black", "gender", "unisex", "drop", "10mm"));

        assertThat(merged).containsExactlyInAnyOrderEntriesOf(Map.of("colour", "red", "surface", "trail", "drop", "10mm"));
    }
}
