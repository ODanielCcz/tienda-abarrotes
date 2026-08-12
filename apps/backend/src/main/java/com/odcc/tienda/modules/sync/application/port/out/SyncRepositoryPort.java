package com.odcc.tienda.modules.sync.application.port.out;

import com.odcc.tienda.modules.sync.application.command.SyncCommands.IngestOperationCommand;
import com.odcc.tienda.modules.sync.application.model.SyncModels.DeviceCheckpoint;
import com.odcc.tienda.modules.sync.application.model.SyncModels.DeviceContext;
import com.odcc.tienda.modules.sync.application.model.SyncModels.OutboxEvent;
import com.odcc.tienda.modules.sync.application.model.SyncModels.SyncConflict;
import com.odcc.tienda.modules.sync.application.model.SyncModels.SyncOperation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SyncRepositoryPort {
    Optional<DeviceContext> findDevice(UUID deviceId);
    boolean userOwnsDevice(UUID userId, UUID deviceId);
    boolean userCanAccessBranch(UUID userId, UUID branchId);
    Optional<SyncOperation> findByIdempotencyKey(UUID idempotencyKey);
    Optional<SyncOperation> findByDeviceSequence(UUID deviceId, long deviceSequence);
    SyncOperation insertReceived(IngestOperationCommand command, String requestHash);
    SyncOperation markAccepted(UUID operationId, UUID aggregateId, Map<String, Object> result);
    SyncOperation markRejected(UUID operationId, String errorCode, String errorMessage);
    SyncOperation markConflict(UUID operationId, String errorCode, String errorMessage);
    DeviceCheckpoint getOrCreateCheckpoint(UUID deviceId);
    DeviceCheckpoint advanceCheckpoint(UUID deviceId, long sequence);
    DeviceCheckpoint acknowledgeOutbox(UUID deviceId, long sequence);
    long maxOutboxSequence(UUID branchId);
    List<OutboxEvent> findOutbox(UUID branchId, long afterSequence, int limit);
    SyncConflict createConflict(UUID operationId, String conflictType, Map<String, Object> serverState, Map<String, Object> clientState);
    List<SyncConflict> findConflicts(UUID deviceId, Boolean resolved);
    Optional<SyncConflict> findConflict(UUID conflictId);
    Optional<SyncOperation> findOperation(UUID operationId);
    SyncConflict resolveConflict(UUID conflictId, String resolution, String notes, UUID resolvedBy);
}
