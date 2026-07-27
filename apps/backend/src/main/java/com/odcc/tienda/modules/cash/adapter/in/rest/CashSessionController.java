package com.odcc.tienda.modules.cash.adapter.in.rest;

import com.odcc.tienda.modules.cash.adapter.in.rest.request.CloseCashSessionRequest;
import com.odcc.tienda.modules.cash.adapter.in.rest.request.CreateCashMovementRequest;
import com.odcc.tienda.modules.cash.adapter.in.rest.request.OpenCashSessionRequest;
import com.odcc.tienda.modules.cash.application.command.CloseCashSessionCommand;
import com.odcc.tienda.modules.cash.application.command.CreateCashMovementCommand;
import com.odcc.tienda.modules.cash.application.command.OpenCashSessionCommand;
import com.odcc.tienda.modules.cash.application.model.CashMovement;
import com.odcc.tienda.modules.cash.application.model.CashSession;
import com.odcc.tienda.modules.cash.application.port.in.CashSessionUseCases;
import com.odcc.tienda.modules.cash.application.query.ListCashSessionsQuery;
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
@RequestMapping("/api/v1/cash/sessions")
@RequiredArgsConstructor
@Tag(name = "Caja", description = "Apertura, cierre y movimientos de caja")
public class CashSessionController {

    private final CashSessionUseCases useCases;

    @PostMapping("/open")
    @Operation(summary = "Abrir sesion de caja")
    @PreAuthorize("hasAuthority('CASH_SESSION_OPEN')")
    public ResponseEntity<ApiResponseDto<CashSession>> open(@Valid @RequestBody OpenCashSessionRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        CashSession session = useCases.open(new OpenCashSessionCommand(request.cashRegisterId(), currentUserId(jwt), request.openingAmount(), request.notes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(HttpStatus.CREATED, "CASH_SESSION_OPENED", "Caja abierta correctamente", session, servletRequest.getRequestURI()));
    }

    @GetMapping("/{cashSessionId}")
    @Operation(summary = "Consultar sesion de caja")
    @PreAuthorize("hasAuthority('CASH_SESSION_READ')")
    public ResponseEntity<ApiResponseDto<CashSession>> getById(@PathVariable UUID cashSessionId, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "CASH_SESSION_FOUND", "Sesion de caja consultada correctamente", useCases.getById(cashSessionId), servletRequest.getRequestURI()));
    }

    @GetMapping
    @Operation(summary = "Listar sesiones de caja")
    @PreAuthorize("hasAuthority('CASH_SESSION_READ')")
    public ResponseEntity<ApiResponseDto<List<CashSession>>> list(@RequestParam(required = false) UUID cashRegisterId, @RequestParam(required = false) String status, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "CASH_SESSIONS_FOUND", "Sesiones de caja consultadas correctamente", useCases.list(new ListCashSessionsQuery(cashRegisterId, status)), servletRequest.getRequestURI()));
    }

    @PostMapping("/{cashSessionId}/close")
    @Operation(summary = "Cerrar sesion de caja")
    @PreAuthorize("hasAuthority('CASH_SESSION_CLOSE')")
    public ResponseEntity<ApiResponseDto<CashSession>> close(@PathVariable UUID cashSessionId, @Valid @RequestBody CloseCashSessionRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        CashSession session = useCases.close(new CloseCashSessionCommand(cashSessionId, currentUserId(jwt), request.countedCashAmount(), request.notes()));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "CASH_SESSION_CLOSED", "Caja cerrada correctamente", session, servletRequest.getRequestURI()));
    }

    @GetMapping("/{cashSessionId}/movements")
    @Operation(summary = "Consultar movimientos de una sesion de caja")
    @PreAuthorize("hasAuthority('CASH_MOVEMENT_READ')")
    public ResponseEntity<ApiResponseDto<List<CashMovement>>> listMovements(@PathVariable UUID cashSessionId, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "CASH_MOVEMENTS_FOUND", "Movimientos de caja consultados correctamente", useCases.listMovements(cashSessionId), servletRequest.getRequestURI()));
    }

    @PostMapping("/{cashSessionId}/movements")
    @Operation(summary = "Registrar movimiento manual de caja")
    @PreAuthorize("hasAuthority('CASH_MOVEMENT_CREATE')")
    public ResponseEntity<ApiResponseDto<CashMovement>> createMovement(@PathVariable UUID cashSessionId, @Valid @RequestBody CreateCashMovementRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        CashMovement movement = useCases.createMovement(new CreateCashMovementCommand(cashSessionId, request.movementType(), request.direction(), request.amount(), request.reference(), request.reason(), currentUserId(jwt)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(HttpStatus.CREATED, "CASH_MOVEMENT_CREATED", "Movimiento de caja registrado correctamente", movement, servletRequest.getRequestURI()));
    }

    private UUID currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new IllegalArgumentException("No se pudo identificar al usuario autenticado");
        return UUID.fromString(jwt.getSubject());
    }
}