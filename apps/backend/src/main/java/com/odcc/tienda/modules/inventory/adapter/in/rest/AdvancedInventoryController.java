package com.odcc.tienda.modules.inventory.adapter.in.rest;

import com.odcc.tienda.modules.inventory.adapter.in.rest.mapper.AdvancedInventoryRestMapper;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.CreateInventoryAdjustmentRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.CreateInventoryCountRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.CreateInventoryTransferRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.CreateReservationRequest;
import com.odcc.tienda.modules.inventory.application.model.InventoryCountView;
import com.odcc.tienda.modules.inventory.application.model.LotView;
import com.odcc.tienda.modules.inventory.application.model.ReservationView;
import com.odcc.tienda.modules.inventory.application.model.StockMovementView;
import com.odcc.tienda.modules.inventory.application.port.in.AdvancedInventoryUseCases;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventario avanzado", description = "Ajustes, traspasos, conteos, reservas y caducidades")
public class AdvancedInventoryController {

    private final AdvancedInventoryUseCases useCases;
    private final AdvancedInventoryRestMapper mapper;

    @PostMapping("/adjustments")
    @Operation(summary = "Crear ajuste manual de inventario")
    @PreAuthorize("hasAuthority('INVENTORY_ADJUSTMENT_CREATE')")
    public ResponseEntity<ApiResponseDto<StockMovementView>> adjust(
        @Valid @RequestBody CreateInventoryAdjustmentRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        StockMovementView movement = useCases.adjust(
            mapper.toAdjustmentCommand(request, currentUserId(jwt))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(HttpStatus.CREATED, "INVENTORY_ADJUSTMENT_CREATED", "Ajuste de inventario creado correctamente", movement, servletRequest.getRequestURI()));
    }

    @PostMapping("/transfers")
    @Operation(summary = "Crear traspaso entre almacenes")
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER_CREATE')")
    public ResponseEntity<ApiResponseDto<List<StockMovementView>>> transfer(
        @Valid @RequestBody CreateInventoryTransferRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        List<StockMovementView> movements = useCases.transfer(
            mapper.toTransferCommand(request, currentUserId(jwt))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(HttpStatus.CREATED, "INVENTORY_TRANSFER_CREATED", "Traspaso de inventario creado correctamente", movements, servletRequest.getRequestURI()));
    }

    @PostMapping("/counts")
    @Operation(summary = "Crear conteo fisico")
    @PreAuthorize("hasAuthority('INVENTORY_COUNT_CREATE')")
    public ResponseEntity<ApiResponseDto<InventoryCountView>> createCount(
        @Valid @RequestBody CreateInventoryCountRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        InventoryCountView count = useCases.createCount(
            mapper.toCountCommand(request, currentUserId(jwt))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(HttpStatus.CREATED, "INVENTORY_COUNT_CREATED", "Conteo fisico creado correctamente", count, servletRequest.getRequestURI()));
    }

    @PostMapping("/counts/{inventoryCountId}/confirm")
    @Operation(summary = "Confirmar conteo fisico y ajustar diferencias")
    @PreAuthorize("hasAuthority('INVENTORY_COUNT_CONFIRM')")
    public ResponseEntity<ApiResponseDto<InventoryCountView>> confirmCount(
        @PathVariable UUID inventoryCountId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        InventoryCountView count = useCases.confirmCount(
            mapper.toConfirmCountCommand(inventoryCountId, currentUserId(jwt))
        );
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "INVENTORY_COUNT_CONFIRMED", "Conteo fisico confirmado correctamente", count, servletRequest.getRequestURI()));
    }

    @PostMapping("/reservations")
    @Operation(summary = "Crear reserva de inventario")
    @PreAuthorize("hasAuthority('INVENTORY_RESERVATION_CREATE')")
    public ResponseEntity<ApiResponseDto<ReservationView>> reserve(
        @Valid @RequestBody CreateReservationRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        ReservationView reservation = useCases.reserve(
            mapper.toReservationCommand(request, currentUserId(jwt))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(HttpStatus.CREATED, "INVENTORY_RESERVATION_CREATED", "Reserva de inventario creada correctamente", reservation, servletRequest.getRequestURI()));
    }

    @PostMapping("/reservations/{reservationId}/release")
    @Operation(summary = "Liberar reserva de inventario")
    @PreAuthorize("hasAuthority('INVENTORY_RESERVATION_RELEASE')")
    public ResponseEntity<ApiResponseDto<ReservationView>> releaseReservation(
        @PathVariable UUID reservationId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        ReservationView reservation = useCases.releaseReservation(
            mapper.toReleaseReservationCommand(reservationId, currentUserId(jwt))
        );
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "INVENTORY_RESERVATION_RELEASED", "Reserva de inventario liberada correctamente", reservation, servletRequest.getRequestURI()));
    }

    @GetMapping("/expiring-lots")
    @Operation(summary = "Consultar lotes proximos a caducar")
    @PreAuthorize("hasAuthority('INVENTORY_EXPIRING_LOT_READ')")
    public ResponseEntity<ApiResponseDto<List<LotView>>> expiringLots(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresBefore,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        List<LotView> lots = useCases.findExpiringLots(expiresBefore, currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "INVENTORY_EXPIRING_LOTS_FOUND", "Lotes proximos a caducar consultados correctamente", lots, servletRequest.getRequestURI()));
    }

    private UUID currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new IllegalArgumentException("No se pudo identificar al usuario autenticado");
        return UUID.fromString(jwt.getSubject());
    }
}
