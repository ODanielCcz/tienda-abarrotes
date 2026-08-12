package com.odcc.tienda.modules.organization.application.port.out;

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
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepositoryPort {
    BranchView createBranch(CreateBranchCommand command);
    List<BranchView> listBranches(BranchStatus status);
    Optional<BranchView> findBranch(UUID branchId);
    Optional<BranchView> findBranchForUpdate(UUID branchId);
    BranchView updateBranch(UpdateBranchCommand command);
    BranchView changeBranchStatus(ChangeBranchStatusCommand command);
    boolean branchCodeExists(String code, UUID excludedBranchId);

    WarehouseView createWarehouse(CreateWarehouseCommand command);
    List<WarehouseView> listWarehouses(UUID branchId, WarehouseStatus status);
    Optional<WarehouseView> findWarehouse(UUID warehouseId);
    WarehouseView updateWarehouse(UpdateWarehouseCommand command);
    WarehouseView changeWarehouseStatus(ChangeWarehouseStatusCommand command);
    boolean warehouseCodeExists(UUID branchId, String code, UUID excludedWarehouseId);

    CashRegisterView createCashRegister(CreateCashRegisterCommand command);
    List<CashRegisterView> listCashRegisters(UUID branchId, CashRegisterStatus status);
    Optional<CashRegisterView> findCashRegister(UUID cashRegisterId);
    CashRegisterView updateCashRegister(UpdateCashRegisterCommand command);
    CashRegisterView changeCashRegisterStatus(ChangeCashRegisterStatusCommand command);
    boolean cashRegisterCodeExists(UUID branchId, String code, UUID excludedCashRegisterId);

    DeviceView createDevice(CreateDeviceCommand command);
    List<DeviceView> listDevices(UUID branchId, DeviceStatus status);
    Optional<DeviceView> findDevice(UUID deviceId);
    DeviceView updateDevice(UpdateDeviceCommand command);
    DeviceView changeDeviceStatus(ChangeDeviceStatusCommand command);
    boolean deviceCodeExists(String deviceCode, UUID excludedDeviceId);
}
