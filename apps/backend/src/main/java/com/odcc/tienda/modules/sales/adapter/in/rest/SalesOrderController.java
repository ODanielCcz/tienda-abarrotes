package com.odcc.tienda.modules.sales.adapter.in.rest;

import com.odcc.tienda.modules.sales.adapter.in.rest.request.CreateSalesOrderItemRequest;
import com.odcc.tienda.modules.sales.adapter.in.rest.request.CreateSalesOrderRequest;
import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderItemCommand;
import com.odcc.tienda.modules.sales.application.model.SalesOrder;
import com.odcc.tienda.modules.sales.application.port.in.SalesOrderUseCases;
import com.odcc.tienda.modules.sales.application.query.ListSalesOrdersQuery;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/sales/orders")
@RequiredArgsConstructor
@Tag(name = "Ventas", description = "Ordenes de venta con descuento real de inventario")
public class SalesOrderController {

    private final SalesOrderUseCases useCases;

    @PostMapping
    @Operation(summary = "Crear venta confirmada")
    @PreAuthorize("hasAuthority('SALES_ORDER_CREATE')")
    public ResponseEntity<ApiResponseDto<SalesOrder>> create(
        @Valid @RequestBody CreateSalesOrderRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        SalesOrder salesOrder = useCases.create(new CreateSalesOrderCommand(
            request.warehouseId(),
            request.customerId(),
            request.deviceId(),
            request.channel(),
            request.currencyCode(),
            request.idempotencyKey(),
            request.items().stream().map(this::toItemCommand).toList()
        ), currentUserId(jwt));

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(
            HttpStatus.CREATED,
            "SALES_ORDER_CREATED",
            "Venta creada correctamente",
            salesOrder,
            servletRequest.getRequestURI()
        ));
    }

    @GetMapping
    @Operation(summary = "Listar ventas")
    @PreAuthorize("hasAuthority('SALES_ORDER_READ')")
    public ResponseEntity<ApiResponseDto<List<SalesOrder>>> list(
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) String status,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        List<SalesOrder> salesOrders = useCases.list(new ListSalesOrdersQuery(warehouseId, customerId, status), currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "SALES_ORDERS_FOUND",
            "Ventas consultadas correctamente",
            salesOrders,
            servletRequest.getRequestURI()
        ));
    }

    @GetMapping("/{salesOrderId}")
    @Operation(summary = "Consultar venta por id")
    @PreAuthorize("hasAuthority('SALES_ORDER_READ')")
    public ResponseEntity<ApiResponseDto<SalesOrder>> getById(
        @PathVariable UUID salesOrderId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        SalesOrder salesOrder = useCases.getById(salesOrderId, currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "SALES_ORDER_FOUND",
            "Venta consultada correctamente",
            salesOrder,
            servletRequest.getRequestURI()
        ));
    }

    @PostMapping("/{salesOrderId}/cancel")
    @Operation(summary = "Cancelar venta y reponer inventario")
    @PreAuthorize("hasAuthority('SALES_ORDER_CANCEL')")
    public ResponseEntity<ApiResponseDto<SalesOrder>> cancel(
        @PathVariable UUID salesOrderId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        SalesOrder salesOrder = useCases.cancel(salesOrderId, currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "SALES_ORDER_CANCELLED",
            "Venta cancelada correctamente",
            salesOrder,
            servletRequest.getRequestURI()
        ));
    }

    private CreateSalesOrderItemCommand toItemCommand(CreateSalesOrderItemRequest request) {
        return new CreateSalesOrderItemCommand(
            request.productPresentationId(),
            request.quantity(),
            request.unitPrice(),
            request.discountAmount()
        );
    }

    private static UUID currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new IllegalStateException("El JWT no contiene usuario");
        return UUID.fromString(jwt.getSubject());
    }
}
