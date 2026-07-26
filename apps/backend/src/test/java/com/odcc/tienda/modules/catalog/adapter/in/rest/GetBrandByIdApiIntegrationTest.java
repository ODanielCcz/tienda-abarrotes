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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@WithMockUser(authorities = "CATALOG_BRAND_READ")
class GetBrandByIdApiIntegrationTest {

    private static final String ENDPOINT =
        "/api/v1/catalog/brands";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BrandRepositoryPort brandRepository;

    @Test
    void shouldReturnPersistedBrandById() throws Exception {
        UUID brandId = UUID.fromString(
            "7cb0363e-f978-46dd-80e3-5314a98b4500"
        );

        Brand brand = Brand.restore(
            brandId,
            "API-GET-BRAND",
            "Marca consultada por API",
            BrandStatus.ACTIVE,
            Instant.parse("2026-07-14T08:00:00Z")
        );

        brandRepository.save(brand);

        String endpoint = ENDPOINT + "/" + brandId;

        mockMvc.perform(get(endpoint))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.code").value("BRAND_FOUND"))
            .andExpect(jsonPath("$.data.id").value(brandId.toString()))
            .andExpect(jsonPath("$.data.code").value("API-GET-BRAND"))
            .andExpect(
                jsonPath("$.data.name")
                    .value("Marca consultada por API")
            )
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.path").value(endpoint));
    }

    @Test
    void shouldReturnNotFoundForUnknownBrandId()
        throws Exception {

        UUID brandId = UUID.fromString(
            "a1233048-b8f2-425c-941b-025ee9af3879"
        );

        String endpoint = ENDPOINT + "/" + brandId;

        mockMvc.perform(get(endpoint))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"))
            .andExpect(jsonPath("$.path").value(endpoint));
    }
}
