package com.odcc.tienda.modules.catalog.adapter.in.rest;

import com.odcc.tienda.TestcontainersConfiguration;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@WithMockUser(authorities = "CATALOG_BRAND_CREATE")
class CreateBrandApiIntegrationTest {

    private static final String ENDPOINT =
        "/api/v1/catalog/brands";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateBrandAndPersistItInPostgreSql()
        throws Exception {

        mockMvc.perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "code": "api-e2e-brand",
                          "name": "Marca API E2E"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.code").value("BRAND_CREATED"))
            .andExpect(jsonPath("$.data.id").isNotEmpty())
            .andExpect(jsonPath("$.data.code").value("API-E2E-BRAND"))
            .andExpect(jsonPath("$.data.name").value("Marca API E2E"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.path").value(ENDPOINT));

        Integer persistedBrands = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM catalog.brands
                WHERE code = ?
                """,
            Integer.class,
            "API-E2E-BRAND"
        );

        assertEquals(1, persistedBrands);
    }

    @Test
    void shouldReturnConflictWhenBrandCodeAlreadyExists()
        throws Exception {

        String request = """
            {
              "code": "api-duplicated-brand",
              "name": "Marca duplicada"
            }
            """;

        mockMvc.perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)
            )
            .andExpect(status().isCreated());

        mockMvc.perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.code")
                    .value("BRAND_CODE_ALREADY_EXISTS")
            )
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.path").value(ENDPOINT));

        Integer persistedBrands = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM catalog.brands
                WHERE code = ?
                """,
            Integer.class,
            "API-DUPLICATED-BRAND"
        );

        assertEquals(1, persistedBrands);
    }

    @Test
    void shouldReturnBadRequestAndNotPersistInvalidBrand()
        throws Exception {

        mockMvc.perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "code": "",
                          "name": ""
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_ERROR")
            )
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors.code").exists())
            .andExpect(jsonPath("$.errors.name").exists())
            .andExpect(jsonPath("$.path").value(ENDPOINT));

        Integer persistedBrands = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM catalog.brands
                WHERE code = ''
                   OR name = ''
                """,
            Integer.class
        );

        assertEquals(0, persistedBrands);
    }
}
