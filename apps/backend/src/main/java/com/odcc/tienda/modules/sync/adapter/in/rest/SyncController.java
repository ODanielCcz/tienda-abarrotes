package com.odcc.tienda.modules.sync.adapter.in.rest;

import com.odcc.tienda.modules.sync.adapter.in.rest.request.SyncRequests.AcknowledgeCheckpointRequest;
import com.odcc.tienda.modules.sync.adapter.in.rest.request.SyncRequests.IngestOperationRequest;
import com.odcc.tienda.modules.sync.adapter.in.rest.request.SyncRequests.ResolveConflictRequest;
import com.odcc.tienda.modules.sync.application.command.SyncCommands.AcknowledgeCheckpointCommand;
import com.odcc.tienda.modules.sync.application.command.SyncCommands.IngestOperationCommand;
import com.odcc.tienda.modules.sync.application.command.SyncCommands.ResolveConflictCommand;
import com.odcc.tienda.modules.sync.application.model.SyncModels.DeviceCheckpoint;
import com.odcc.tienda.modules.sync.application.model.SyncModels.OutboxBatch;
import com.odcc.tienda.modules.sync.application.model.SyncModels.SyncConflict;
import com.odcc.tienda.modules.sync.application.model.SyncModels.SyncOperation;
import com.odcc.tienda.modules.sync.application.port.in.SyncUseCases;
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
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@Tag(name = "Sincronizacion offline", description = "Inbox, outbox, checkpoints y conflictos de dispositivos")
public class SyncController {

    private final SyncUseCases useCases;

    @PostMapping("/inbox")
    @Operation(summary = "Recibir operacion offline idempotente")
    @PreAuthorize("hasAuthority('SYNC_INBOX_WRITE')")
    public ResponseEntity<ApiResponseDto<SyncOperation>> ingest(
        @Valid @RequestBody IngestOperationRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        SyncOperation operation = useCases.ingest(new IngestOperationCommand(
            request.operationId(), request.deviceId(), request.deviceSequence(), request.idempotencyKey(),
            request.operationType(), request.aggregateType(), request.aggregateId(), request.payload(),
            request.clientCreatedAt(), userId(jwt)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(
            HttpStatus.CREATED, "SYNC_OPERATION_RECEIVED", "Operacion Sync procesada correctamente",
            operation, servletRequest.getRequestURI()));
    }

    @GetMapping("/outbox")
    @Operation(summary = "Descargar eventos de outbox para un dispositivo")
    @PreAuthorize("hasAuthority('SYNC_OUTBOX_READ')")
    public ResponseEntity<ApiResponseDto<OutboxBatch>> outbox(
        @RequestParam UUID deviceId,
        @RequestParam(required = false) Long afterSequence,
        @RequestParam(defaultValue = "100") int limit,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        return ok("SYNC_OUTBOX_FOUND", "Eventos de sincronizacion consultados correctamente",
            useCases.getOutbox(deviceId, afterSequence, limit, userId(jwt)), servletRequest);
    }

    @GetMapping("/devices/{deviceId}/checkpoint")
    @Operation(summary = "Consultar checkpoint de dispositivo")
    @PreAuthorize("hasAuthority('SYNC_CHECKPOINT_READ')")
    public ResponseEntity<ApiResponseDto<DeviceCheckpoint>> checkpoint(
        @PathVariable UUID deviceId,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        return ok("SYNC_CHECKPOINT_FOUND", "Checkpoint consultado correctamente",
            useCases.getCheckpoint(deviceId, userId(jwt)), servletRequest);
    }

    @PostMapping("/devices/{deviceId}/checkpoint")
    @Operation(summary = "Confirmar cursor de outbox procesado")
    @PreAuthorize("hasAuthority('SYNC_CHECKPOINT_ACK')")
    public ResponseEntity<ApiResponseDto<DeviceCheckpoint>> acknowledge(
        @PathVariable UUID deviceId,
        @Valid @RequestBody AcknowledgeCheckpointRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        return ok("SYNC_CHECKPOINT_ACKNOWLEDGED", "Checkpoint confirmado correctamente",
            useCases.acknowledge(new AcknowledgeCheckpointCommand(deviceId, request.outboxSequence(), userId(jwt))),
            servletRequest);
    }

    @GetMapping("/conflicts")
    @Operation(summary = "Consultar conflictos de un dispositivo")
    @PreAuthorize("hasAuthority('SYNC_CONFLICT_READ')")
    public ResponseEntity<ApiResponseDto<List<SyncConflict>>> conflicts(
        @RequestParam UUID deviceId,
        @RequestParam(required = false) Boolean resolved,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        return ok("SYNC_CONFLICTS_FOUND", "Conflictos consultados correctamente",
            useCases.listConflicts(deviceId, resolved, userId(jwt)), servletRequest);
    }

    @PostMapping("/conflicts/{conflictId}/resolve")
    @Operation(summary = "Resolver conflicto de sincronizacion")
    @PreAuthorize("hasAuthority('SYNC_CONFLICT_RESOLVE')")
    public ResponseEntity<ApiResponseDto<SyncConflict>> resolve(
        @PathVariable UUID conflictId,
        @Valid @RequestBody ResolveConflictRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        return ok("SYNC_CONFLICT_RESOLVED", "Conflicto resuelto correctamente",
            useCases.resolveConflict(new ResolveConflictCommand(
                conflictId, request.resolution(), request.resolutionNotes(),
                request.mergedPayload(), userId(jwt))), servletRequest);
    }

    private UUID userId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new IllegalStateException("El JWT no contiene usuario");
        return UUID.fromString(jwt.getSubject());
    }

    private <T> ResponseEntity<ApiResponseDto<T>> ok(String code, String message, T data, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, code, message, data, request.getRequestURI()));
    }
}
