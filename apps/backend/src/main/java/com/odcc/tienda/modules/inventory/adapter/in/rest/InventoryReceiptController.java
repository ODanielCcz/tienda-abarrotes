package com.odcc.tienda.modules.inventory.adapter.in.rest;

import com.odcc.tienda.modules.inventory.adapter.in.rest.mapper.InventoryReceiptRestMapper;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.CreateInventoryReceiptRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.response.InventoryReceiptResponse;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;
import com.odcc.tienda.modules.inventory.application.port.in.CreateInventoryReceiptUseCase;
import com.odcc.tienda.modules.inventory.application.port.in.GetInventoryReceiptByIdUseCase;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/receipts")
@RequiredArgsConstructor
@Tag(name = "Recepciones de inventario", description = "Entradas de mercancia con lotes y pallets opcionales")
public class InventoryReceiptController {

    private final CreateInventoryReceiptUseCase createInventoryReceiptUseCase;
    private final GetInventoryReceiptByIdUseCase getInventoryReceiptByIdUseCase;
    private final InventoryReceiptRestMapper mapper;

    @PostMapping
    @Operation(summary = "Registrar una recepcion de mercancia")
    @PreAuthorize("hasAuthority('INVENTORY_RECEIPT_CREATE')")
    public ResponseEntity<ApiResponseDto<InventoryReceiptResponse>> create(
        @Valid @RequestBody CreateInventoryReceiptRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        InventoryReceipt receipt = createInventoryReceiptUseCase.execute(
            mapper.toCommand(request),
            currentUserId(jwt)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(
            HttpStatus.CREATED,
            "INVENTORY_RECEIPT_CREATED",
            "Recepcion de inventario registrada correctamente",
            mapper.toResponse(receipt),
            servletRequest.getRequestURI()
        ));
    }

    private UUID currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new IllegalArgumentException("No se pudo identificar al usuario autenticado");
        return UUID.fromString(jwt.getSubject());
    }

    @GetMapping("/{receiptId}")
    @Operation(summary = "Consultar una recepcion de mercancia")
    @PreAuthorize("hasAuthority('INVENTORY_RECEIPT_READ')")
    public ResponseEntity<ApiResponseDto<InventoryReceiptResponse>> getById(
        @PathVariable("receiptId") UUID receiptId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        InventoryReceipt receipt = getInventoryReceiptByIdUseCase.execute(receiptId, currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "INVENTORY_RECEIPT_FOUND",
            "Recepcion de inventario consultada correctamente",
            mapper.toResponse(receipt),
            servletRequest.getRequestURI()
        ));
    }

}
