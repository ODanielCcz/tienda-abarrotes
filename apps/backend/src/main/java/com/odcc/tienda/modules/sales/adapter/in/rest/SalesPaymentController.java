package com.odcc.tienda.modules.sales.adapter.in.rest;

import com.odcc.tienda.modules.sales.adapter.in.rest.mapper.SalesPaymentRestMapper;
import com.odcc.tienda.modules.sales.adapter.in.rest.request.CreateSalesPaymentRequest;
import com.odcc.tienda.modules.sales.application.model.SalesPayment;
import com.odcc.tienda.modules.sales.application.port.in.SalesPaymentUseCases;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales/orders/{salesOrderId}/payments")
@RequiredArgsConstructor
@Tag(name = "Pagos de ventas", description = "Registro y consulta de pagos asociados a ventas")
public class SalesPaymentController {

    private final SalesPaymentUseCases useCases;
    private final SalesPaymentRestMapper mapper;

    @PostMapping
    @Operation(summary = "Registrar pago de venta")
    @PreAuthorize("hasAuthority('SALES_PAYMENT_CREATE')")
    public ResponseEntity<ApiResponseDto<SalesPayment>> create(
        @PathVariable UUID salesOrderId,
        @Valid @RequestBody CreateSalesPaymentRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        SalesPayment payment = useCases.create(
            mapper.toCreateCommand(salesOrderId, request, currentUserId(jwt))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(HttpStatus.CREATED, "SALES_PAYMENT_CREATED", "Pago registrado correctamente", payment, servletRequest.getRequestURI()));
    }

    @GetMapping
    @Operation(summary = "Consultar pagos de una venta")
    @PreAuthorize("hasAuthority('SALES_PAYMENT_READ')")
    public ResponseEntity<ApiResponseDto<List<SalesPayment>>> list(@PathVariable UUID salesOrderId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "SALES_PAYMENTS_FOUND", "Pagos consultados correctamente", useCases.listBySalesOrder(salesOrderId, currentUserId(jwt)), servletRequest.getRequestURI()));
    }

    private UUID currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new IllegalArgumentException("No se pudo identificar al usuario autenticado");
        return UUID.fromString(jwt.getSubject());
    }
}
