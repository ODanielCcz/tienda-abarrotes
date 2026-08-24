package com.odcc.tienda.modules.cash.adapter.in.rest.mapper;

import com.odcc.tienda.modules.cash.adapter.in.rest.request.CloseCashSessionRequest;
import com.odcc.tienda.modules.cash.adapter.in.rest.request.CreateCashMovementRequest;
import com.odcc.tienda.modules.cash.adapter.in.rest.request.OpenCashSessionRequest;
import com.odcc.tienda.modules.cash.application.command.CloseCashSessionCommand;
import com.odcc.tienda.modules.cash.application.command.CreateCashMovementCommand;
import com.odcc.tienda.modules.cash.application.command.OpenCashSessionCommand;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(config = CentralMapperConfig.class)
public interface CashSessionRestMapper {

    @Mapping(target = "openedBy", source = "actorUserId")
    OpenCashSessionCommand toOpenCommand(
        OpenCashSessionRequest request,
        UUID actorUserId
    );

    @Mapping(target = "cashSessionId", source = "cashSessionId")
    @Mapping(target = "closedBy", source = "actorUserId")
    CloseCashSessionCommand toCloseCommand(
        UUID cashSessionId,
        CloseCashSessionRequest request,
        UUID actorUserId
    );

    @Mapping(target = "cashSessionId", source = "cashSessionId")
    @Mapping(target = "createdBy", source = "actorUserId")
    CreateCashMovementCommand toMovementCommand(
        UUID cashSessionId,
        CreateCashMovementRequest request,
        UUID actorUserId
    );
}
