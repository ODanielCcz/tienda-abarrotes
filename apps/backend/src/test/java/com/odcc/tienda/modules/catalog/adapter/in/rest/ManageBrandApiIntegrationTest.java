package com.odcc.tienda.modules.catalog.adapter.in.rest;

import com.odcc.tienda.TestcontainersConfiguration;
import com.odcc.tienda.modules.catalog.application.port.out.BrandRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import com.odcc.tienda.modules.catalog.domain.model.BrandStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@WithMockUser(authorities = {
    "CATALOG_BRAND_READ",
    "CATALOG_BRAND_UPDATE",
    "CATALOG_BRAND_STATUS"
})
class ManageBrandApiIntegrationTest {

    private static final String ENDPOINT = "/api/v1/catalog/brands";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BrandRepositoryPort brandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldListFilteredBrandsFromPostgreSql() throws Exception {
        brandRepository.save(Brand.create("API-LIST-PEPSI", "Pepsi API"));
        brandRepository.save(Brand.create("API-LIST-COLA", "Cola API"));
        brandRepository.save(
            Brand.create("API-LIST-INACTIVE", "Cola inactiva API")
                .changeStatus(BrandStatus.INACTIVE)
        );

        mockMvc.perform(
                get(ENDPOINT)
                    .queryParam("search", "cola")
                    .queryParam("status", "ACTIVE")
                    .queryParam("sortBy", "CODE")
                    .queryParam("direction", "ASC")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("BRANDS_FOUND"))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].code").value("API-LIST-COLA"));
    }

    @Test
    void shouldUpdateBrandAndWriteBusinessAudit() throws Exception {
        Brand brand = brandRepository.save(
            Brand.create("API-UPDATE-OLD", "Anterior API")
        );
        mockMvc.perform(
                put(ENDPOINT + "/" + brand.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "code": "API-UPDATE-NEW",
                          "name": "Actualizada API"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("BRAND_UPDATED"))
            .andExpect(jsonPath("$.data.code").value("API-UPDATE-NEW"));

        Integer persisted = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM catalog.brands
                WHERE brand_id = ? AND code = ? AND name = ?
                """,
            Integer.class,
            brand.getId(),
            "API-UPDATE-NEW",
            "Actualizada API"
        );

        Integer audited = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM audit.business_events
                WHERE aggregate_id = ?
                  AND event_type = 'BRAND_UPDATED'
                """,
            Integer.class,
            brand.getId()
        );

        assertEquals(1, persisted);
        assertEquals(1, audited);
    }

    @Test
    void shouldDeactivateBrandAndWriteBusinessAudit() throws Exception {
        Brand brand = brandRepository.save(
            Brand.create("API-STATUS", "Estado API")
        );

        mockMvc.perform(
                patch(ENDPOINT + "/" + brand.getId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"status": "INACTIVE"}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM catalog.brands WHERE brand_id = ?",
            String.class,
            brand.getId()
        );

        Integer audited = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM audit.business_events
                WHERE aggregate_id = ?
                  AND event_type = 'BRAND_STATUS_CHANGED'
                """,
            Integer.class,
            brand.getId()
        );

        assertEquals("INACTIVE", status);
        assertEquals(1, audited);
    }

    @Test
    void shouldPublishOpenApiContractForBrandOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("Tienda de Abarrotes API"))
            .andExpect(jsonPath("$.info.version").value("v1"))
            .andExpect(
                jsonPath("$.components.securitySchemes.bearerAuth.type")
                    .value("http")
            )
            .andExpect(
                jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                    .value("bearer")
            )
            .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
            .andExpect(
                jsonPath("$.paths['/api/v1/auth/login'].post.security")
                    .isEmpty()
            )
            .andExpect(jsonPath("$.paths['/api/v1/catalog/brands'].get").exists())
            .andExpect(jsonPath("$.paths['/api/v1/catalog/brands'].post").exists())
            .andExpect(
                jsonPath(
                    "$.paths['/api/v1/catalog/brands'].post.responses['409']"
                ).exists()
            )
            .andExpect(jsonPath("$.paths['/api/v1/catalog/brands/{brandId}'].put").exists())
            .andExpect(
                jsonPath(
                    "$.paths['/api/v1/catalog/brands/{brandId}/status'].patch"
                ).exists()
            );
    }
}
