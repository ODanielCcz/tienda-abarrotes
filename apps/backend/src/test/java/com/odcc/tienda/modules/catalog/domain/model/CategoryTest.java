package com.odcc.tienda.modules.catalog.domain.model;

import com.odcc.tienda.modules.catalog.domain.exception.InvalidCategoryException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CategoryTest {

    @Test
    void shouldCreateActiveRootCategoryWithNormalizedCode() {
        Category category = Category.create(" bebidas ", " Bebidas ", null);

        assertNotNull(category.getId());
        assertEquals("BEBIDAS", category.getCode());
        assertEquals("Bebidas", category.getName());
        assertEquals(CategoryStatus.ACTIVE, category.getStatus());
        assertNull(category.getParentCategoryId());
        assertNotNull(category.getCreatedAt());
        assertNotNull(category.getUpdatedAt());
    }

    @Test
    void shouldRejectInvalidCode() {
        assertThrows(InvalidCategoryException.class, () -> Category.create("bebidas premium", "Bebidas", null));
    }

    @Test
    void shouldRejectSelfParentOnRestore() {
        UUID categoryId = UUID.randomUUID();

        assertThrows(
            InvalidCategoryException.class,
            () -> Category.restore(
                categoryId,
                categoryId,
                "BEBIDAS",
                "Bebidas",
                CategoryStatus.ACTIVE,
                Instant.now(),
                Instant.now()
            )
        );
    }

    @Test
    void shouldUpdateParentAndStatus() {
        UUID parentCategoryId = UUID.randomUUID();
        Category category = Category.create("ABARROTES", "Abarrotes", null);

        Category updated = category.update("LIMPIEZA", "Limpieza", parentCategoryId);
        Category inactive = updated.changeStatus(CategoryStatus.INACTIVE);

        assertEquals(parentCategoryId, updated.getParentCategoryId());
        assertEquals("LIMPIEZA", updated.getCode());
        assertEquals(CategoryStatus.INACTIVE, inactive.getStatus());
    }
}