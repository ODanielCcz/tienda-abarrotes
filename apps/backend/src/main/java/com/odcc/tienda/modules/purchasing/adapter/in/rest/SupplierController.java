package com.odcc.tienda.modules.purchasing.adapter.in.rest;

import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.ChangeSupplierStatusRequest;
import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.CreateSupplierRequest;
import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.UpdateSupplierRequest;
import com.odcc.tienda.modules.purchasing.application.command.ChangeSupplierStatusCommand;
import com.odcc.tienda.modules.purchasing.application.command.CreateSupplierCommand;
import com.odcc.tienda.modules.purchasing.application.command.UpdateSupplierCommand;
import com.odcc.tienda.modules.purchasing.application.model.Supplier;
import com.odcc.tienda.modules.purchasing.application.port.in.SupplierUseCases;
import com.odcc.tienda.modules.purchasing.application.query.ListSuppliersQuery;
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
@RequestMapping("/api/v1/purchasing/suppliers")
@RequiredArgsConstructor
@Tag(name = "Proveedores", description = "Administracion de proveedores")
public class SupplierController {

    private final SupplierUseCases useCases;

    @PostMapping
    @Operation(summary = "Crear proveedor")
    @PreAuthorize("hasAuthority('PURCHASING_SUPPLIER_CREATE')")
    public ResponseEntity<ApiResponseDto<Supplier>> create(@Valid @RequestBody CreateSupplierRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        Supplier supplier = useCases.create(new CreateSupplierCommand(request.supplierCode(), request.legalName(), request.tradeName(), request.taxId(), request.email(), request.phone(), request.creditDays()), currentUserId(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(HttpStatus.CREATED, "SUPPLIER_CREATED", "Proveedor creado correctamente", supplier, servletRequest.getRequestURI()));
    }

    @GetMapping
    @Operation(summary = "Listar proveedores")
    @PreAuthorize("hasAuthority('PURCHASING_SUPPLIER_READ')")
    public ResponseEntity<ApiResponseDto<List<Supplier>>> list(@RequestParam(required = false) String search, @RequestParam(required = false) String status, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        List<Supplier> suppliers = useCases.list(new ListSuppliersQuery(search, status), currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "SUPPLIERS_FOUND", "Proveedores consultados correctamente", suppliers, servletRequest.getRequestURI()));
    }

    @GetMapping("/{supplierId}")
    @Operation(summary = "Consultar proveedor por id")
    @PreAuthorize("hasAuthority('PURCHASING_SUPPLIER_READ')")
    public ResponseEntity<ApiResponseDto<Supplier>> getById(@PathVariable UUID supplierId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        Supplier supplier = useCases.getById(supplierId, currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "SUPPLIER_FOUND", "Proveedor consultado correctamente", supplier, servletRequest.getRequestURI()));
    }

    @PutMapping("/{supplierId}")
    @Operation(summary = "Actualizar proveedor")
    @PreAuthorize("hasAuthority('PURCHASING_SUPPLIER_UPDATE')")
    public ResponseEntity<ApiResponseDto<Supplier>> update(@PathVariable UUID supplierId, @Valid @RequestBody UpdateSupplierRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        Supplier supplier = useCases.update(new UpdateSupplierCommand(supplierId, request.supplierCode(), request.legalName(), request.tradeName(), request.taxId(), request.email(), request.phone(), request.creditDays()), currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "SUPPLIER_UPDATED", "Proveedor actualizado correctamente", supplier, servletRequest.getRequestURI()));
    }

    @PatchMapping("/{supplierId}/status")
    @Operation(summary = "Cambiar estado de proveedor")
    @PreAuthorize("hasAuthority('PURCHASING_SUPPLIER_STATUS')")
    public ResponseEntity<ApiResponseDto<Supplier>> changeStatus(@PathVariable UUID supplierId, @Valid @RequestBody ChangeSupplierStatusRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        Supplier supplier = useCases.changeStatus(new ChangeSupplierStatusCommand(supplierId, request.status()), currentUserId(jwt));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "SUPPLIER_STATUS_UPDATED", "Estado de proveedor actualizado correctamente", supplier, servletRequest.getRequestURI()));
    }

    private static UUID currentUserId(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getSubject());
    }
}
