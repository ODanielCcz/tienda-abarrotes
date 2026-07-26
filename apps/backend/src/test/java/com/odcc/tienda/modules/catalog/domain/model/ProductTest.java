package com.odcc.tienda.modules.catalog.domain.model;

import com.odcc.tienda.modules.catalog.domain.exception.InvalidProductException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    @Test
    void shouldCreateProductWithNormalizedNameAndDefaults() {
        Product product = Product.create(null, null, "  Coca Cola 600ml  ", null, null, true, false, false);

        assertEquals("Coca Cola 600ml", product.getName());
        assertEquals(ProductType.GOODS, product.getProductType());
        assertEquals(ProductStatus.ACTIVE, product.getStatus());
    }

    @Test
    void shouldRejectExpirationWithoutLots() {
        assertThrows(
            InvalidProductException.class,
            () -> Product.create(null, null, "Leche", null, ProductType.GOODS, true, false, true)
        );
    }

    @Test
    void shouldRejectServiceWithInventoryTracking() {
        assertThrows(
            InvalidProductException.class,
            () -> Product.create(null, null, "Servicio", null, ProductType.SERVICE, true, false, false)
        );
    }
}
