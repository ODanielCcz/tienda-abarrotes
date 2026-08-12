package com.odcc.tienda.modules.organization.adapter.out.persistence.jdbc;

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
import com.odcc.tienda.modules.organization.application.exception.OrganizationResourceNotFoundException;
import com.odcc.tienda.modules.organization.application.model.BranchView;
import com.odcc.tienda.modules.organization.application.model.CashRegisterView;
import com.odcc.tienda.modules.organization.application.model.DeviceView;
import com.odcc.tienda.modules.organization.application.model.WarehouseView;
import com.odcc.tienda.modules.organization.application.port.out.OrganizationRepositoryPort;
import com.odcc.tienda.modules.organization.domain.model.BranchStatus;
import com.odcc.tienda.modules.organization.domain.model.CashRegisterStatus;
import com.odcc.tienda.modules.organization.domain.model.DeviceStatus;
import com.odcc.tienda.modules.organization.domain.model.DeviceType;
import com.odcc.tienda.modules.organization.domain.model.WarehouseStatus;
import com.odcc.tienda.modules.organization.domain.model.WarehouseType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcOrganizationRepositoryAdapter implements OrganizationRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public BranchView createBranch(CreateBranchCommand command) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                INSERT INTO organization.branches (branch_id, code, name, legal_name, timezone, currency_code, status)
                VALUES (:id, :code, :name, :legalName, :timezone, :currencyCode, 'ACTIVE')
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("code", command.code())
                .addValue("name", command.name())
                .addValue("legalName", command.legalName())
                .addValue("timezone", command.timezone())
                .addValue("currencyCode", command.currencyCode()));
            return findBranch(id).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new OrganizationCodeAlreadyExistsException("una sucursal", command.code());
        }
    }

    @Override
    public List<BranchView> listBranches(BranchStatus status) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("status", status == null ? null : status.name());
        return jdbc.query("""
            SELECT branch_id, code, name, legal_name, timezone, currency_code, status, created_at, updated_at
            FROM organization.branches
            WHERE (CAST(:status AS varchar) IS NULL OR status = :status)
            ORDER BY code
            """, params, this::mapBranch);
    }

    @Override
    public Optional<BranchView> findBranch(UUID branchId) {
        try {
            return Optional.of(jdbc.queryForObject("""
                SELECT branch_id, code, name, legal_name, timezone, currency_code, status, created_at, updated_at
                FROM organization.branches WHERE branch_id = :id
                """, new MapSqlParameterSource("id", branchId), this::mapBranch));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<BranchView> findBranchForUpdate(UUID branchId) {
        try {
            return Optional.of(jdbc.queryForObject("""
                SELECT branch_id, code, name, legal_name, timezone,
                       currency_code, status, created_at, updated_at
                FROM organization.branches
                WHERE branch_id = :id
                FOR UPDATE
                """, new MapSqlParameterSource("id", branchId), this::mapBranch));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public BranchView updateBranch(UpdateBranchCommand command) {
        try {
            int updated = jdbc.update("""
                UPDATE organization.branches
                SET code = :code, name = :name, legal_name = :legalName, timezone = :timezone, currency_code = :currencyCode, updated_at = clock_timestamp()
                WHERE branch_id = :id
                """, new MapSqlParameterSource()
                .addValue("id", command.branchId())
                .addValue("code", command.code())
                .addValue("name", command.name())
                .addValue("legalName", command.legalName())
                .addValue("timezone", command.timezone())
                .addValue("currencyCode", command.currencyCode()));
            if (updated != 1) throw new OrganizationResourceNotFoundException("una sucursal", command.branchId());
            return findBranch(command.branchId()).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new OrganizationCodeAlreadyExistsException("una sucursal", command.code());
        }
    }

    @Override
    public BranchView changeBranchStatus(ChangeBranchStatusCommand command) {
        int updated = jdbc.update("UPDATE organization.branches SET status = :status, updated_at = clock_timestamp() WHERE branch_id = :id", new MapSqlParameterSource("id", command.branchId()).addValue("status", command.status().name()));
        if (updated != 1) throw new OrganizationResourceNotFoundException("una sucursal", command.branchId());
        return findBranch(command.branchId()).orElseThrow();
    }

    @Override
    public boolean branchCodeExists(String code, UUID excludedBranchId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM organization.branches
            WHERE code = :code AND (CAST(:excludedId AS uuid) IS NULL OR branch_id <> :excludedId)
            """, new MapSqlParameterSource("code", code).addValue("excludedId", excludedBranchId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public WarehouseView createWarehouse(CreateWarehouseCommand command) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                INSERT INTO organization.warehouses (warehouse_id, branch_id, code, name, warehouse_type, status)
                VALUES (:id, :branchId, :code, :name, :type, 'ACTIVE')
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("branchId", command.branchId())
                .addValue("code", command.code())
                .addValue("name", command.name())
                .addValue("type", warehouseType(command.warehouseType())));
            return findWarehouse(id).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new OrganizationCodeAlreadyExistsException("un almacen en la sucursal", command.code());
        }
    }

    @Override
    public List<WarehouseView> listWarehouses(UUID branchId, WarehouseStatus status) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("branchId", branchId).addValue("status", status == null ? null : status.name());
        return jdbc.query("""
            SELECT warehouse_id, branch_id, code, name, warehouse_type, status, created_at, updated_at
            FROM organization.warehouses
            WHERE (CAST(:branchId AS uuid) IS NULL OR branch_id = :branchId)
              AND (CAST(:status AS varchar) IS NULL OR status = :status)
            ORDER BY code
            """, params, this::mapWarehouse);
    }

    @Override
    public Optional<WarehouseView> findWarehouse(UUID warehouseId) {
        try {
            return Optional.of(jdbc.queryForObject("""
                SELECT warehouse_id, branch_id, code, name, warehouse_type, status, created_at, updated_at
                FROM organization.warehouses WHERE warehouse_id = :id
                """, new MapSqlParameterSource("id", warehouseId), this::mapWarehouse));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public WarehouseView updateWarehouse(UpdateWarehouseCommand command) {
        try {
            int updated = jdbc.update("""
                UPDATE organization.warehouses
                SET branch_id = :branchId, code = :code, name = :name, warehouse_type = :type, updated_at = clock_timestamp()
                WHERE warehouse_id = :id
                """, new MapSqlParameterSource()
                .addValue("id", command.warehouseId())
                .addValue("branchId", command.branchId())
                .addValue("code", command.code())
                .addValue("name", command.name())
                .addValue("type", warehouseType(command.warehouseType())));
            if (updated != 1) throw new OrganizationResourceNotFoundException("un almacen", command.warehouseId());
            return findWarehouse(command.warehouseId()).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new OrganizationCodeAlreadyExistsException("un almacen en la sucursal", command.code());
        }
    }

    @Override
    public WarehouseView changeWarehouseStatus(ChangeWarehouseStatusCommand command) {
        int updated = jdbc.update("UPDATE organization.warehouses SET status = :status, updated_at = clock_timestamp() WHERE warehouse_id = :id", new MapSqlParameterSource("id", command.warehouseId()).addValue("status", command.status().name()));
        if (updated != 1) throw new OrganizationResourceNotFoundException("un almacen", command.warehouseId());
        return findWarehouse(command.warehouseId()).orElseThrow();
    }

    @Override
    public boolean warehouseCodeExists(UUID branchId, String code, UUID excludedWarehouseId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM organization.warehouses
            WHERE branch_id = :branchId AND code = :code AND (CAST(:excludedId AS uuid) IS NULL OR warehouse_id <> :excludedId)
            """, new MapSqlParameterSource("branchId", branchId).addValue("code", code).addValue("excludedId", excludedWarehouseId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public CashRegisterView createCashRegister(CreateCashRegisterCommand command) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                INSERT INTO organization.cash_registers (cash_register_id, branch_id, device_id, code, name, status)
                VALUES (:id, :branchId, :deviceId, :code, :name, 'ACTIVE')
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("branchId", command.branchId())
                .addValue("deviceId", command.deviceId())
                .addValue("code", command.code())
                .addValue("name", command.name()));
            return findCashRegister(id).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new OrganizationCodeAlreadyExistsException("una caja registradora en la sucursal", command.code());
        }
    }

    @Override
    public List<CashRegisterView> listCashRegisters(UUID branchId, CashRegisterStatus status) {
        return jdbc.query("""
            SELECT cash_register_id, branch_id, device_id, code, name, status, created_at
            FROM organization.cash_registers
            WHERE (CAST(:branchId AS uuid) IS NULL OR branch_id = :branchId)
              AND (CAST(:status AS varchar) IS NULL OR status = :status)
            ORDER BY code
            """, new MapSqlParameterSource("branchId", branchId).addValue("status", status == null ? null : status.name()), this::mapCashRegister);
    }

    @Override
    public Optional<CashRegisterView> findCashRegister(UUID cashRegisterId) {
        try {
            return Optional.of(jdbc.queryForObject("""
                SELECT cash_register_id, branch_id, device_id, code, name, status, created_at
                FROM organization.cash_registers WHERE cash_register_id = :id
                """, new MapSqlParameterSource("id", cashRegisterId), this::mapCashRegister));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public CashRegisterView updateCashRegister(UpdateCashRegisterCommand command) {
        try {
            int updated = jdbc.update("""
                UPDATE organization.cash_registers
                SET branch_id = :branchId, device_id = :deviceId, code = :code, name = :name
                WHERE cash_register_id = :id
                """, new MapSqlParameterSource()
                .addValue("id", command.cashRegisterId())
                .addValue("branchId", command.branchId())
                .addValue("deviceId", command.deviceId())
                .addValue("code", command.code())
                .addValue("name", command.name()));
            if (updated != 1) throw new OrganizationResourceNotFoundException("una caja registradora", command.cashRegisterId());
            return findCashRegister(command.cashRegisterId()).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new OrganizationCodeAlreadyExistsException("una caja registradora en la sucursal", command.code());
        }
    }

    @Override
    public CashRegisterView changeCashRegisterStatus(ChangeCashRegisterStatusCommand command) {
        int updated = jdbc.update("UPDATE organization.cash_registers SET status = :status WHERE cash_register_id = :id", new MapSqlParameterSource("id", command.cashRegisterId()).addValue("status", command.status().name()));
        if (updated != 1) throw new OrganizationResourceNotFoundException("una caja registradora", command.cashRegisterId());
        return findCashRegister(command.cashRegisterId()).orElseThrow();
    }

    @Override
    public boolean cashRegisterCodeExists(UUID branchId, String code, UUID excludedCashRegisterId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM organization.cash_registers
            WHERE branch_id = :branchId AND code = :code AND (CAST(:excludedId AS uuid) IS NULL OR cash_register_id <> :excludedId)
            """, new MapSqlParameterSource("branchId", branchId).addValue("code", code).addValue("excludedId", excludedCashRegisterId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public DeviceView createDevice(CreateDeviceCommand command) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                INSERT INTO organization.devices (device_id, branch_id, warehouse_id, device_code, device_type, platform, app_version, status)
                VALUES (:id, :branchId, :warehouseId, :deviceCode, :deviceType, :platform, :appVersion, 'ACTIVE')
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("branchId", command.branchId())
                .addValue("warehouseId", command.warehouseId())
                .addValue("deviceCode", command.deviceCode())
                .addValue("deviceType", command.deviceType().name())
                .addValue("platform", command.platform())
                .addValue("appVersion", command.appVersion()));
            return findDevice(id).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new OrganizationCodeAlreadyExistsException("un dispositivo", command.deviceCode());
        }
    }

    @Override
    public List<DeviceView> listDevices(UUID branchId, DeviceStatus status) {
        return jdbc.query("""
            SELECT device_id, branch_id, warehouse_id, device_code, device_type, platform, app_version, status, last_seen_at, created_at, updated_at
            FROM organization.devices
            WHERE (CAST(:branchId AS uuid) IS NULL OR branch_id = :branchId)
              AND (CAST(:status AS varchar) IS NULL OR status = :status)
            ORDER BY device_code
            """, new MapSqlParameterSource("branchId", branchId).addValue("status", status == null ? null : status.name()), this::mapDevice);
    }

    @Override
    public Optional<DeviceView> findDevice(UUID deviceId) {
        try {
            return Optional.of(jdbc.queryForObject("""
                SELECT device_id, branch_id, warehouse_id, device_code, device_type, platform, app_version, status, last_seen_at, created_at, updated_at
                FROM organization.devices WHERE device_id = :id
                """, new MapSqlParameterSource("id", deviceId), this::mapDevice));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public DeviceView updateDevice(UpdateDeviceCommand command) {
        try {
            int updated = jdbc.update("""
                UPDATE organization.devices
                SET branch_id = :branchId, warehouse_id = :warehouseId, device_code = :deviceCode, device_type = :deviceType, platform = :platform, app_version = :appVersion, updated_at = clock_timestamp()
                WHERE device_id = :id
                """, new MapSqlParameterSource()
                .addValue("id", command.deviceId())
                .addValue("branchId", command.branchId())
                .addValue("warehouseId", command.warehouseId())
                .addValue("deviceCode", command.deviceCode())
                .addValue("deviceType", command.deviceType().name())
                .addValue("platform", command.platform())
                .addValue("appVersion", command.appVersion()));
            if (updated != 1) throw new OrganizationResourceNotFoundException("un dispositivo", command.deviceId());
            return findDevice(command.deviceId()).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new OrganizationCodeAlreadyExistsException("un dispositivo", command.deviceCode());
        }
    }

    @Override
    public DeviceView changeDeviceStatus(ChangeDeviceStatusCommand command) {
        int updated = jdbc.update("UPDATE organization.devices SET status = :status, updated_at = clock_timestamp() WHERE device_id = :id", new MapSqlParameterSource("id", command.deviceId()).addValue("status", command.status().name()));
        if (updated != 1) throw new OrganizationResourceNotFoundException("un dispositivo", command.deviceId());
        return findDevice(command.deviceId()).orElseThrow();
    }

    @Override
    public boolean deviceCodeExists(String deviceCode, UUID excludedDeviceId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM organization.devices
            WHERE device_code = :deviceCode AND (CAST(:excludedId AS uuid) IS NULL OR device_id <> :excludedId)
            """, new MapSqlParameterSource("deviceCode", deviceCode).addValue("excludedId", excludedDeviceId), Integer.class);
        return count != null && count > 0;
    }

    private BranchView mapBranch(ResultSet rs, int rowNum) throws SQLException {
        return new BranchView(getUuid(rs, "branch_id"), rs.getString("code"), rs.getString("name"), rs.getString("legal_name"), rs.getString("timezone"), rs.getString("currency_code").trim(), BranchStatus.valueOf(rs.getString("status")), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private WarehouseView mapWarehouse(ResultSet rs, int rowNum) throws SQLException {
        return new WarehouseView(getUuid(rs, "warehouse_id"), getUuid(rs, "branch_id"), rs.getString("code"), rs.getString("name"), WarehouseType.valueOf(rs.getString("warehouse_type")), WarehouseStatus.valueOf(rs.getString("status")), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private CashRegisterView mapCashRegister(ResultSet rs, int rowNum) throws SQLException {
        return new CashRegisterView(getUuid(rs, "cash_register_id"), getUuid(rs, "branch_id"), getUuid(rs, "device_id"), rs.getString("code"), rs.getString("name"), CashRegisterStatus.valueOf(rs.getString("status")), instant(rs, "created_at"));
    }

    private DeviceView mapDevice(ResultSet rs, int rowNum) throws SQLException {
        return new DeviceView(getUuid(rs, "device_id"), getUuid(rs, "branch_id"), getUuid(rs, "warehouse_id"), rs.getString("device_code"), DeviceType.valueOf(rs.getString("device_type")), rs.getString("platform"), rs.getString("app_version"), DeviceStatus.valueOf(rs.getString("status")), instant(rs, "last_seen_at"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private String warehouseType(WarehouseType type) {
        return type == null ? WarehouseType.STORE.name() : type.name();
    }

    private UUID getUuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
