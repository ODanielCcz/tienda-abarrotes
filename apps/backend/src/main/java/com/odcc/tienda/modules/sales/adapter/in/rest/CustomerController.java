package com.odcc.tienda.modules.sales.adapter.in.rest;

import com.odcc.tienda.modules.sales.adapter.in.rest.request.ChangeCustomerStatusRequest;
import com.odcc.tienda.modules.sales.adapter.in.rest.request.CreateCustomerRequest;
import com.odcc.tienda.modules.sales.adapter.in.rest.request.UpdateCustomerRequest;
import com.odcc.tienda.modules.sales.application.command.ChangeCustomerStatusCommand;
import com.odcc.tienda.modules.sales.application.command.CreateCustomerCommand;
import com.odcc.tienda.modules.sales.application.command.UpdateCustomerCommand;
import com.odcc.tienda.modules.sales.application.model.Customer;
import com.odcc.tienda.modules.sales.application.port.in.CustomerUseCases;
import com.odcc.tienda.modules.sales.application.query.ListCustomersQuery;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/sales/customers")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Administracion de clientes de ventas")
public class CustomerController {

    private final CustomerUseCases useCases;

    @PostMapping
    @Operation(summary = "Crear cliente")
    @PreAuthorize("hasAuthority('SALES_CUSTOMER_CREATE')")
    public ResponseEntity<ApiResponseDto<Customer>> create(@Valid @RequestBody CreateCustomerRequest request, HttpServletRequest servletRequest) {
        Customer customer = useCases.create(new CreateCustomerCommand(request.customerCode(), request.customerType(), request.displayName(), request.email(), request.phone()), currentUserId(servletRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(HttpStatus.CREATED, "CUSTOMER_CREATED", "Cliente creado correctamente", customer, servletRequest.getRequestURI()));
    }

    @GetMapping
    @Operation(summary = "Listar clientes")
    @PreAuthorize("hasAuthority('SALES_CUSTOMER_READ')")
    public ResponseEntity<ApiResponseDto<List<Customer>>> list(@RequestParam(required = false) String search, @RequestParam(required = false) String customerType, @RequestParam(required = false) String status, HttpServletRequest servletRequest) {
        List<Customer> customers = useCases.list(new ListCustomersQuery(search, customerType, status), currentUserId(servletRequest));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "CUSTOMERS_FOUND", "Clientes consultados correctamente", customers, servletRequest.getRequestURI()));
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Consultar cliente por id")
    @PreAuthorize("hasAuthority('SALES_CUSTOMER_READ')")
    public ResponseEntity<ApiResponseDto<Customer>> getById(@PathVariable UUID customerId, HttpServletRequest servletRequest) {
        Customer customer = useCases.getById(customerId, currentUserId(servletRequest));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "CUSTOMER_FOUND", "Cliente consultado correctamente", customer, servletRequest.getRequestURI()));
    }

    @PutMapping("/{customerId}")
    @Operation(summary = "Actualizar cliente")
    @PreAuthorize("hasAuthority('SALES_CUSTOMER_UPDATE')")
    public ResponseEntity<ApiResponseDto<Customer>> update(@PathVariable UUID customerId, @Valid @RequestBody UpdateCustomerRequest request, HttpServletRequest servletRequest) {
        Customer customer = useCases.update(new UpdateCustomerCommand(customerId, request.customerCode(), request.customerType(), request.displayName(), request.email(), request.phone()), currentUserId(servletRequest));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "CUSTOMER_UPDATED", "Cliente actualizado correctamente", customer, servletRequest.getRequestURI()));
    }

    @PatchMapping("/{customerId}/status")
    @Operation(summary = "Cambiar estado de cliente")
    @PreAuthorize("hasAuthority('SALES_CUSTOMER_STATUS')")
    public ResponseEntity<ApiResponseDto<Customer>> changeStatus(@PathVariable UUID customerId, @Valid @RequestBody ChangeCustomerStatusRequest request, HttpServletRequest servletRequest) {
        Customer customer = useCases.changeStatus(new ChangeCustomerStatusCommand(customerId, request.status()), currentUserId(servletRequest));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "CUSTOMER_STATUS_UPDATED", "Estado de cliente actualizado correctamente", customer, servletRequest.getRequestURI()));
    }

    private static UUID currentUserId(HttpServletRequest request) {
        if (request.getUserPrincipal() == null || request.getUserPrincipal().getName() == null) {
            throw new IllegalStateException("El JWT no contiene usuario");
        }
        return UUID.fromString(request.getUserPrincipal().getName());
    }
}
