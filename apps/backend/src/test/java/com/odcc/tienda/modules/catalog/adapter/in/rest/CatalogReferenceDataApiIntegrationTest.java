package com.odcc.tienda.modules.catalog.adapter.in.rest;

import com.odcc.tienda.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@WithMockUser(authorities = "CATALOG_PRESENTATION_READ")
class CatalogReferenceDataApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void shouldListUnitsAndOnlyActiveTaxesForPresentationForms() throws Exception {
        UUID unitId = UUID.randomUUID();
        UUID activeTaxId = UUID.randomUUID();
        UUID inactiveTaxId = UUID.randomUUID();
        String suffix = unitId.toString().substring(0, 8).toUpperCase();

        jdbc.update(
            "INSERT INTO catalog.units_of_measure (unit_id, code, name, symbol, quantity_scale) VALUES (?, ?, ?, ?, ?)",
            unitId,
            "U" + suffix,
            "Unidad " + suffix,
            "u",
            3
        );
        jdbc.update(
            "INSERT INTO catalog.taxes (tax_id, code, name, rate, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
            activeTaxId,
            "TA" + suffix,
            "Impuesto activo " + suffix,
            0.160000
        );
        jdbc.update(
            "INSERT INTO catalog.taxes (tax_id, code, name, rate, status) VALUES (?, ?, ?, ?, 'INACTIVE')",
            inactiveTaxId,
            "TI" + suffix,
            "Impuesto inactivo " + suffix,
            0.080000
        );

        mockMvc.perform(get("/api/v1/catalog/units"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CATALOG_UNITS_FOUND"))
            .andExpect(jsonPath("$.data[?(@.id == '%s')].name".formatted(unitId)).value("Unidad " + suffix))
            .andExpect(jsonPath("$.data[?(@.id == '%s')].quantityScale".formatted(unitId)).value(3));

        mockMvc.perform(get("/api/v1/catalog/taxes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CATALOG_TAXES_FOUND"))
            .andExpect(jsonPath("$.data[?(@.id == '%s')].name".formatted(activeTaxId)).value("Impuesto activo " + suffix))
            .andExpect(jsonPath("$.data[?(@.id == '%s')]".formatted(inactiveTaxId)).isEmpty());
    }

    @Test
    void shouldPublishReferenceEndpointsInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/catalog/units'].get").exists())
            .andExpect(jsonPath("$.paths['/api/v1/catalog/taxes'].get").exists());
    }
}
