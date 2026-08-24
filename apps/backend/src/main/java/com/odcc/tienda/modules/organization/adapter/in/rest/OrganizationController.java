package com.odcc.tienda.modules.organization.adapter.in.rest;

import com.odcc.tienda.modules.organization.adapter.in.rest.mapper.OrganizationRestMapper;
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
import com.odcc.tienda.modules.organization.application.model.BranchView;
import com.odcc.tienda.modules.organization.application.model.CashRegisterView;
import com.odcc.tienda.modules.organization.application.model.DeviceView;
import com.odcc.tienda.modules.organization.application.model.WarehouseView;
import com.odcc.tienda.modules.organization.application.port.in.OrganizationUseCases;
import com.odcc.tienda.modules.organization.domain.model.BranchStatus;
import com.odcc.tienda.modules.organization.domain.model.CashRegisterStatus;
import com.odcc.tienda.modules.organization.domain.model.DeviceStatus;
import com.odcc.tienda.modules.organization.domain.model.WarehouseStatus;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organization")
@RequiredArgsConstructor
@Tag(name = "Organizacion", description = "Administracion de sucursales, almacenes, cajas y dispositivos")
public class OrganizationController {

    private final OrganizationUseCases useCases;
    private final OrganizationRestMapper mapper;

    @PostMapping("/branches")
    @Operation(summary = "Crear sucursal")
    @PreAuthorize("hasAuthority('ORGANIZATION_BRANCH_CREATE')")
    public ResponseEntity<ApiResponseDto<BranchView>> createBranch(@Valid @RequestBody CreateBranchRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        BranchView branch = useCases.createBranch(mapper.toCreateBranchCommand(request), currentUserId(jwt));
        return created("BRANCH_CREATED", "Sucursal creada correctamente", branch, servletRequest);
    }

    @GetMapping("/branches")
    @Operation(summary = "Listar sucursales")
    @PreAuthorize("hasAuthority('ORGANIZATION_BRANCH_READ')")
    public ResponseEntity<ApiResponseDto<List<BranchView>>> listBranches(@RequestParam(required = false) BranchStatus status, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        return ok("BRANCHES_FOUND", "Sucursales consultadas correctamente", useCases.listBranches(status, currentUserId(jwt)), servletRequest);
    }

    @GetMapping("/branches/{branchId}")
    @Operation(summary = "Consultar sucursal por id")
    @PreAuthorize("hasAuthority('ORGANIZATION_BRANCH_READ')")
    public ResponseEntity<ApiResponseDto<BranchView>> getBranch(@PathVariable UUID branchId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        return ok("BRANCH_FOUND", "Sucursal consultada correctamente", useCases.getBranch(branchId, currentUserId(jwt)), servletRequest);
    }

    @PutMapping("/branches/{branchId}")
    @Operation(summary = "Actualizar sucursal")
    @PreAuthorize("hasAuthority('ORGANIZATION_BRANCH_UPDATE')")
    public ResponseEntity<ApiResponseDto<BranchView>> updateBranch(@PathVariable UUID branchId, @Valid @RequestBody UpdateBranchRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        BranchView branch = useCases.updateBranch(mapper.toUpdateBranchCommand(branchId, request), currentUserId(jwt));
        return ok("BRANCH_UPDATED", "Sucursal actualizada correctamente", branch, servletRequest);
    }

    @PatchMapping("/branches/{branchId}/status")
    @Operation(summary = "Cambiar estado de sucursal")
    @PreAuthorize("hasAuthority('ORGANIZATION_BRANCH_STATUS')")
    public ResponseEntity<ApiResponseDto<BranchView>> changeBranchStatus(@PathVariable UUID branchId, @Valid @RequestBody ChangeBranchStatusRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        BranchView branch = useCases.changeBranchStatus(mapper.toBranchStatusCommand(branchId, request), currentUserId(jwt));
        return ok("BRANCH_STATUS_UPDATED", "Estado de sucursal actualizado correctamente", branch, servletRequest);
    }

    @PostMapping("/warehouses")
    @Operation(summary = "Crear almacen")
    @PreAuthorize("hasAuthority('ORGANIZATION_WAREHOUSE_CREATE')")
    public ResponseEntity<ApiResponseDto<WarehouseView>> createWarehouse(@Valid @RequestBody CreateWarehouseRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        WarehouseView warehouse = useCases.createWarehouse(mapper.toCreateWarehouseCommand(request), currentUserId(jwt));
        return created("WAREHOUSE_CREATED", "Almacen creado correctamente", warehouse, servletRequest);
    }

    @GetMapping("/warehouses")
    @Operation(summary = "Listar almacenes")
    @PreAuthorize("hasAuthority('ORGANIZATION_WAREHOUSE_READ')")
    public ResponseEntity<ApiResponseDto<List<WarehouseView>>> listWarehouses(@RequestParam(required = false) UUID branchId, @RequestParam(required = false) WarehouseStatus status, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        return ok("WAREHOUSES_FOUND", "Almacenes consultados correctamente", useCases.listWarehouses(branchId, status, currentUserId(jwt)), servletRequest);
    }

    @GetMapping("/warehouses/{warehouseId}")
    @Operation(summary = "Consultar almacen por id")
    @PreAuthorize("hasAuthority('ORGANIZATION_WAREHOUSE_READ')")
    public ResponseEntity<ApiResponseDto<WarehouseView>> getWarehouse(@PathVariable UUID warehouseId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        return ok("WAREHOUSE_FOUND", "Almacen consultado correctamente", useCases.getWarehouse(warehouseId, currentUserId(jwt)), servletRequest);
    }

    @PutMapping("/warehouses/{warehouseId}")
    @Operation(summary = "Actualizar almacen")
    @PreAuthorize("hasAuthority('ORGANIZATION_WAREHOUSE_UPDATE')")
    public ResponseEntity<ApiResponseDto<WarehouseView>> updateWarehouse(@PathVariable UUID warehouseId, @Valid @RequestBody UpdateWarehouseRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        WarehouseView warehouse = useCases.updateWarehouse(mapper.toUpdateWarehouseCommand(warehouseId, request), currentUserId(jwt));
        return ok("WAREHOUSE_UPDATED", "Almacen actualizado correctamente", warehouse, servletRequest);
    }

    @PatchMapping("/warehouses/{warehouseId}/status")
    @Operation(summary = "Cambiar estado de almacen")
    @PreAuthorize("hasAuthority('ORGANIZATION_WAREHOUSE_STATUS')")
    public ResponseEntity<ApiResponseDto<WarehouseView>> changeWarehouseStatus(@PathVariable UUID warehouseId, @Valid @RequestBody ChangeWarehouseStatusRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        WarehouseView warehouse = useCases.changeWarehouseStatus(mapper.toWarehouseStatusCommand(warehouseId, request), currentUserId(jwt));
        return ok("WAREHOUSE_STATUS_UPDATED", "Estado de almacen actualizado correctamente", warehouse, servletRequest);
    }

    @PostMapping("/cash-registers")
    @Operation(summary = "Crear caja registradora")
    @PreAuthorize("hasAuthority('ORGANIZATION_CASH_REGISTER_CREATE')")
    public ResponseEntity<ApiResponseDto<CashRegisterView>> createCashRegister(@Valid @RequestBody CreateCashRegisterRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        CashRegisterView register = useCases.createCashRegister(mapper.toCreateCashRegisterCommand(request), currentUserId(jwt));
        return created("CASH_REGISTER_CREATED", "Caja registradora creada correctamente", register, servletRequest);
    }

    @GetMapping("/cash-registers")
    @Operation(summary = "Listar cajas registradoras")
    @PreAuthorize("hasAuthority('ORGANIZATION_CASH_REGISTER_READ')")
    public ResponseEntity<ApiResponseDto<List<CashRegisterView>>> listCashRegisters(@RequestParam(required = false) UUID branchId, @RequestParam(required = false) CashRegisterStatus status, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        return ok("CASH_REGISTERS_FOUND", "Cajas registradoras consultadas correctamente", useCases.listCashRegisters(branchId, status, currentUserId(jwt)), servletRequest);
    }

    @GetMapping("/cash-registers/{cashRegisterId}")
    @Operation(summary = "Consultar caja registradora por id")
    @PreAuthorize("hasAuthority('ORGANIZATION_CASH_REGISTER_READ')")
    public ResponseEntity<ApiResponseDto<CashRegisterView>> getCashRegister(@PathVariable UUID cashRegisterId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        return ok("CASH_REGISTER_FOUND", "Caja registradora consultada correctamente", useCases.getCashRegister(cashRegisterId, currentUserId(jwt)), servletRequest);
    }

    @PutMapping("/cash-registers/{cashRegisterId}")
    @Operation(summary = "Actualizar caja registradora")
    @PreAuthorize("hasAuthority('ORGANIZATION_CASH_REGISTER_UPDATE')")
    public ResponseEntity<ApiResponseDto<CashRegisterView>> updateCashRegister(@PathVariable UUID cashRegisterId, @Valid @RequestBody UpdateCashRegisterRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        CashRegisterView register = useCases.updateCashRegister(mapper.toUpdateCashRegisterCommand(cashRegisterId, request), currentUserId(jwt));
        return ok("CASH_REGISTER_UPDATED", "Caja registradora actualizada correctamente", register, servletRequest);
    }

    @PatchMapping("/cash-registers/{cashRegisterId}/status")
    @Operation(summary = "Cambiar estado de caja registradora")
    @PreAuthorize("hasAuthority('ORGANIZATION_CASH_REGISTER_STATUS')")
    public ResponseEntity<ApiResponseDto<CashRegisterView>> changeCashRegisterStatus(@PathVariable UUID cashRegisterId, @Valid @RequestBody ChangeCashRegisterStatusRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        CashRegisterView register = useCases.changeCashRegisterStatus(mapper.toCashRegisterStatusCommand(cashRegisterId, request), currentUserId(jwt));
        return ok("CASH_REGISTER_STATUS_UPDATED", "Estado de caja registradora actualizado correctamente", register, servletRequest);
    }

    @PostMapping("/devices")
    @Operation(summary = "Crear dispositivo")
    @PreAuthorize("hasAuthority('ORGANIZATION_DEVICE_CREATE')")
    public ResponseEntity<ApiResponseDto<DeviceView>> createDevice(@Valid @RequestBody CreateDeviceRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        DeviceView device = useCases.createDevice(mapper.toCreateDeviceCommand(request), currentUserId(jwt));
        return created("DEVICE_CREATED", "Dispositivo creado correctamente", device, servletRequest);
    }

    @GetMapping("/devices")
    @Operation(summary = "Listar dispositivos")
    @PreAuthorize("hasAuthority('ORGANIZATION_DEVICE_READ')")
    public ResponseEntity<ApiResponseDto<List<DeviceView>>> listDevices(@RequestParam(required = false) UUID branchId, @RequestParam(required = false) DeviceStatus status, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        return ok("DEVICES_FOUND", "Dispositivos consultados correctamente", useCases.listDevices(branchId, status, currentUserId(jwt)), servletRequest);
    }

    @GetMapping("/devices/{deviceId}")
    @Operation(summary = "Consultar dispositivo por id")
    @PreAuthorize("hasAuthority('ORGANIZATION_DEVICE_READ')")
    public ResponseEntity<ApiResponseDto<DeviceView>> getDevice(@PathVariable UUID deviceId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        return ok("DEVICE_FOUND", "Dispositivo consultado correctamente", useCases.getDevice(deviceId, currentUserId(jwt)), servletRequest);
    }

    @PutMapping("/devices/{deviceId}")
    @Operation(summary = "Actualizar dispositivo")
    @PreAuthorize("hasAuthority('ORGANIZATION_DEVICE_UPDATE')")
    public ResponseEntity<ApiResponseDto<DeviceView>> updateDevice(@PathVariable UUID deviceId, @Valid @RequestBody UpdateDeviceRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        DeviceView device = useCases.updateDevice(mapper.toUpdateDeviceCommand(deviceId, request), currentUserId(jwt));
        return ok("DEVICE_UPDATED", "Dispositivo actualizado correctamente", device, servletRequest);
    }

    @PatchMapping("/devices/{deviceId}/status")
    @Operation(summary = "Cambiar estado de dispositivo")
    @PreAuthorize("hasAuthority('ORGANIZATION_DEVICE_STATUS')")
    public ResponseEntity<ApiResponseDto<DeviceView>> changeDeviceStatus(@PathVariable UUID deviceId, @Valid @RequestBody ChangeDeviceStatusRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        DeviceView device = useCases.changeDeviceStatus(mapper.toDeviceStatusCommand(deviceId, request), currentUserId(jwt));
        return ok("DEVICE_STATUS_UPDATED", "Estado de dispositivo actualizado correctamente", device, servletRequest);
    }

    private <T> ResponseEntity<ApiResponseDto<T>> created(String code, String message, T data, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(HttpStatus.CREATED, code, message, data, request.getRequestURI()));
    }

    private <T> ResponseEntity<ApiResponseDto<T>> ok(String code, String message, T data, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, code, message, data, request.getRequestURI()));
    }

    private static UUID currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new IllegalStateException("El JWT no contiene usuario");
        return UUID.fromString(jwt.getSubject());
    }
}
