package com.odcc.tienda.modules.catalog.adapter.in.rest;

import com.odcc.tienda.modules.catalog.adapter.in.rest.mapper.ProductPresentationRestMapper;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.ChangeProductPresentationStatusRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.UpdateProductPresentationRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.ProductPresentationResponse;
import com.odcc.tienda.modules.catalog.application.command.UpdateProductPresentationCommand;
import com.odcc.tienda.modules.catalog.application.port.in.ChangeProductPresentationStatusUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.GetProductPresentationByIdUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.UpdateProductPresentationUseCase;
import com.odcc.tienda.modules.catalog.domain.model.ProductPresentation;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/product-presentations")
@RequiredArgsConstructor
@Tag(name = "Presentaciones de producto", description = "Administracion de presentaciones vendibles del catalogo")
public class ProductPresentationController {

    private final GetProductPresentationByIdUseCase getProductPresentationByIdUseCase;
    private final UpdateProductPresentationUseCase updateProductPresentationUseCase;
    private final ChangeProductPresentationStatusUseCase changeProductPresentationStatusUseCase;
    private final ProductPresentationRestMapper mapper;

    @GetMapping("/{presentationId}")
    @Operation(summary = "Consultar una presentacion por id")
    @PreAuthorize("hasAuthority('CATALOG_PRESENTATION_READ')")
    public ResponseEntity<ApiResponseDto<ProductPresentationResponse>> getById(
        @PathVariable("presentationId") UUID presentationId,
        HttpServletRequest servletRequest
    ) {
        ProductPresentation presentation = getProductPresentationByIdUseCase.execute(presentationId);
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "PRODUCT_PRESENTATION_FOUND",
            "Presentacion encontrada correctamente",
            mapper.toResponse(presentation),
            servletRequest.getRequestURI()
        ));
    }

    @PutMapping("/{presentationId}")
    @Operation(summary = "Actualizar una presentacion")
    @PreAuthorize("hasAuthority('CATALOG_PRESENTATION_UPDATE')")
    public ResponseEntity<ApiResponseDto<ProductPresentationResponse>> update(
        @PathVariable("presentationId") UUID presentationId,
        @Valid @RequestBody UpdateProductPresentationRequest request,
        HttpServletRequest servletRequest
    ) {
        UpdateProductPresentationCommand command = mapper.toUpdateCommand(presentationId, request);
        ProductPresentation presentation = updateProductPresentationUseCase.execute(command);
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "PRODUCT_PRESENTATION_UPDATED",
            "Presentacion actualizada correctamente",
            mapper.toResponse(presentation),
            servletRequest.getRequestURI()
        ));
    }

    @PatchMapping("/{presentationId}/status")
    @Operation(summary = "Cambiar estado de una presentacion")
    @PreAuthorize("hasAuthority('CATALOG_PRESENTATION_STATUS')")
    public ResponseEntity<ApiResponseDto<ProductPresentationResponse>> changeStatus(
        @PathVariable("presentationId") UUID presentationId,
        @Valid @RequestBody ChangeProductPresentationStatusRequest request,
        HttpServletRequest servletRequest
    ) {
        ProductPresentation presentation = changeProductPresentationStatusUseCase.execute(
            mapper.toStatusCommand(presentationId, request)
        );
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "PRODUCT_PRESENTATION_STATUS_UPDATED",
            "Estado de la presentacion actualizado correctamente",
            mapper.toResponse(presentation),
            servletRequest.getRequestURI()
        ));
    }
}
