package com.odcc.tienda.modules.catalog.adapter.in.rest;

import com.odcc.tienda.modules.catalog.adapter.in.rest.mapper.CatalogReferenceDataRestMapper;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.CatalogTaxResponse;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.CatalogUnitResponse;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.CatalogPriceResponse;
import com.odcc.tienda.modules.catalog.application.port.in.ListCatalogPricesUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ListCatalogTaxesUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ListCatalogUnitsUseCase;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@Tag(name = "Datos de referencia del catálogo", description = "Catálogos de apoyo para presentaciones")
public class CatalogReferenceDataController {
    private final ListCatalogUnitsUseCase listCatalogUnitsUseCase;
    private final ListCatalogTaxesUseCase listCatalogTaxesUseCase;
    private final ListCatalogPricesUseCase listCatalogPricesUseCase;
    private final CatalogReferenceDataRestMapper mapper;

    @GetMapping("/prices")
    @Operation(summary = "Listar precios generales vigentes para una sucursal")
    @PreAuthorize("hasAuthority('CATALOG_PRESENTATION_READ')")
    public ResponseEntity<ApiResponseDto<List<CatalogPriceResponse>>> listPrices(
        @RequestParam UUID branchId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest request
    ) {
        List<CatalogPriceResponse> data = listCatalogPricesUseCase
            .execute(branchId, currentUserId(jwt))
            .stream()
            .map(mapper::toResponse)
            .toList();
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "CATALOG_PRICES_FOUND",
            "Precios vigentes consultados correctamente",
            data,
            request.getRequestURI()
        ));
    }

    @GetMapping("/units")
    @Operation(summary = "Listar unidades de medida")
    @PreAuthorize("hasAuthority('CATALOG_PRESENTATION_READ')")
    public ResponseEntity<ApiResponseDto<List<CatalogUnitResponse>>> listUnits(HttpServletRequest request) {
        List<CatalogUnitResponse> data = listCatalogUnitsUseCase.execute().stream()
            .map(mapper::toResponse)
            .toList();
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "CATALOG_UNITS_FOUND",
            "Unidades de medida consultadas correctamente",
            data,
            request.getRequestURI()
        ));
    }

    @GetMapping("/taxes")
    @Operation(summary = "Listar impuestos activos")
    @PreAuthorize("hasAuthority('CATALOG_PRESENTATION_READ')")
    public ResponseEntity<ApiResponseDto<List<CatalogTaxResponse>>> listTaxes(HttpServletRequest request) {
        List<CatalogTaxResponse> data = listCatalogTaxesUseCase.execute().stream()
            .map(mapper::toResponse)
            .toList();
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "CATALOG_TAXES_FOUND",
            "Impuestos consultados correctamente",
            data,
            request.getRequestURI()
        ));
    }

    private static UUID currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new IllegalArgumentException("No se pudo identificar al usuario autenticado");
        }
        return UUID.fromString(jwt.getSubject());
    }
}
