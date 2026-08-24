package com.odcc.tienda.modules.inventory.adapter.in.rest.mapper;

import com.odcc.tienda.modules.inventory.adapter.in.rest.request.CreateInventoryAdjustmentRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.CreateInventoryCountRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.CreateInventoryTransferRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.CreateReservationRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.InventoryAdjustmentItemRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.InventoryCountItemRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.InventoryTransferItemRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.ReservationItemRequest;
import com.odcc.tienda.modules.inventory.application.command.ConfirmInventoryCountCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateInventoryAdjustmentCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateInventoryCountCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateInventoryTransferCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateReservationCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryAdjustmentItemCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryCountItemCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryTransferItemCommand;
import com.odcc.tienda.modules.inventory.application.command.ReleaseReservationCommand;
import com.odcc.tienda.modules.inventory.application.command.ReservationItemCommand;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(config = CentralMapperConfig.class)
public interface AdvancedInventoryRestMapper {

    InventoryAdjustmentItemCommand toCommand(InventoryAdjustmentItemRequest request);

    InventoryTransferItemCommand toCommand(InventoryTransferItemRequest request);

    InventoryCountItemCommand toCommand(InventoryCountItemRequest request);

    ReservationItemCommand toCommand(ReservationItemRequest request);

    @Mapping(target = "createdBy", source = "actorUserId")
    CreateInventoryAdjustmentCommand toAdjustmentCommand(
        CreateInventoryAdjustmentRequest request,
        UUID actorUserId
    );

    @Mapping(target = "createdBy", source = "actorUserId")
    CreateInventoryTransferCommand toTransferCommand(
        CreateInventoryTransferRequest request,
        UUID actorUserId
    );

    @Mapping(target = "startedBy", source = "actorUserId")
    CreateInventoryCountCommand toCountCommand(
        CreateInventoryCountRequest request,
        UUID actorUserId
    );

    @Mapping(target = "createdBy", source = "actorUserId")
    CreateReservationCommand toReservationCommand(
        CreateReservationRequest request,
        UUID actorUserId
    );

    ConfirmInventoryCountCommand toConfirmCountCommand(
        UUID inventoryCountId,
        UUID confirmedBy
    );

    ReleaseReservationCommand toReleaseReservationCommand(
        UUID reservationId,
        UUID releasedBy
    );
}
