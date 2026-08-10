package com.odcc.tienda.modules.sales.adapter.in.rest;

import com.odcc.tienda.modules.sales.adapter.in.rest.request.ConfirmSalesReturnRequest;
import com.odcc.tienda.modules.sales.adapter.in.rest.request.CreateSalesReturnItemRequest;
import com.odcc.tienda.modules.sales.adapter.in.rest.request.CreateSalesReturnRequest;
import com.odcc.tienda.modules.sales.application.command.ConfirmSalesReturnCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesReturnCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesReturnItemCommand;
import com.odcc.tienda.modules.sales.application.model.SalesReturn;
import com.odcc.tienda.modules.sales.application.port.in.SalesReturnUseCases;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Devoluciones de ventas", description = "Creacion, confirmacion y cancelacion de devoluciones")
public class SalesReturnController {

    private final SalesReturnUseCases useCases;

    @PostMapping("/sales/orders/{salesOrderId}/returns")
    @Operation(summary = "Crear borrador de devolucion de venta")
    @PreAuthorize("hasAuthority('SALES_RETURN_CREATE')")
    public ResponseEntity<ApiResponseDto<SalesReturn>> create(
        @PathVariable UUID salesOrderId,
        @Valid @RequestBody CreateSalesReturnRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        SalesReturn salesReturn = useCases.create(new CreateSalesReturnCommand(
            salesOrderId,
            request.reason(),
            currentUserId(jwt),
            request.items().stream().map(this::toItemCommand).toList()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(HttpStatus.CREATED, "SALES_RETURN_CREATED", "Devolucion creada correctamente", salesReturn, servletRequest.getRequestURI()));
    }

    @GetMapping("/sales/returns/{returnId}")
    @Operation(summary = "Consultar devolucion por id")
    @PreAuthorize("hasAuthority('SALES_RETURN_READ')")
    public ResponseEntity<ApiResponseDto<SalesReturn>> getById(@PathVariable UUID returnId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "SALES_RETURN_FOUND", "Devolucion consultada correctamente", useCases.getById(returnId, currentUserId(jwt)), servletRequest.getRequestURI()));
    }

    @PostMapping("/sales/returns/{returnId}/confirm")
    @Operation(summary = "Confirmar devolucion y reponer inventario")
    @PreAuthorize("hasAuthority('SALES_RETURN_CONFIRM')")
    public ResponseEntity<ApiResponseDto<SalesReturn>> confirm(
        @PathVariable UUID returnId,
        @RequestBody(required = false) ConfirmSalesReturnRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        UUID cashSessionId = request == null ? null : request.cashSessionId();
        SalesReturn salesReturn = useCases.confirm(new ConfirmSalesReturnCommand(returnId, cashSessionId, currentUserId(jwt)));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "SALES_RETURN_CONFIRMED", "Devolucion confirmada correctamente", salesReturn, servletRequest.getRequestURI()));
    }

    @PostMapping("/sales/returns/{returnId}/cancel")
    @Operation(summary = "Cancelar borrador de devolucion")
    @PreAuthorize("hasAuthority('SALES_RETURN_CANCEL')")
    public ResponseEntity<ApiResponseDto<SalesReturn>> cancel(@PathVariable UUID returnId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        SalesReturn salesReturn = useCases.cancel(returnId, currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "SALES_RETURN_CANCELLED", "Devolucion cancelada correctamente", salesReturn, servletRequest.getRequestURI()));
    }

    private CreateSalesReturnItemCommand toItemCommand(CreateSalesReturnItemRequest request) {
        return new CreateSalesReturnItemCommand(request.salesOrderItemId(), request.quantity());
    }

    private UUID currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new IllegalArgumentException("No se pudo identificar al usuario autenticado");
        return UUID.fromString(jwt.getSubject());
    }
}
