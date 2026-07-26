package com.odcc.tienda.modules.catalog.adapter.in.rest;

import com.odcc.tienda.TestcontainersConfiguration;
import com.odcc.tienda.modules.catalog.application.port.out.ProductRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.Product;
import com.odcc.tienda.modules.catalog.domain.model.ProductStatus;
import com.odcc.tienda.modules.catalog.domain.model.ProductType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@WithMockUser(authorities = {
    "CATALOG_PRODUCT_READ",
    "CATALOG_PRODUCT_CREATE",
    "CATALOG_PRODUCT_UPDATE",
    "CATALOG_PRODUCT_STATUS"
})
class ManageProductApiIntegrationTest {

    private static final String ENDPOINT = "/api/v1/catalog/products";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepositoryPort productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateProductAndWriteBusinessAudit() throws Exception {
        mockMvc.perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Producto API",
                          "description": "Producto creado desde API",
                          "productType": "GOODS",
                          "tracksInventory": true,
                          "tracksLots": false,
                          "tracksExpiration": false
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("PRODUCT_CREATED"))
            .andExpect(jsonPath("$.data.name").value("Producto API"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        Integer persisted = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM catalog.products WHERE name = ?",
            Integer.class,
            "Producto API"
        );
        Integer audited = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM audit.business_events
                WHERE event_type = 'PRODUCT_CREATED'
                  AND aggregate_type = 'PRODUCT'
                """,
            Integer.class
        );

        assertEquals(1, persisted);
        assertEquals(1, audited);
    }

    @Test
    void shouldListUpdateAndChangeStatus() throws Exception {
        Product product = productRepository.save(Product.create(null, null, "Producto editable", null, ProductType.GOODS, true, false, false));

        mockMvc.perform(get(ENDPOINT).queryParam("search", "editable"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("PRODUCTS_FOUND"))
            .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(
                put(ENDPOINT + "/" + product.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Producto actualizado",
                          "description": "Actualizado",
                          "productType": "GOODS",
                          "tracksInventory": true,
                          "tracksLots": true,
                          "tracksExpiration": false
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("PRODUCT_UPDATED"))
            .andExpect(jsonPath("$.data.name").value("Producto actualizado"))
            .andExpect(jsonPath("$.data.tracksLots").value(true));

        mockMvc.perform(
                patch(ENDPOINT + "/" + product.getId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"status": "INACTIVE"}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value(ProductStatus.INACTIVE.name()));
    }

    @Test
    void shouldPublishOpenApiContractForProductOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/catalog/products'].get").exists())
            .andExpect(jsonPath("$.paths['/api/v1/catalog/products'].post").exists())
            .andExpect(jsonPath("$.paths['/api/v1/catalog/products/{productId}'].get").exists())
            .andExpect(jsonPath("$.paths['/api/v1/catalog/products/{productId}'].put").exists())
            .andExpect(jsonPath("$.paths['/api/v1/catalog/products/{productId}/status'].patch").exists());
    }
}
