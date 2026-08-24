package com.odcc.tienda.modules.purchasing.adapter.in.rest;

import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;
import com.odcc.tienda.modules.purchasing.adapter.in.rest.mapper.PurchaseRestMapper;
import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.CreatePurchaseRequest;
import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.ReceivePurchaseRequest;
import com.odcc.tienda.modules.purchasing.application.model.Purchase;
import com.odcc.tienda.modules.purchasing.application.port.in.PurchaseUseCases;
import com.odcc.tienda.modules.purchasing.application.query.ListPurchasesQuery;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchasing/purchases")
@RequiredArgsConstructor
@Tag(name = "Compras", description = "Registro, confirmacion y recepcion de compras")
public class PurchaseController {

    private final PurchaseUseCases useCases;
    private final PurchaseRestMapper mapper;

    @PostMapping
    @Operation(summary = "Crear compra")
    @PreAuthorize("hasAuthority('PURCHASING_PURCHASE_CREATE')")
    public ResponseEntity<ApiResponseDto<Purchase>> create(@Valid @RequestBody CreatePurchaseRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        Purchase purchase = useCases.create(
            mapper.toCreateCommand(request),
            currentUserId(jwt)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(HttpStatus.CREATED, "PURCHASE_CREATED", "Compra creada correctamente", purchase, servletRequest.getRequestURI()));
    }

    @GetMapping
    @Operation(summary = "Listar compras")
    @PreAuthorize("hasAuthority('PURCHASING_PURCHASE_READ')")
    public ResponseEntity<ApiResponseDto<List<Purchase>>> list(@RequestParam(required = false) UUID supplierId, @RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) String status, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        List<Purchase> purchases = useCases.list(new ListPurchasesQuery(supplierId, warehouseId, status), currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "PURCHASES_FOUND", "Compras consultadas correctamente", purchases, servletRequest.getRequestURI()));
    }

    @GetMapping("/{purchaseId}")
    @Operation(summary = "Consultar compra por id")
    @PreAuthorize("hasAuthority('PURCHASING_PURCHASE_READ')")
    public ResponseEntity<ApiResponseDto<Purchase>> getById(@PathVariable UUID purchaseId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        Purchase purchase = useCases.getById(purchaseId, currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "PURCHASE_FOUND", "Compra consultada correctamente", purchase, servletRequest.getRequestURI()));
    }

    @PostMapping("/{purchaseId}/confirm")
    @Operation(summary = "Confirmar compra")
    @PreAuthorize("hasAuthority('PURCHASING_PURCHASE_CONFIRM')")
    public ResponseEntity<ApiResponseDto<Purchase>> confirm(@PathVariable UUID purchaseId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        Purchase purchase = useCases.confirm(purchaseId, currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "PURCHASE_CONFIRMED", "Compra confirmada correctamente", purchase, servletRequest.getRequestURI()));
    }

    @PostMapping("/{purchaseId}/receive")
    @Operation(summary = "Recibir mercancia de compra")
    @PreAuthorize("hasAuthority('PURCHASING_PURCHASE_RECEIVE')")
    public ResponseEntity<ApiResponseDto<InventoryReceipt>> receive(@PathVariable UUID purchaseId, @Valid @RequestBody ReceivePurchaseRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        InventoryReceipt receipt = useCases.receive(
            mapper.toReceiveCommand(request, purchaseId),
            currentUserId(jwt)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(HttpStatus.CREATED, "PURCHASE_RECEIVED", "Compra recibida correctamente", receipt, servletRequest.getRequestURI()));
    }

    private UUID currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new IllegalArgumentException("No se pudo identificar al usuario autenticado");
        return UUID.fromString(jwt.getSubject());
    }
}
