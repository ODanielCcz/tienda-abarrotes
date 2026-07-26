package com.odcc.tienda.modules.catalog.adapter.in.rest;

import com.odcc.tienda.modules.catalog.adapter.in.rest.error.BrandExceptionHandler;
import com.odcc.tienda.modules.catalog.adapter.in.rest.mapper.BrandRestMapperImpl;
import com.odcc.tienda.modules.catalog.application.command.CreateBrandCommand;
import com.odcc.tienda.modules.catalog.application.command.UpdateBrandCommand;
import com.odcc.tienda.modules.catalog.application.command.ChangeBrandStatusCommand;
import com.odcc.tienda.modules.catalog.application.exception.BrandCodeAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.exception.BrandNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.in.CreateBrandUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.GetBrandByIdUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ListBrandsUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.UpdateBrandUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ChangeBrandStatusUseCase;
import com.odcc.tienda.modules.catalog.application.query.BrandPage;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import com.odcc.tienda.modules.catalog.domain.model.BrandStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrandController.class)
@Import({
    BrandRestMapperImpl.class,
    BrandExceptionHandler.class
})
@WithMockUser(authorities = {
    "CATALOG_BRAND_READ",
    "CATALOG_BRAND_CREATE",
    "CATALOG_BRAND_UPDATE",
    "CATALOG_BRAND_STATUS"
})
class BrandControllerTest {

    private static final String ENDPOINT =
        "/api/v1/catalog/brands";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateBrandUseCase createBrandUseCase;

    @MockitoBean
    private GetBrandByIdUseCase getBrandByIdUseCase;

    @MockitoBean
    private ListBrandsUseCase listBrandsUseCase;

    @MockitoBean
    private UpdateBrandUseCase updateBrandUseCase;

    @MockitoBean
    private ChangeBrandStatusUseCase changeBrandStatusUseCase;

    @Test
    void shouldCreateBrand() throws Exception {
        UUID brandId = UUID.fromString(
            "07c5ab7f-bfff-47da-9397-342394649481"
        );

        Instant createdAt = Instant.parse(
            "2026-07-13T18:00:00Z"
        );

        Brand createdBrand = Brand.restore(
            brandId,
            "COCA-COLA",
            "Coca Cola",
            BrandStatus.ACTIVE,
            createdAt
        );

        given(
            createBrandUseCase.execute(
                any(CreateBrandCommand.class)
            )
        ).willReturn(createdBrand);

        mockMvc.perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "code": "coca-cola",
                          "name": "Coca Cola"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.timestamp").isNotEmpty())
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.code").value("BRAND_CREATED"))
            .andExpect(
                jsonPath("$.message")
                    .value("Marca creada correctamente")
            )
            .andExpect(jsonPath("$.data.id").value(brandId.toString()))
            .andExpect(jsonPath("$.data.code").value("COCA-COLA"))
            .andExpect(jsonPath("$.data.name").value("Coca Cola"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(
                jsonPath("$.data.createdAt")
                    .value("2026-07-13T18:00:00Z")
            )
            .andExpect(jsonPath("$.path").value(ENDPOINT));
    }

    @Test
    void shouldReturnConflictWhenCodeAlreadyExists()
        throws Exception {

        given(
            createBrandUseCase.execute(
                any(CreateBrandCommand.class)
            )
        ).willThrow(
            new BrandCodeAlreadyExistsException("COCA-COLA")
        );

        mockMvc.perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "code": "COCA-COLA",
                          "name": "Coca Cola"
                        }
                        """)
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(
                jsonPath("$.code")
                    .value("BRAND_CODE_ALREADY_EXISTS")
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Ya existe una marca con el código: COCA-COLA"
                    )
            )
            .andExpect(jsonPath("$.path").value(ENDPOINT));
    }

    @Test
    void shouldReturnBadRequestWhenRequestIsInvalid()
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
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_ERROR")
            )
            .andExpect(jsonPath("$.errors.code").exists())
            .andExpect(jsonPath("$.errors.name").exists())
            .andExpect(jsonPath("$.path").value(ENDPOINT));

        verifyNoInteractions(createBrandUseCase);
    }

    @Test
    void shouldReturnBrandById() throws Exception {
        UUID brandId = UUID.fromString(
            "ef15c4d1-8b6c-4aef-9877-fe3596832f89"
        );

        Brand brand = Brand.restore(
            brandId,
            "PEPSI",
            "Pepsi",
            BrandStatus.ACTIVE,
            Instant.parse("2026-07-14T07:00:00Z")
        );

        given(getBrandByIdUseCase.execute(brandId))
            .willReturn(brand);

        String endpoint = ENDPOINT + "/" + brandId;

        mockMvc.perform(get(endpoint))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timestamp").isNotEmpty())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.code").value("BRAND_FOUND"))
            .andExpect(
                jsonPath("$.message")
                    .value("Marca encontrada correctamente")
            )
            .andExpect(jsonPath("$.data.id").value(brandId.toString()))
            .andExpect(jsonPath("$.data.code").value("PEPSI"))
            .andExpect(jsonPath("$.data.name").value("Pepsi"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(
                jsonPath("$.data.createdAt")
                    .value("2026-07-14T07:00:00Z")
            )
            .andExpect(jsonPath("$.path").value(endpoint));
    }

    @Test
    void shouldReturnNotFoundWhenBrandDoesNotExist()
        throws Exception {

        UUID brandId = UUID.fromString(
            "ff422126-f9cd-48f3-ae37-e03ec7bb5c18"
        );

        given(getBrandByIdUseCase.execute(brandId))
            .willThrow(new BrandNotFoundException(brandId));

        String endpoint = ENDPOINT + "/" + brandId;

        mockMvc.perform(get(endpoint))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"))
            .andExpect(
                jsonPath("$.message")
                    .value("No existe una marca con el id: " + brandId)
            )
            .andExpect(jsonPath("$.path").value(endpoint));
    }

    @Test
    void shouldReturnBadRequestWhenBrandIdIsInvalid()
        throws Exception {

        String endpoint = ENDPOINT + "/invalid-uuid";

        mockMvc.perform(get(endpoint))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
            .andExpect(jsonPath("$.errors.brandId").exists())
            .andExpect(jsonPath("$.path").value(endpoint));

        verifyNoInteractions(getBrandByIdUseCase);
    }

    @Test
    void shouldListBrandsWithPaginationMetadata() throws Exception {
        Brand brand = Brand.restore(
            UUID.fromString("59ea3de5-b7f9-4af7-8801-f6e3634d2057"),
            "COCA-COLA",
            "Coca Cola",
            BrandStatus.ACTIVE,
            Instant.parse("2026-07-24T06:00:00Z")
        );

        given(listBrandsUseCase.execute(any()))
            .willReturn(new BrandPage(List.of(brand), 0, 20, 1, 1));

        mockMvc.perform(
                get(ENDPOINT)
                    .queryParam("search", "cola")
                    .queryParam("status", "ACTIVE")
                    .header("X-Correlation-ID", "brands-list-test")
            )
            .andExpect(status().isOk())
            .andExpect(header().string("X-Correlation-ID", "brands-list-test"))
            .andExpect(jsonPath("$.code").value("BRANDS_FOUND"))
            .andExpect(jsonPath("$.data.content[0].code").value("COCA-COLA"))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.first").value(true))
            .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void shouldRejectPageSizeAboveMaximum() throws Exception {
        mockMvc.perform(get(ENDPOINT).queryParam("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        verifyNoInteractions(listBrandsUseCase);
    }

    @Test
    void shouldUpdateBrand() throws Exception {
        UUID brandId = UUID.fromString(
            "ad9272c7-7995-4cf7-ab52-f46bb91674ad"
        );
        Brand updated = Brand.restore(
            brandId,
            "UPDATED",
            "Marca actualizada",
            BrandStatus.ACTIVE,
            Instant.parse("2026-07-24T06:00:00Z")
        );

        given(updateBrandUseCase.execute(any(UpdateBrandCommand.class)))
            .willReturn(updated);

        mockMvc.perform(
                put(ENDPOINT + "/" + brandId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "code": "updated",
                          "name": "Marca actualizada"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("BRAND_UPDATED"))
            .andExpect(jsonPath("$.data.code").value("UPDATED"));
    }

    @Test
    void shouldDeactivateBrand() throws Exception {
        UUID brandId = UUID.fromString(
            "5c369e08-054a-4c06-b00a-8e93e17fd92b"
        );
        Brand inactive = Brand.restore(
            brandId,
            "STATUS",
            "Marca",
            BrandStatus.INACTIVE,
            Instant.parse("2026-07-24T06:00:00Z")
        );

        given(
            changeBrandStatusUseCase.execute(
                any(ChangeBrandStatusCommand.class)
            )
        ).willReturn(inactive);

        mockMvc.perform(
                patch(ENDPOINT + "/" + brandId + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"status": "INACTIVE"}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("BRAND_STATUS_UPDATED"))
            .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void shouldReturnControlledErrorForMalformedJson() throws Exception {
        mockMvc.perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }
}
