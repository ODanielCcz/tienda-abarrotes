package com.odcc.tienda.modules.sync.application.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SyncModels {

    private SyncModels() {
    }

    public record DeviceContext(
        UUID deviceId,
        UUID branchId,
        UUID warehouseId,
        String deviceType,
        String status
    ) {
    }

    public record SyncOperation(
        UUID operationId,
        UUID deviceId,
        long deviceSequence,
        UUID idempotencyKey,
        String requestHash,
        String operationType,
        String aggregateType,
        UUID aggregateId,
        Map<String, Object> payload,
        Instant clientCreatedAt,
        Instant receivedAt,
        Instant processedAt,
        String status,
        Map<String, Object> result,
        String errorCode,
        String errorMessage
    ) {
    }

    public record DeviceCheckpoint(
        UUID deviceId,
        long lastReceivedSequence,
        long lastProcessedSequence,
        long lastAcknowledgedOutboxSequence,
        Instant lastSyncAt,
        Instant updatedAt
    ) {
    }

    public record OutboxEvent(
        UUID outboxEventId,
        long globalSequence,
        UUID branchId,
        UUID warehouseId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        Map<String, Object> payload,
        UUID correlationId,
        Instant createdAt
    ) {
    }

    public record OutboxBatch(
        UUID deviceId,
        long afterSequence,
        long nextSequence,
        List<OutboxEvent> events
    ) {
    }

    public record SyncConflict(
        UUID conflictId,
        UUID operationId,
        UUID deviceId,
        long deviceSequence,
        String operationType,
        String conflictType,
        Map<String, Object> serverState,
        Map<String, Object> clientState,
        String resolution,
        String resolutionNotes,
        UUID resolvedBy,
        Instant createdAt,
        Instant resolvedAt
    ) {
    }
}
