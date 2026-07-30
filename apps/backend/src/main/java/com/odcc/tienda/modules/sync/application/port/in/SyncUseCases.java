package com.odcc.tienda.modules.sync.application.port.in;

import com.odcc.tienda.modules.sync.application.command.SyncCommands.AcknowledgeCheckpointCommand;
import com.odcc.tienda.modules.sync.application.command.SyncCommands.IngestOperationCommand;
import com.odcc.tienda.modules.sync.application.command.SyncCommands.ResolveConflictCommand;
import com.odcc.tienda.modules.sync.application.model.SyncModels.DeviceCheckpoint;
import com.odcc.tienda.modules.sync.application.model.SyncModels.OutboxBatch;
import com.odcc.tienda.modules.sync.application.model.SyncModels.SyncConflict;
import com.odcc.tienda.modules.sync.application.model.SyncModels.SyncOperation;

import java.util.List;
import java.util.UUID;

public interface SyncUseCases {
    SyncOperation ingest(IngestOperationCommand command);
    OutboxBatch getOutbox(UUID deviceId, Long afterSequence, int limit, UUID actorUserId);
    DeviceCheckpoint getCheckpoint(UUID deviceId, UUID actorUserId);
    DeviceCheckpoint acknowledge(AcknowledgeCheckpointCommand command);
    List<SyncConflict> listConflicts(UUID deviceId, Boolean resolved, UUID actorUserId);
    SyncConflict resolveConflict(ResolveConflictCommand command);
}
