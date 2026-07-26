package com.odcc.tienda.modules.inventory.adapter.in.rest;

import com.odcc.tienda.modules.inventory.adapter.in.rest.request.CreateInventoryReceiptRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.InventoryReceiptItemRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.InventoryReceiptPalletRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.response.InventoryReceiptItemResponse;
import com.odcc.tienda.modules.inventory.adapter.in.rest.response.InventoryReceiptPalletResponse;
import com.odcc.tienda.modules.inventory.adapter.in.rest.response.InventoryReceiptResponse;
import com.odcc.tienda.modules.inventory.application.command.CreateInventoryReceiptCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryReceiptItemCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryReceiptPalletCommand;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceiptItem;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceiptPallet;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/receipts")
@RequiredArgsConstructor
@Tag(name = "Recepciones de inventario", description = "Entradas de mercancia con lotes y pallets opcionales")
public class InventoryReceiptController {

    private final CreateInventoryReceiptUseCase createInventoryReceiptUseCase;
    private final GetInventoryReceiptByIdUseCase getInventoryReceiptByIdUseCase;

    @PostMapping
    @Operation(summary = "Registrar una recepcion de mercancia")
    @PreAuthorize("hasAuthority('INVENTORY_RECEIPT_CREATE')")
    public ResponseEntity<ApiResponseDto<InventoryReceiptResponse>> create(
        @Valid @RequestBody CreateInventoryReceiptRequest request,
        HttpServletRequest servletRequest
    ) {
        InventoryReceipt receipt = createInventoryReceiptUseCase.execute(toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(
            HttpStatus.CREATED,
            "INVENTORY_RECEIPT_CREATED",
            "Recepcion de inventario registrada correctamente",
            toResponse(receipt),
            servletRequest.getRequestURI()
        ));
    }

    @GetMapping("/{receiptId}")
    @Operation(summary = "Consultar una recepcion de mercancia")
    @PreAuthorize("hasAuthority('INVENTORY_RECEIPT_READ')")
    public ResponseEntity<ApiResponseDto<InventoryReceiptResponse>> getById(
        @PathVariable("receiptId") UUID receiptId,
        HttpServletRequest servletRequest
    ) {
        InventoryReceipt receipt = getInventoryReceiptByIdUseCase.execute(receiptId);
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "INVENTORY_RECEIPT_FOUND",
            "Recepcion de inventario consultada correctamente",
            toResponse(receipt),
            servletRequest.getRequestURI()
        ));
    }

    private CreateInventoryReceiptCommand toCommand(CreateInventoryReceiptRequest request) {
        return new CreateInventoryReceiptCommand(
            request.warehouseId(),
            request.supplierId(),
            request.idempotencyKey(),
            request.reason(),
            request.items() == null ? List.of() : request.items().stream().map(this::toItemCommand).toList(),
            request.pallets() == null ? List.of() : request.pallets().stream().map(this::toPalletCommand).toList()
        );
    }

    private InventoryReceiptPalletCommand toPalletCommand(InventoryReceiptPalletRequest request) {
        return new InventoryReceiptPalletCommand(
            request.externalPalletCode(),
            request.items().stream().map(this::toItemCommand).toList()
        );
    }

    private InventoryReceiptItemCommand toItemCommand(InventoryReceiptItemRequest request) {
        return new InventoryReceiptItemCommand(
            request.productPresentationId(),
            request.lotNumber(),
            request.manufacturedAt(),
            request.expiresAt(),
            request.quantity(),
            request.unitCost()
        );
    }

    private InventoryReceiptResponse toResponse(InventoryReceipt receipt) {
        return new InventoryReceiptResponse(
            receipt.receiptId(),
            receipt.warehouseId(),
            receipt.supplierId(),
            receipt.status(),
            receipt.receivedAt(),
            receipt.items().stream().map(this::toItemResponse).toList(),
            receipt.pallets().stream().map(this::toPalletResponse).toList()
        );
    }

    private InventoryReceiptPalletResponse toPalletResponse(InventoryReceiptPallet pallet) {
        return new InventoryReceiptPalletResponse(
            pallet.palletId(),
            pallet.palletCode(),
            pallet.externalPalletCode(),
            pallet.status(),
            pallet.items().stream().map(this::toItemResponse).toList()
        );
    }

    private InventoryReceiptItemResponse toItemResponse(InventoryReceiptItem item) {
        return new InventoryReceiptItemResponse(
            item.stockMovementItemId(),
            item.productPresentationId(),
            item.lotId(),
            item.lotNumber(),
            item.quantity(),
            item.unitCost(),
            item.quantityBefore(),
            item.quantityAfter(),
            item.manufacturedAt(),
            item.expiresAt()
        );
    }
}
