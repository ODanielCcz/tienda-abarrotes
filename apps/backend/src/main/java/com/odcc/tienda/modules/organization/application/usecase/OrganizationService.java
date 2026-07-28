package com.odcc.tienda.modules.organization.application.usecase;

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
import com.odcc.tienda.modules.organization.application.exception.OrganizationCodeAlreadyExistsException;
import com.odcc.tienda.modules.organization.application.exception.OrganizationException;
import com.odcc.tienda.modules.organization.application.exception.OrganizationResourceNotFoundException;
import com.odcc.tienda.modules.organization.application.model.BranchView;
import com.odcc.tienda.modules.organization.application.model.CashRegisterView;
import com.odcc.tienda.modules.organization.application.model.DeviceView;
import com.odcc.tienda.modules.organization.application.model.WarehouseView;
import com.odcc.tienda.modules.organization.application.port.in.OrganizationUseCases;
import com.odcc.tienda.modules.organization.application.port.out.OrganizationRepositoryPort;
import com.odcc.tienda.modules.organization.domain.model.BranchStatus;
import com.odcc.tienda.modules.organization.domain.model.CashRegisterStatus;
import com.odcc.tienda.modules.organization.domain.model.DeviceStatus;
import com.odcc.tienda.modules.organization.domain.model.WarehouseStatus;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class OrganizationService implements OrganizationUseCases {

    private final OrganizationRepositoryPort repository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public BranchView createBranch(CreateBranchCommand command) {
        validateBranch(command.code(), command.name(), command.currencyCode());
        String code = normalize(command.code());
        ensureBranchCodeAvailable(code, null);
        return transactionRunner.required(() -> {
            BranchView branch = repository.createBranch(new CreateBranchCommand(code, command.name().trim(), trimToNull(command.legalName()), defaultTimezone(command.timezone()), normalizeCurrency(command.currencyCode())));
            audit("BRANCH_CREATED", "BRANCH", branch.branchId(), Map.of(), Map.of("code", branch.code(), "status", branch.status().name()));
            return branch;
        });
    }

    @Override
    public List<BranchView> listBranches(BranchStatus status) {
        return repository.listBranches(status);
    }

    @Override
    public BranchView getBranch(UUID branchId) {
        return findBranch(branchId);
    }

    @Override
    public BranchView updateBranch(UpdateBranchCommand command) {
        if (command == null || command.branchId() == null) throw new OrganizationException("La sucursal es obligatoria");
        validateBranch(command.code(), command.name(), command.currencyCode());
        BranchView current = findBranch(command.branchId());
        String code = normalize(command.code());
        ensureBranchCodeAvailable(code, command.branchId());
        return transactionRunner.required(() -> {
            BranchView branch = repository.updateBranch(new UpdateBranchCommand(command.branchId(), code, command.name().trim(), trimToNull(command.legalName()), defaultTimezone(command.timezone()), normalizeCurrency(command.currencyCode())));
            audit("BRANCH_UPDATED", "BRANCH", branch.branchId(), Map.of("code", current.code(), "name", current.name()), Map.of("code", branch.code(), "name", branch.name()));
            return branch;
        });
    }

    @Override
    public BranchView changeBranchStatus(ChangeBranchStatusCommand command) {
        if (command == null || command.branchId() == null) throw new OrganizationException("La sucursal es obligatoria");
        if (command.status() == null) throw new OrganizationException("El estado de la sucursal es obligatorio");
        BranchView current = findBranch(command.branchId());
        return transactionRunner.required(() -> {
            BranchView branch = repository.changeBranchStatus(command);
            audit("BRANCH_STATUS_CHANGED", "BRANCH", branch.branchId(), Map.of("status", current.status().name()), Map.of("status", branch.status().name()));
            return branch;
        });
    }

    @Override
    public WarehouseView createWarehouse(CreateWarehouseCommand command) {
        validateWarehouse(command.branchId(), command.code(), command.name());
        BranchView branch = findActiveBranch(command.branchId());
        String code = normalize(command.code());
        ensureWarehouseCodeAvailable(branch.branchId(), code, null);
        return transactionRunner.required(() -> {
            WarehouseView warehouse = repository.createWarehouse(new CreateWarehouseCommand(branch.branchId(), code, command.name().trim(), command.warehouseType()));
            audit("WAREHOUSE_CREATED", "WAREHOUSE", warehouse.warehouseId(), Map.of(), Map.of("code", warehouse.code(), "branchId", warehouse.branchId()));
            return warehouse;
        });
    }

    @Override
    public List<WarehouseView> listWarehouses(UUID branchId, WarehouseStatus status) {
        return repository.listWarehouses(branchId, status);
    }

    @Override
    public WarehouseView getWarehouse(UUID warehouseId) {
        return findWarehouse(warehouseId);
    }

    @Override
    public WarehouseView updateWarehouse(UpdateWarehouseCommand command) {
        if (command == null || command.warehouseId() == null) throw new OrganizationException("El almacen es obligatorio");
        validateWarehouse(command.branchId(), command.code(), command.name());
        WarehouseView current = findWarehouse(command.warehouseId());
        BranchView branch = findActiveBranch(command.branchId());
        String code = normalize(command.code());
        ensureWarehouseCodeAvailable(branch.branchId(), code, command.warehouseId());
        return transactionRunner.required(() -> {
            WarehouseView warehouse = repository.updateWarehouse(new UpdateWarehouseCommand(command.warehouseId(), branch.branchId(), code, command.name().trim(), command.warehouseType()));
            audit("WAREHOUSE_UPDATED", "WAREHOUSE", warehouse.warehouseId(), Map.of("code", current.code()), Map.of("code", warehouse.code(), "branchId", warehouse.branchId()));
            return warehouse;
        });
    }

    @Override
    public WarehouseView changeWarehouseStatus(ChangeWarehouseStatusCommand command) {
        if (command == null || command.warehouseId() == null) throw new OrganizationException("El almacen es obligatorio");
        if (command.status() == null) throw new OrganizationException("El estado del almacen es obligatorio");
        WarehouseView current = findWarehouse(command.warehouseId());
        return transactionRunner.required(() -> {
            WarehouseView warehouse = repository.changeWarehouseStatus(command);
            audit("WAREHOUSE_STATUS_CHANGED", "WAREHOUSE", warehouse.warehouseId(), Map.of("status", current.status().name()), Map.of("status", warehouse.status().name()));
            return warehouse;
        });
    }

    @Override
    public CashRegisterView createCashRegister(CreateCashRegisterCommand command) {
        validateCashRegister(command.branchId(), command.code(), command.name());
        BranchView branch = findActiveBranch(command.branchId());
        validateActiveDeviceForBranch(command.deviceId(), branch.branchId());
        String code = normalize(command.code());
        ensureCashRegisterCodeAvailable(branch.branchId(), code, null);
        return transactionRunner.required(() -> {
            CashRegisterView register = repository.createCashRegister(new CreateCashRegisterCommand(branch.branchId(), command.deviceId(), code, command.name().trim()));
            audit("CASH_REGISTER_CREATED", "CASH_REGISTER", register.cashRegisterId(), Map.of(), Map.of("code", register.code(), "branchId", register.branchId()));
            return register;
        });
    }

    @Override
    public List<CashRegisterView> listCashRegisters(UUID branchId, CashRegisterStatus status) {
        return repository.listCashRegisters(branchId, status);
    }

    @Override
    public CashRegisterView getCashRegister(UUID cashRegisterId) {
        return findCashRegister(cashRegisterId);
    }

    @Override
    public CashRegisterView updateCashRegister(UpdateCashRegisterCommand command) {
        if (command == null || command.cashRegisterId() == null) throw new OrganizationException("La caja registradora es obligatoria");
        validateCashRegister(command.branchId(), command.code(), command.name());
        CashRegisterView current = findCashRegister(command.cashRegisterId());
        BranchView branch = findActiveBranch(command.branchId());
        validateActiveDeviceForBranch(command.deviceId(), branch.branchId());
        String code = normalize(command.code());
        ensureCashRegisterCodeAvailable(branch.branchId(), code, command.cashRegisterId());
        return transactionRunner.required(() -> {
            CashRegisterView register = repository.updateCashRegister(new UpdateCashRegisterCommand(command.cashRegisterId(), branch.branchId(), command.deviceId(), code, command.name().trim()));
            audit("CASH_REGISTER_UPDATED", "CASH_REGISTER", register.cashRegisterId(), Map.of("code", current.code()), Map.of("code", register.code(), "branchId", register.branchId()));
            return register;
        });
    }

    @Override
    public CashRegisterView changeCashRegisterStatus(ChangeCashRegisterStatusCommand command) {
        if (command == null || command.cashRegisterId() == null) throw new OrganizationException("La caja registradora es obligatoria");
        if (command.status() == null) throw new OrganizationException("El estado de la caja registradora es obligatorio");
        CashRegisterView current = findCashRegister(command.cashRegisterId());
        return transactionRunner.required(() -> {
            CashRegisterView register = repository.changeCashRegisterStatus(command);
            audit("CASH_REGISTER_STATUS_CHANGED", "CASH_REGISTER", register.cashRegisterId(), Map.of("status", current.status().name()), Map.of("status", register.status().name()));
            return register;
        });
    }

    @Override
    public DeviceView createDevice(CreateDeviceCommand command) {
        validateDevice(command.branchId(), command.deviceCode(), command.deviceType());
        BranchView branch = findActiveBranch(command.branchId());
        validateActiveWarehouseForBranch(command.warehouseId(), branch.branchId());
        String code = normalize(command.deviceCode());
        ensureDeviceCodeAvailable(code, null);
        return transactionRunner.required(() -> {
            DeviceView device = repository.createDevice(new CreateDeviceCommand(branch.branchId(), command.warehouseId(), code, command.deviceType(), trimToNull(command.platform()), trimToNull(command.appVersion())));
            audit("DEVICE_CREATED", "DEVICE", device.deviceId(), Map.of(), Map.of("deviceCode", device.deviceCode(), "branchId", device.branchId()));
            return device;
        });
    }

    @Override
    public List<DeviceView> listDevices(UUID branchId, DeviceStatus status) {
        return repository.listDevices(branchId, status);
    }

    @Override
    public DeviceView getDevice(UUID deviceId) {
        return findDevice(deviceId);
    }

    @Override
    public DeviceView updateDevice(UpdateDeviceCommand command) {
        if (command == null || command.deviceId() == null) throw new OrganizationException("El dispositivo es obligatorio");
        validateDevice(command.branchId(), command.deviceCode(), command.deviceType());
        DeviceView current = findDevice(command.deviceId());
        BranchView branch = findActiveBranch(command.branchId());
        validateActiveWarehouseForBranch(command.warehouseId(), branch.branchId());
        String code = normalize(command.deviceCode());
        ensureDeviceCodeAvailable(code, command.deviceId());
        return transactionRunner.required(() -> {
            DeviceView device = repository.updateDevice(new UpdateDeviceCommand(command.deviceId(), branch.branchId(), command.warehouseId(), code, command.deviceType(), trimToNull(command.platform()), trimToNull(command.appVersion())));
            audit("DEVICE_UPDATED", "DEVICE", device.deviceId(), Map.of("deviceCode", current.deviceCode()), Map.of("deviceCode", device.deviceCode(), "branchId", device.branchId()));
            return device;
        });
    }

    @Override
    public DeviceView changeDeviceStatus(ChangeDeviceStatusCommand command) {
        if (command == null || command.deviceId() == null) throw new OrganizationException("El dispositivo es obligatorio");
        if (command.status() == null) throw new OrganizationException("El estado del dispositivo es obligatorio");
        DeviceView current = findDevice(command.deviceId());
        return transactionRunner.required(() -> {
            DeviceView device = repository.changeDeviceStatus(command);
            audit("DEVICE_STATUS_CHANGED", "DEVICE", device.deviceId(), Map.of("status", current.status().name()), Map.of("status", device.status().name()));
            return device;
        });
    }

    private BranchView findBranch(UUID branchId) {
        if (branchId == null) throw new OrganizationException("La sucursal es obligatoria");
        return repository.findBranch(branchId).orElseThrow(() -> new OrganizationResourceNotFoundException("una sucursal", branchId));
    }

    private BranchView findActiveBranch(UUID branchId) {
        BranchView branch = findBranch(branchId);
        if (branch.status() != BranchStatus.ACTIVE) throw new OrganizationException("La sucursal debe estar activa");
        return branch;
    }

    private WarehouseView findWarehouse(UUID warehouseId) {
        if (warehouseId == null) throw new OrganizationException("El almacen es obligatorio");
        return repository.findWarehouse(warehouseId).orElseThrow(() -> new OrganizationResourceNotFoundException("un almacen", warehouseId));
    }

    private CashRegisterView findCashRegister(UUID cashRegisterId) {
        if (cashRegisterId == null) throw new OrganizationException("La caja registradora es obligatoria");
        return repository.findCashRegister(cashRegisterId).orElseThrow(() -> new OrganizationResourceNotFoundException("una caja registradora", cashRegisterId));
    }

    private DeviceView findDevice(UUID deviceId) {
        if (deviceId == null) throw new OrganizationException("El dispositivo es obligatorio");
        return repository.findDevice(deviceId).orElseThrow(() -> new OrganizationResourceNotFoundException("un dispositivo", deviceId));
    }

    private void validateActiveWarehouseForBranch(UUID warehouseId, UUID branchId) {
        if (warehouseId == null) return;
        WarehouseView warehouse = findWarehouse(warehouseId);
        if (!warehouse.branchId().equals(branchId)) throw new OrganizationException("El almacen no pertenece a la sucursal");
        if (warehouse.status() != WarehouseStatus.ACTIVE) throw new OrganizationException("El almacen debe estar activo");
    }

    private void validateActiveDeviceForBranch(UUID deviceId, UUID branchId) {
        if (deviceId == null) return;
        DeviceView device = findDevice(deviceId);
        if (!device.branchId().equals(branchId)) throw new OrganizationException("El dispositivo no pertenece a la sucursal");
        if (device.status() != DeviceStatus.ACTIVE) throw new OrganizationException("El dispositivo debe estar activo");
    }

    private void ensureBranchCodeAvailable(String code, UUID excludedId) {
        if (repository.branchCodeExists(code, excludedId)) throw new OrganizationCodeAlreadyExistsException("una sucursal", code);
    }

    private void ensureWarehouseCodeAvailable(UUID branchId, String code, UUID excludedId) {
        if (repository.warehouseCodeExists(branchId, code, excludedId)) throw new OrganizationCodeAlreadyExistsException("un almacen en la sucursal", code);
    }

    private void ensureCashRegisterCodeAvailable(UUID branchId, String code, UUID excludedId) {
        if (repository.cashRegisterCodeExists(branchId, code, excludedId)) throw new OrganizationCodeAlreadyExistsException("una caja registradora en la sucursal", code);
    }

    private void ensureDeviceCodeAvailable(String code, UUID excludedId) {
        if (repository.deviceCodeExists(code, excludedId)) throw new OrganizationCodeAlreadyExistsException("un dispositivo", code);
    }

    private void validateBranch(String code, String name, String currencyCode) {
        validateCode(code, "El codigo de sucursal es obligatorio");
        validateName(name, "El nombre de sucursal es obligatorio");
        String currency = normalizeCurrency(currencyCode);
        if (!currency.matches("^[A-Z]{3}$")) throw new OrganizationException("La moneda debe tener tres letras");
    }

    private void validateWarehouse(UUID branchId, String code, String name) {
        if (branchId == null) throw new OrganizationException("La sucursal es obligatoria");
        validateCode(code, "El codigo de almacen es obligatorio");
        validateName(name, "El nombre de almacen es obligatorio");
    }

    private void validateCashRegister(UUID branchId, String code, String name) {
        if (branchId == null) throw new OrganizationException("La sucursal es obligatoria");
        validateCode(code, "El codigo de caja registradora es obligatorio");
        validateName(name, "El nombre de caja registradora es obligatorio");
    }

    private void validateDevice(UUID branchId, String deviceCode, Object deviceType) {
        if (branchId == null) throw new OrganizationException("La sucursal es obligatoria");
        validateCode(deviceCode, "El codigo de dispositivo es obligatorio");
        if (deviceType == null) throw new OrganizationException("El tipo de dispositivo es obligatorio");
    }

    private void validateCode(String value, String message) {
        if (value == null || value.isBlank()) throw new OrganizationException(message);
        if (normalize(value).length() > 80) throw new OrganizationException("El codigo es demasiado largo");
    }

    private void validateName(String value, String message) {
        if (value == null || value.isBlank()) throw new OrganizationException(message);
        if (value.trim().length() > 150) throw new OrganizationException("El nombre es demasiado largo");
    }

    private String defaultTimezone(String value) {
        return value == null || value.isBlank() ? "America/Mexico_City" : value.trim();
    }

    private String normalizeCurrency(String value) {
        return value == null || value.isBlank() ? "MXN" : value.trim().toUpperCase();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void audit(String eventType, String aggregateType, UUID aggregateId, Map<String, Object> before, Map<String, Object> after) {
        auditPort.record(new BusinessAuditEvent(eventType, aggregateType, aggregateId, before, after, Map.of()));
    }
}