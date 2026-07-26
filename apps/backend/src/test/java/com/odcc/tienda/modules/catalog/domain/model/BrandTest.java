package com.odcc.tienda.modules.catalog.domain.model;

import com.odcc.tienda.modules.catalog.domain.exception.InvalidBrandException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandTest {

    @Test
    void shouldNormalizeCodeAndNameWhenCreated() {
        Brand brand = Brand.create("  coca-cola  ", "  Coca Cola  ");

        assertEquals("COCA-COLA", brand.getCode());
        assertEquals("Coca Cola", brand.getName());
        assertEquals(BrandStatus.ACTIVE, brand.getStatus());
    }

    @Test
    void shouldRejectInvalidCode() {
        assertThrows(
            InvalidBrandException.class,
            () -> Brand.create("coca cola!", "Coca Cola")
        );
    }

    @Test
    void shouldReturnUpdatedCopyPreservingIdentityAndState() {
        UUID id = UUID.fromString("f2899172-fe75-4e96-aad1-eccf87c9ca53");
        Instant createdAt = Instant.parse("2026-07-24T06:00:00Z");
        Brand brand = Brand.restore(
            id,
            "OLD",
            "Nombre anterior",
            BrandStatus.INACTIVE,
            createdAt
        );

        Brand updated = brand.update(" new-code ", " Nombre nuevo ");

        assertNotSame(brand, updated);
        assertEquals(id, updated.getId());
        assertEquals("NEW-CODE", updated.getCode());
        assertEquals("Nombre nuevo", updated.getName());
        assertEquals(BrandStatus.INACTIVE, updated.getStatus());
        assertEquals(createdAt, updated.getCreatedAt());
    }

    @Test
    void shouldChangeStatusAndKeepOperationIdempotent() {
        Brand brand = Brand.create("STATUS", "Estado");

        Brand inactive = brand.changeStatus(BrandStatus.INACTIVE);
        Brand unchanged = inactive.changeStatus(BrandStatus.INACTIVE);

        assertEquals(BrandStatus.INACTIVE, inactive.getStatus());
        assertSame(inactive, unchanged);
    }
}
