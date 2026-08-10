package com.odcc.tienda.modules.inventory.adapter.in.rest;

import com.odcc.tienda.modules.inventory.application.model.LotView;
import com.odcc.tienda.modules.inventory.application.model.PalletView;
import com.odcc.tienda.modules.inventory.application.model.StockBalanceView;
import com.odcc.tienda.modules.inventory.application.model.StockMovementView;
import com.odcc.tienda.modules.inventory.application.port.in.InventoryQueriesUseCase;
import com.odcc.tienda.modules.inventory.application.query.LotQuery;
import com.odcc.tienda.modules.inventory.application.query.PalletQuery;
import com.odcc.tienda.modules.inventory.application.query.StockMovementQuery;
import com.odcc.tienda.modules.inventory.application.query.StockQuery;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventario", description = "Consultas operativas de stock, lotes, pallets y movimientos")
public class InventoryQueryController {

    private final InventoryQueriesUseCase queries;

    @GetMapping("/stock")
    @Operation(summary = "Consultar stock por almacen y/o presentacion")
    @PreAuthorize("hasAuthority('INVENTORY_STOCK_READ')")
    public ResponseEntity<ApiResponseDto<List<StockBalanceView>>> findStock(
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) UUID productPresentationId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        List<StockBalanceView> stock = queries.findStock(new StockQuery(warehouseId, productPresentationId), currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "INVENTORY_STOCK_FOUND", "Stock consultado correctamente", stock, servletRequest.getRequestURI()));
    }

    @GetMapping("/stock/{productPresentationId}")
    @Operation(summary = "Consultar stock de una presentacion")
    @PreAuthorize("hasAuthority('INVENTORY_STOCK_READ')")
    public ResponseEntity<ApiResponseDto<List<StockBalanceView>>> findStockByPresentation(
        @PathVariable UUID productPresentationId,
        @RequestParam(required = false) UUID warehouseId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        List<StockBalanceView> stock = queries.findStock(new StockQuery(warehouseId, productPresentationId), currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "INVENTORY_STOCK_FOUND", "Stock de presentacion consultado correctamente", stock, servletRequest.getRequestURI()));
    }

    @GetMapping("/lots")
    @Operation(summary = "Consultar lotes")
    @PreAuthorize("hasAuthority('INVENTORY_LOT_READ')")
    public ResponseEntity<ApiResponseDto<List<LotView>>> findLots(
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) UUID productPresentationId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) LocalDate expiresBefore,
        @RequestParam(required = false) LocalDate expiresAfter,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        List<LotView> lots = queries.findLots(new LotQuery(warehouseId, productPresentationId, status, expiresBefore, expiresAfter), currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "INVENTORY_LOTS_FOUND", "Lotes consultados correctamente", lots, servletRequest.getRequestURI()));
    }

    @GetMapping("/lots/{lotId}")
    @Operation(summary = "Consultar lote por id")
    @PreAuthorize("hasAuthority('INVENTORY_LOT_READ')")
    public ResponseEntity<ApiResponseDto<LotView>> getLotById(
        @PathVariable UUID lotId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        LotView lot = queries.getLotById(lotId, currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "INVENTORY_LOT_FOUND", "Lote consultado correctamente", lot, servletRequest.getRequestURI()));
    }

    @GetMapping("/pallets")
    @Operation(summary = "Consultar pallets")
    @PreAuthorize("hasAuthority('INVENTORY_PALLET_READ')")
    public ResponseEntity<ApiResponseDto<List<PalletView>>> findPallets(
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) String status,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        List<PalletView> pallets = queries.findPallets(new PalletQuery(warehouseId, status), currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "INVENTORY_PALLETS_FOUND", "Pallets consultados correctamente", pallets, servletRequest.getRequestURI()));
    }

    @GetMapping("/pallets/{palletId}")
    @Operation(summary = "Consultar pallet por id")
    @PreAuthorize("hasAuthority('INVENTORY_PALLET_READ')")
    public ResponseEntity<ApiResponseDto<PalletView>> getPalletById(
        @PathVariable UUID palletId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        PalletView pallet = queries.getPalletById(palletId, currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "INVENTORY_PALLET_FOUND", "Pallet consultado correctamente", pallet, servletRequest.getRequestURI()));
    }

    @GetMapping("/movements")
    @Operation(summary = "Consultar movimientos de inventario")
    @PreAuthorize("hasAuthority('INVENTORY_MOVEMENT_READ')")
    public ResponseEntity<ApiResponseDto<List<StockMovementView>>> findMovements(
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) String movementType,
        @RequestParam(required = false) String status,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        List<StockMovementView> movements = queries.findMovements(new StockMovementQuery(warehouseId, movementType, status), currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "INVENTORY_MOVEMENTS_FOUND", "Movimientos de inventario consultados correctamente", movements, servletRequest.getRequestURI()));
    }

    @GetMapping("/movements/{movementId}")
    @Operation(summary = "Consultar movimiento de inventario por id")
    @PreAuthorize("hasAuthority('INVENTORY_MOVEMENT_READ')")
    public ResponseEntity<ApiResponseDto<StockMovementView>> getMovementById(
        @PathVariable UUID movementId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        StockMovementView movement = queries.getMovementById(movementId, currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "INVENTORY_MOVEMENT_FOUND", "Movimiento de inventario consultado correctamente", movement, servletRequest.getRequestURI()));
    }

    private UUID currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new IllegalArgumentException("No se pudo identificar al usuario autenticado");
        return UUID.fromString(jwt.getSubject());
    }
}
