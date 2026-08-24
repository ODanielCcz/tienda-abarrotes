package com.odcc.tienda.modules.sync.adapter.in.rest.mapper;

import com.odcc.tienda.modules.sync.adapter.in.rest.request.SyncRequests.AcknowledgeCheckpointRequest;
import com.odcc.tienda.modules.sync.adapter.in.rest.request.SyncRequests.IngestOperationRequest;
import com.odcc.tienda.modules.sync.adapter.in.rest.request.SyncRequests.ResolveConflictRequest;
import com.odcc.tienda.modules.sync.application.command.SyncCommands.AcknowledgeCheckpointCommand;
import com.odcc.tienda.modules.sync.application.command.SyncCommands.IngestOperationCommand;
import com.odcc.tienda.modules.sync.application.command.SyncCommands.ResolveConflictCommand;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(config = CentralMapperConfig.class)
public interface SyncRestMapper {

    @Mapping(target = "actorUserId", source = "actorUserId")
    IngestOperationCommand toIngestCommand(
        IngestOperationRequest request,
        UUID actorUserId
    );

    @Mapping(target = "deviceId", source = "deviceId")
    @Mapping(target = "actorUserId", source = "actorUserId")
    AcknowledgeCheckpointCommand toAcknowledgeCommand(
        UUID deviceId,
        AcknowledgeCheckpointRequest request,
        UUID actorUserId
    );

    @Mapping(target = "conflictId", source = "conflictId")
    @Mapping(target = "actorUserId", source = "actorUserId")
    ResolveConflictCommand toResolveCommand(
        UUID conflictId,
        ResolveConflictRequest request,
        UUID actorUserId
    );
}
