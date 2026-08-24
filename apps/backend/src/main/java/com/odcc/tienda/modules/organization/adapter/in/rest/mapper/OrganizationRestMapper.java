package com.odcc.tienda.modules.organization.adapter.in.rest.mapper;

import com.odcc.tienda.modules.organization.adapter.in.rest.request.BranchRequests.ChangeBranchStatusRequest;
import com.odcc.tienda.modules.organization.adapter.in.rest.request.BranchRequests.CreateBranchRequest;
import com.odcc.tienda.modules.organization.adapter.in.rest.request.BranchRequests.UpdateBranchRequest;
import com.odcc.tienda.modules.organization.adapter.in.rest.request.CashRegisterRequests.ChangeCashRegisterStatusRequest;
import com.odcc.tienda.modules.organization.adapter.in.rest.request.CashRegisterRequests.CreateCashRegisterRequest;
import com.odcc.tienda.modules.organization.adapter.in.rest.request.CashRegisterRequests.UpdateCashRegisterRequest;
import com.odcc.tienda.modules.organization.adapter.in.rest.request.DeviceRequests.ChangeDeviceStatusRequest;
import com.odcc.tienda.modules.organization.adapter.in.rest.request.DeviceRequests.CreateDeviceRequest;
import com.odcc.tienda.modules.organization.adapter.in.rest.request.DeviceRequests.UpdateDeviceRequest;
import com.odcc.tienda.modules.organization.adapter.in.rest.request.WarehouseRequests.ChangeWarehouseStatusRequest;
import com.odcc.tienda.modules.organization.adapter.in.rest.request.WarehouseRequests.CreateWarehouseRequest;
import com.odcc.tienda.modules.organization.adapter.in.rest.request.WarehouseRequests.UpdateWarehouseRequest;
import com.odcc.tienda.modules.organization.application.command.BranchCommands.ChangeBranchStatusCommand;
import com.odcc.tienda.modules.organization.application.command.BranchCommands.CreateBranchCommand;
import com.odcc.tienda.modules.organization.application.command.BranchCommands.UpdateBranchCommand;
import com.odcc.tienda.modules.organization.application.command.CashRegisterCommands.ChangeCashRegisterStatusCommand;
import com.odcc.tienda.modules.organization.application.command.CashRegisterCommands.CreateCashRegisterCommand;
import com.odcc.tienda.modules.organization.application.command.CashRegisterCommands.UpdateCashRegisterCommand;
import com.odcc.tienda.modules.organization.application.command.DeviceCommands.ChangeDeviceStatusCommand;
import com.odcc.tienda.modules.organization.application.command.DeviceCommands.CreateDeviceCommand;
import com.odcc.tienda.modules.organization.application.command.DeviceCommands.UpdateDeviceCommand;
import com.odcc.tienda.modules.organization.application.command.WarehouseCommands.ChangeWarehouseStatusCommand;
import com.odcc.tienda.modules.organization.application.command.WarehouseCommands.CreateWarehouseCommand;
import com.odcc.tienda.modules.organization.application.command.WarehouseCommands.UpdateWarehouseCommand;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(config = CentralMapperConfig.class)
public interface OrganizationRestMapper {

    CreateBranchCommand toCreateBranchCommand(CreateBranchRequest request);

    @Mapping(target = "branchId", source = "branchId")
    UpdateBranchCommand toUpdateBranchCommand(UUID branchId, UpdateBranchRequest request);

    @Mapping(target = "branchId", source = "branchId")
    ChangeBranchStatusCommand toBranchStatusCommand(UUID branchId, ChangeBranchStatusRequest request);

    CreateWarehouseCommand toCreateWarehouseCommand(CreateWarehouseRequest request);

    @Mapping(target = "warehouseId", source = "warehouseId")
    UpdateWarehouseCommand toUpdateWarehouseCommand(UUID warehouseId, UpdateWarehouseRequest request);

    @Mapping(target = "warehouseId", source = "warehouseId")
    ChangeWarehouseStatusCommand toWarehouseStatusCommand(UUID warehouseId, ChangeWarehouseStatusRequest request);

    CreateCashRegisterCommand toCreateCashRegisterCommand(CreateCashRegisterRequest request);

    @Mapping(target = "cashRegisterId", source = "cashRegisterId")
    UpdateCashRegisterCommand toUpdateCashRegisterCommand(UUID cashRegisterId, UpdateCashRegisterRequest request);

    @Mapping(target = "cashRegisterId", source = "cashRegisterId")
    ChangeCashRegisterStatusCommand toCashRegisterStatusCommand(UUID cashRegisterId, ChangeCashRegisterStatusRequest request);

    CreateDeviceCommand toCreateDeviceCommand(CreateDeviceRequest request);

    @Mapping(target = "deviceId", source = "deviceId")
    UpdateDeviceCommand toUpdateDeviceCommand(UUID deviceId, UpdateDeviceRequest request);

    @Mapping(target = "deviceId", source = "deviceId")
    ChangeDeviceStatusCommand toDeviceStatusCommand(UUID deviceId, ChangeDeviceStatusRequest request);
}
