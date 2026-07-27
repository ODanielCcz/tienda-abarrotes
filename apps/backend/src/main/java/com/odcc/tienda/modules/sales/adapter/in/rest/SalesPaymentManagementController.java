package com.odcc.tienda.modules.sales.adapter.in.rest;

import com.odcc.tienda.modules.sales.application.model.SalesPayment;
import com.odcc.tienda.modules.sales.application.port.in.SalesPaymentUseCases;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales/payments")
@RequiredArgsConstructor
@Tag(name = "Pagos de ventas", description = "Consulta y cancelacion de pagos")
public class SalesPaymentManagementController {

    private final SalesPaymentUseCases useCases;

    @GetMapping("/{paymentId}")
    @Operation(summary = "Consultar pago por id")
    @PreAuthorize("hasAuthority('SALES_PAYMENT_READ')")
    public ResponseEntity<ApiResponseDto<SalesPayment>> getById(@PathVariable UUID paymentId, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "SALES_PAYMENT_FOUND", "Pago consultado correctamente", useCases.getById(paymentId), servletRequest.getRequestURI()));
    }

    @PostMapping("/{paymentId}/cancel")
    @Operation(summary = "Cancelar pago capturado")
    @PreAuthorize("hasAuthority('SALES_PAYMENT_CANCEL')")
    public ResponseEntity<ApiResponseDto<SalesPayment>> cancel(@PathVariable UUID paymentId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        SalesPayment payment = useCases.cancel(paymentId, currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "SALES_PAYMENT_CANCELLED", "Pago cancelado correctamente", payment, servletRequest.getRequestURI()));
    }

    private UUID currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new IllegalArgumentException("No se pudo identificar al usuario autenticado");
        return UUID.fromString(jwt.getSubject());
    }
}