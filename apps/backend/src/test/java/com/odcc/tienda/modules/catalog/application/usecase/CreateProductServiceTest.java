package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.CreateProductCommand;
import com.odcc.tienda.modules.catalog.application.exception.ProductBrandNotFoundException;
import com.odcc.tienda.modules.catalog.application.exception.ProductCategoryNotFoundException;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import com.odcc.tienda.modules.catalog.domain.model.Product;
import com.odcc.tienda.modules.catalog.domain.model.ProductType;
import com.odcc.tienda.modules.catalog.support.InMemoryBrandRepository;
import com.odcc.tienda.modules.catalog.support.InMemoryCategoryRepository;
import com.odcc.tienda.modules.catalog.support.InMemoryProductRepository;
import com.odcc.tienda.shared.support.ImmediateTransactionRunner;
import com.odcc.tienda.shared.support.InMemoryBusinessAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateProductServiceTest {

    private InMemoryProductRepository productRepository;
    private InMemoryCategoryRepository categoryRepository;
    private InMemoryBrandRepository brandRepository;
    private InMemoryBusinessAuditPort auditPort;
    private CreateProductService service;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        categoryRepository = new InMemoryCategoryRepository();
        brandRepository = new InMemoryBrandRepository();
        auditPort = new InMemoryBusinessAuditPort();
        service = new CreateProductService(productRepository, categoryRepository, brandRepository, new ImmediateTransactionRunner(), auditPort);
    }

    @Test
    void shouldCreateProductAndAuditEvent() {
        Category category = categoryRepository.save(Category.create("DRINKS", "Bebidas", null));
        Brand brand = brandRepository.save(Brand.create("COCA", "Coca Cola"));

        Product product = service.execute(new CreateProductCommand(
            category.getId(),
            brand.getId(),
            "Coca Cola 600ml",
            "Refresco",
            ProductType.GOODS,
            true,
            false,
            false
        ));

        assertEquals("Coca Cola 600ml", product.getName());
        assertEquals(category.getId(), product.getCategoryId());
        assertEquals(brand.getId(), product.getBrandId());
        assertEquals("PRODUCT_CREATED", auditPort.events().getFirst().eventType());
    }

    @Test
    void shouldRejectUnknownCategory() {
        assertThrows(
            ProductCategoryNotFoundException.class,
            () -> service.execute(new CreateProductCommand(UUID.randomUUID(), null, "Producto", null, ProductType.GOODS, true, false, false))
        );
    }

    @Test
    void shouldRejectUnknownBrand() {
        assertThrows(
            ProductBrandNotFoundException.class,
            () -> service.execute(new CreateProductCommand(null, UUID.randomUUID(), "Producto", null, ProductType.GOODS, true, false, false))
        );
    }
}
