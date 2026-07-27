package com.odcc.tienda.modules.sales.application.usecase;

import com.odcc.tienda.modules.sales.application.command.ChangeCustomerStatusCommand;
import com.odcc.tienda.modules.sales.application.command.CreateCustomerCommand;
import com.odcc.tienda.modules.sales.application.command.UpdateCustomerCommand;
import com.odcc.tienda.modules.sales.application.exception.CustomerCodeAlreadyExistsException;
import com.odcc.tienda.modules.sales.application.exception.CustomerNotFoundException;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.model.Customer;
import com.odcc.tienda.modules.sales.application.port.in.CustomerUseCases;
import com.odcc.tienda.modules.sales.application.port.out.CustomerRepositoryPort;
import com.odcc.tienda.modules.sales.application.query.ListCustomersQuery;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class CustomerService implements CustomerUseCases {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final List<String> TYPES = List.of("GENERAL", "PERSON", "BUSINESS");
    private static final List<String> STATUSES = List.of("ACTIVE", "INACTIVE", "BLOCKED");

    private final CustomerRepositoryPort repository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public Customer create(CreateCustomerCommand command) {
        return transactionRunner.required(() -> {
            String code = normalizeOptionalCode(command == null ? null : command.customerCode());
            if (code != null && repository.existsByCode(code)) throw new CustomerCodeAlreadyExistsException(code);
            Instant now = Instant.now();
            Customer saved = repository.save(new Customer(
                UUID.randomUUID(),
                code,
                normalizeType(command == null ? null : command.customerType()),
                normalizeRequired(command == null ? null : command.displayName(), "El nombre del cliente es obligatorio", 200),
                normalizeEmail(command == null ? null : command.email()),
                normalizeOptional(command == null ? null : command.phone(), 40, "El telefono no puede exceder 40 caracteres"),
                "ACTIVE",
                now,
                now
            ));
            auditPort.record(new BusinessAuditEvent("CUSTOMER_CREATED", "CUSTOMER", saved.customerId(), Map.of(), state(saved), Map.of()));
            return saved;
        });
    }

    @Override
    public Customer getById(UUID customerId) {
        return repository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    @Override
    public List<Customer> list(ListCustomersQuery query) {
        return repository.findAll(query);
    }

    @Override
    public Customer update(UpdateCustomerCommand command) {
        return transactionRunner.required(() -> {
            if (command == null || command.customerId() == null) throw new SalesException("El cliente es obligatorio");
            Customer current = getById(command.customerId());
            String code = normalizeOptionalCode(command.customerCode());
            if (code != null && repository.existsByCodeAndIdNot(code, command.customerId())) throw new CustomerCodeAlreadyExistsException(code);
            Customer updated = repository.save(new Customer(
                current.customerId(),
                code,
                normalizeType(command.customerType()),
                normalizeRequired(command.displayName(), "El nombre del cliente es obligatorio", 200),
                normalizeEmail(command.email()),
                normalizeOptional(command.phone(), 40, "El telefono no puede exceder 40 caracteres"),
                current.status(),
                current.createdAt(),
                Instant.now()
            ));
            auditPort.record(new BusinessAuditEvent("CUSTOMER_UPDATED", "CUSTOMER", updated.customerId(), state(current), state(updated), Map.of()));
            return updated;
        });
    }

    @Override
    public Customer changeStatus(ChangeCustomerStatusCommand command) {
        return transactionRunner.required(() -> {
            if (command == null || command.customerId() == null) throw new SalesException("El cliente es obligatorio");
            Customer current = getById(command.customerId());
            Customer updated = repository.save(new Customer(
                current.customerId(),
                current.customerCode(),
                current.customerType(),
                current.displayName(),
                current.email(),
                current.phone(),
                normalizeStatus(command.status()),
                current.createdAt(),
                Instant.now()
            ));
            auditPort.record(new BusinessAuditEvent("CUSTOMER_STATUS_CHANGED", "CUSTOMER", updated.customerId(), state(current), state(updated), Map.of()));
            return updated;
        });
    }

    private static Map<String, Object> state(Customer customer) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("customerCode", customer.customerCode());
        state.put("customerType", customer.customerType());
        state.put("displayName", customer.displayName());
        state.put("status", customer.status());
        return state;
    }

    private static String normalizeOptionalCode(String code) {
        String normalized = normalize(code);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (normalized.length() > 50 || !CODE_PATTERN.matcher(normalized).matches()) throw new SalesException("El codigo del cliente solo acepta letras, numeros, guiones y guiones bajos, maximo 50 caracteres");
        return normalized;
    }

    private static String normalizeType(String type) {
        String normalized = normalize(type);
        if (normalized == null) return "PERSON";
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalized)) throw new SalesException("Tipo de cliente invalido");
        return normalized;
    }

    private static String normalizeStatus(String status) {
        String normalized = normalize(status);
        if (normalized == null) throw new SalesException("El estado del cliente es obligatorio");
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new SalesException("Estado de cliente invalido");
        return normalized;
    }

    private static String normalizeEmail(String email) {
        String normalized = normalizeOptional(email, 254, "El email no puede exceder 254 caracteres");
        if (normalized != null && !EMAIL_PATTERN.matcher(normalized).matches()) throw new SalesException("Email de cliente invalido");
        return normalized;
    }

    private static String normalizeRequired(String value, String message, int maxLength) {
        String normalized = normalize(value);
        if (normalized == null) throw new SalesException(message);
        if (normalized.length() > maxLength) throw new SalesException(message + ", maximo " + maxLength + " caracteres");
        return normalized;
    }

    private static String normalizeOptional(String value, int maxLength, String message) {
        String normalized = normalize(value);
        if (normalized != null && normalized.length() > maxLength) throw new SalesException(message);
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
