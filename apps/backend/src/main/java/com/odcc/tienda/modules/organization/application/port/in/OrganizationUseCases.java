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
    BranchView createBranch(CreateBranchCommand command);
    List<BranchView> listBranches(BranchStatus status);
    BranchView getBranch(UUID branchId);
    BranchView updateBranch(UpdateBranchCommand command);
    BranchView changeBranchStatus(ChangeBranchStatusCommand command);

    WarehouseView createWarehouse(CreateWarehouseCommand command);
    List<WarehouseView> listWarehouses(UUID branchId, WarehouseStatus status);
    WarehouseView getWarehouse(UUID warehouseId);
    WarehouseView updateWarehouse(UpdateWarehouseCommand command);
    WarehouseView changeWarehouseStatus(ChangeWarehouseStatusCommand command);

    CashRegisterView createCashRegister(CreateCashRegisterCommand command);
    List<CashRegisterView> listCashRegisters(UUID branchId, CashRegisterStatus status);
    CashRegisterView getCashRegister(UUID cashRegisterId);
    CashRegisterView updateCashRegister(UpdateCashRegisterCommand command);
    CashRegisterView changeCashRegisterStatus(ChangeCashRegisterStatusCommand command);

    DeviceView createDevice(CreateDeviceCommand command);
    List<DeviceView> listDevices(UUID branchId, DeviceStatus status);
    DeviceView getDevice(UUID deviceId);
    DeviceView updateDevice(UpdateDeviceCommand command);
    DeviceView changeDeviceStatus(ChangeDeviceStatusCommand command);
}