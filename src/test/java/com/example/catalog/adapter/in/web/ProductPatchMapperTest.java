package com.example.catalog.adapter.in.web;

import static com.example.catalog.support.TestData.PRODUCT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.catalog.application.command.AttributesPatch;
import com.example.catalog.application.command.PatchField;
import com.example.catalog.application.command.PatchProductCommand;
import com.example.catalog.domain.exception.DomainValidationException;
import com.example.catalog.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductPatchMapperTest {

    private final ProductPatchMapper mapper = new ProductPatchMapper();

    private PatchProductCommand map(Map<String, Object> body) {
        return mapper.toCommand(PRODUCT_ID, OptionalLong.empty(), body);
    }

    @Test
    void distinguishesAbsentNullAndValue() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Pegasus 42");
        body.put("description", null);

        PatchProductCommand command = map(body);

        assertThat(command.name()).isEqualTo(PatchField.set("Pegasus 42"));
        assertThat(command.description()).isEqualTo(PatchField.clear());
        assertThat(command.price()).isEqualTo(PatchField.unchanged());
    }

    @Test
    void acceptsPriceAsStringOrNumberWithoutFloatRounding() {
        assertThat(map(Map.of("price", "129.99")).price()).isEqualTo(PatchField.set(new BigDecimal("129.99")));
        assertThat(map(Map.of("price", 129.99)).price()).isEqualTo(PatchField.set(new BigDecimal("129.99")));
    }

    @Test
    void readsAttributesAsKeyByKeyMerge() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("surface", "trail");
        attributes.put("gender", null);

        PatchProductCommand command = map(Map.of("attributes", attributes));

        assertThat(command.attributes()).isEqualTo(PatchField.set(new AttributesPatch(Map.of("surface", "trail"), Set.of("gender"))));
    }

    @Test
    void readsStatus() {
        assertThat(map(Map.of("status", "INACTIVE")).status()).isEqualTo(PatchField.set(ProductStatus.INACTIVE));
        assertThatThrownBy(() -> map(Map.of("status", "SOLD_OUT"))).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void refusesSkuAndUnknownFields() {
        assertThatThrownBy(() -> map(Map.of("sku", "NK-NEW")))
                .isInstanceOf(DomainValidationException.class)
                .extracting("field").isEqualTo("sku");
        assertThatThrownBy(() -> map(Map.of("nmae", "typo")))
                .isInstanceOf(DomainValidationException.class)
                .extracting("field").isEqualTo("nmae");
    }

    @Test
    void refusesWrongTypes() {
        assertThatThrownBy(() -> map(Map.of("name", 42))).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> map(Map.of("price", "cheap"))).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> map(Map.of("attributes", "red"))).isInstanceOf(DomainValidationException.class);
    }
}
