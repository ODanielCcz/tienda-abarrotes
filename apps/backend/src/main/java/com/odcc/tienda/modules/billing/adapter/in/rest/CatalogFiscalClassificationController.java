package com.odcc.tienda.modules.billing.adapter.in.rest;

import com.odcc.tienda.modules.billing.adapter.in.rest.mapper.CatalogFiscalClassificationRestMapper;
import com.odcc.tienda.modules.billing.adapter.in.rest.request.BillingRequests.ProductFiscalClassificationRequest;
import com.odcc.tienda.modules.billing.adapter.in.rest.request.BillingRequests.UnitFiscalClassificationRequest;
import com.odcc.tienda.modules.billing.application.port.in.BillingUseCases;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@Tag(name = "Clasificacion fiscal de catalogo", description = "Codigos SAT usados para preparar documentos fiscales")
public class CatalogFiscalClassificationController {

    private final BillingUseCases useCases;
    private final CatalogFiscalClassificationRestMapper mapper;

    @PutMapping("/products/{productId}/fiscal-classification")
    @Operation(summary = "Asignar codigo SAT a producto")
    @PreAuthorize("hasAuthority('CATALOG_FISCAL_CLASSIFICATION_UPDATE')")
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> updateProduct(
        @PathVariable UUID productId,
        @Valid @RequestBody ProductFiscalClassificationRequest request,
        HttpServletRequest servletRequest
    ) {
        useCases.updateProductFiscalClassification(
            mapper.toProductCommand(productId, request),
            currentUserId(servletRequest)
        );
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "PRODUCT_FISCAL_CLASSIFICATION_UPDATED",
            "Clasificacion fiscal del producto actualizada correctamente",
            Map.of("productId", productId, "satProductServiceCode", request.satProductServiceCode().trim()),
            servletRequest.getRequestURI()));
    }

    @PutMapping("/units/{unitId}/fiscal-classification")
    @Operation(summary = "Asignar codigo SAT a unidad de medida")
    @PreAuthorize("hasAuthority('CATALOG_FISCAL_CLASSIFICATION_UPDATE')")
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> updateUnit(
        @PathVariable UUID unitId,
        @Valid @RequestBody UnitFiscalClassificationRequest request,
        HttpServletRequest servletRequest
    ) {
        useCases.updateUnitFiscalClassification(
            mapper.toUnitCommand(unitId, request),
            currentUserId(servletRequest)
        );
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "UNIT_FISCAL_CLASSIFICATION_UPDATED",
            "Clasificacion fiscal de la unidad actualizada correctamente",
            Map.of("unitId", unitId, "satUnitCode", request.satUnitCode().trim().toUpperCase()),
            servletRequest.getRequestURI()));
    }

    private static UUID currentUserId(HttpServletRequest request) {
        if (request.getUserPrincipal() == null || request.getUserPrincipal().getName() == null) {
            throw new IllegalStateException("El JWT no contiene usuario");
        }
        return UUID.fromString(request.getUserPrincipal().getName());
    }
}
