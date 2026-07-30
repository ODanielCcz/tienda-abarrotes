package com.odcc.tienda.modules.sync.adapter.in.rest.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class SyncRequests {

    private SyncRequests() {
    }

    public record IngestOperationRequest(
        @NotNull UUID operationId,
        @NotNull UUID deviceId,
        @Min(1) long deviceSequence,
        @NotNull UUID idempotencyKey,
        @NotBlank String operationType,
        @NotBlank String aggregateType,
        UUID aggregateId,
        @NotNull Map<String, Object> payload,
        @NotNull Instant clientCreatedAt
    ) {
    }

    public record AcknowledgeCheckpointRequest(@Min(0) long outboxSequence) {
    }

    public record ResolveConflictRequest(
        @NotBlank String resolution,
        String resolutionNotes,
        Map<String, Object> mergedPayload
    ) {
    }
}
