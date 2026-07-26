package com.odcc.tienda.modules.catalog.domain.model;

import com.odcc.tienda.modules.catalog.domain.exception.InvalidProductException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductPresentationTest {

    @Test
    void shouldCreateValidPresentation() {
        ProductPresentation presentation = ProductPresentation.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            " arroz-1kg ",
            " Bolsa 1 kg ",
            null,
            BigDecimal.ONE,
            BigDecimal.ZERO
        );

        assertNotNull(presentation.getId());
        assertEquals("ARROZ-1KG", presentation.getSku());
        assertEquals("Bolsa 1 kg", presentation.getName());
        assertEquals(ProductPresentationStatus.ACTIVE, presentation.getStatus());
        assertEquals(BigDecimal.ONE, presentation.getConversionFactor());
    }

    @Test
    void shouldRejectInvalidSku() {
        assertThrows(InvalidProductException.class, () -> ProductPresentation.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "ARROZ 1KG",
            "Bolsa 1 kg",
            BigDecimal.ONE,
            null,
            BigDecimal.ZERO
        ));
    }

    @Test
    void shouldRejectNegativeMinimumStock() {
        assertThrows(InvalidProductException.class, () -> ProductPresentation.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "ARROZ-1KG",
            "Bolsa 1 kg",
            BigDecimal.ONE,
            null,
            new BigDecimal("-1")
        ));
    }

    @Test
    void shouldChangeStatus() {
        ProductPresentation presentation = ProductPresentation.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "ARROZ-1KG",
            "Bolsa 1 kg",
            BigDecimal.ONE,
            null,
            BigDecimal.ZERO
        );

        ProductPresentation updated = presentation.changeStatus(ProductPresentationStatus.INACTIVE);

        assertEquals(ProductPresentationStatus.INACTIVE, updated.getStatus());
    }
}
