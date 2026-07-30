package com.odcc.tienda.modules.sync.application.command;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class SyncCommands {

    private SyncCommands() {
    }

    public record IngestOperationCommand(
        UUID operationId,
        UUID deviceId,
        long deviceSequence,
        UUID idempotencyKey,
        String operationType,
        String aggregateType,
        UUID aggregateId,
        Map<String, Object> payload,
        Instant clientCreatedAt,
        UUID actorUserId
    ) {
    }

    public record AcknowledgeCheckpointCommand(
        UUID deviceId,
        long outboxSequence,
        UUID actorUserId
    ) {
    }

    public record ResolveConflictCommand(
        UUID conflictId,
        String resolution,
        String resolutionNotes,
        Map<String, Object> mergedPayload,
        UUID actorUserId
    ) {
    }
}
