package com.odcc.tienda.modules.organization.application.port.in;

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
import com.odcc.tienda.modules.organization.application.model.BranchView;
import com.odcc.tienda.modules.organization.application.model.CashRegisterView;
import com.odcc.tienda.modules.organization.application.model.DeviceView;
import com.odcc.tienda.modules.organization.application.model.WarehouseView;
import com.odcc.tienda.modules.organization.domain.model.BranchStatus;
import com.odcc.tienda.modules.organization.domain.model.CashRegisterStatus;
import com.odcc.tienda.modules.organization.domain.model.DeviceStatus;
import com.odcc.tienda.modules.organization.domain.model.WarehouseStatus;

import java.util.List;
import java.util.UUID;

public interface OrganizationUseCases {
    BranchView createBranch(CreateBranchCommand command, UUID actorUserId);
    List<BranchView> listBranches(BranchStatus status, UUID actorUserId);
    BranchView getBranch(UUID branchId, UUID actorUserId);
    BranchView updateBranch(UpdateBranchCommand command, UUID actorUserId);
    BranchView changeBranchStatus(ChangeBranchStatusCommand command, UUID actorUserId);

    WarehouseView createWarehouse(CreateWarehouseCommand command, UUID actorUserId);
    List<WarehouseView> listWarehouses(UUID branchId, WarehouseStatus status, UUID actorUserId);
    WarehouseView getWarehouse(UUID warehouseId, UUID actorUserId);
    WarehouseView updateWarehouse(UpdateWarehouseCommand command, UUID actorUserId);
    WarehouseView changeWarehouseStatus(ChangeWarehouseStatusCommand command, UUID actorUserId);

    CashRegisterView createCashRegister(CreateCashRegisterCommand command, UUID actorUserId);
    List<CashRegisterView> listCashRegisters(UUID branchId, CashRegisterStatus status, UUID actorUserId);
    CashRegisterView getCashRegister(UUID cashRegisterId, UUID actorUserId);
    CashRegisterView updateCashRegister(UpdateCashRegisterCommand command, UUID actorUserId);
    CashRegisterView changeCashRegisterStatus(ChangeCashRegisterStatusCommand command, UUID actorUserId);

    DeviceView createDevice(CreateDeviceCommand command, UUID actorUserId);
    List<DeviceView> listDevices(UUID branchId, DeviceStatus status, UUID actorUserId);
    DeviceView getDevice(UUID deviceId, UUID actorUserId);
    DeviceView updateDevice(UpdateDeviceCommand command, UUID actorUserId);
    DeviceView changeDeviceStatus(ChangeDeviceStatusCommand command, UUID actorUserId);
}
