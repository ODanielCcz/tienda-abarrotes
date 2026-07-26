package com.odcc.tienda.modules.catalog.adapter.out.persistence;

import com.odcc.tienda.TestcontainersConfiguration;
import com.odcc.tienda.modules.catalog.application.port.out.ProductRepositoryPort;
import com.odcc.tienda.modules.catalog.application.query.ListProductsQuery;
import com.odcc.tienda.modules.catalog.application.query.ProductPage;
import com.odcc.tienda.modules.catalog.application.query.ProductSortField;
import com.odcc.tienda.modules.catalog.application.query.SortDirection;
import com.odcc.tienda.modules.catalog.domain.model.Product;
import com.odcc.tienda.modules.catalog.domain.model.ProductStatus;
import com.odcc.tienda.modules.catalog.domain.model.ProductType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ProductPersistenceAdapterTest {

    @Autowired
    private ProductRepositoryPort productRepository;

    @Test
    void shouldSaveProductInPostgreSql() {
        Product product = Product.create(null, null, "PERSIST-PRODUCT", "Producto persistido", ProductType.GOODS, true, false, false);

        Product savedProduct = productRepository.save(product);

        assertNotNull(savedProduct.getId());
        assertEquals("PERSIST-PRODUCT", savedProduct.getName());
        assertEquals(ProductStatus.ACTIVE, savedProduct.getStatus());
        assertNotNull(savedProduct.getCreatedAt());
        assertNotNull(savedProduct.getUpdatedAt());
    }

    @Test
    void shouldFindProductByIdInPostgreSql() {
        Product product = productRepository.save(Product.create(null, null, "FIND-PRODUCT", null, ProductType.GOODS, true, false, false));

        Optional<Product> foundProduct = productRepository.findById(product.getId());

        assertTrue(foundProduct.isPresent());
        assertEquals(product.getId(), foundProduct.get().getId());
    }

    @Test
    void shouldFilterSortAndPaginateProductsInPostgreSql() {
        productRepository.save(Product.create(null, null, "PERSIST-AGUA", "Bebida", ProductType.GOODS, true, false, false));
        productRepository.save(Product.create(null, null, "PERSIST-SERVICIO", "Servicio", ProductType.SERVICE, false, false, false));

        ProductPage page = productRepository.findAll(new ListProductsQuery(
            0,
            10,
            "agua",
            ProductStatus.ACTIVE,
            null,
            null,
            ProductSortField.NAME,
            SortDirection.ASC
        ));

        assertEquals(1, page.totalElements());
        assertEquals("PERSIST-AGUA", page.content().getFirst().getName());
    }

    @Test
    void shouldReturnEmptyWhenProductDoesNotExistInPostgreSql() {
        Optional<Product> product = productRepository.findById(UUID.randomUUID());

        assertTrue(product.isEmpty());
    }
}
