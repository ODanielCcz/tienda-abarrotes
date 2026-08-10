package com.odcc.tienda.modules.purchasing.application.usecase;

import com.odcc.tienda.modules.purchasing.application.command.ChangeSupplierStatusCommand;
import com.odcc.tienda.modules.purchasing.application.command.CreateSupplierCommand;
import com.odcc.tienda.modules.purchasing.application.command.UpdateSupplierCommand;
import com.odcc.tienda.modules.purchasing.application.exception.PurchasingException;
import com.odcc.tienda.modules.purchasing.application.exception.SupplierCodeAlreadyExistsException;
import com.odcc.tienda.modules.purchasing.application.exception.SupplierNotFoundException;
import com.odcc.tienda.modules.purchasing.application.model.Supplier;
import com.odcc.tienda.modules.purchasing.application.port.in.SupplierUseCases;
import com.odcc.tienda.modules.purchasing.application.port.out.SupplierRepositoryPort;
import com.odcc.tienda.modules.purchasing.application.query.ListSuppliersQuery;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.authorization.BranchAccessDeniedException;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
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
public class SupplierService implements SupplierUseCases {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]+$");

    private final SupplierRepositoryPort repository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;
    private final BranchAccessPort branchAccessPort;

    @Override
    public Supplier create(CreateSupplierCommand command, UUID actorUserId) {
        requireGlobalAccess(actorUserId);
        return transactionRunner.required(() -> {
            String code = normalizeCode(command.supplierCode());
            if (repository.existsByCode(code)) throw new SupplierCodeAlreadyExistsException(code);
            Instant now = Instant.now();
            Supplier saved = repository.save(new Supplier(UUID.randomUUID(), code, normalizeRequired(command.legalName(), "La razon social es obligatoria"), normalize(command.tradeName()), normalize(command.taxId()), normalize(command.email()), normalize(command.phone()), nonNegative(command.creditDays()), "ACTIVE", now, now));
            auditPort.record(new BusinessAuditEvent("SUPPLIER_CREATED", "SUPPLIER", saved.supplierId(), Map.of(), state(saved), Map.of()));
            return saved;
        });
    }

    @Override
    public Supplier getById(UUID supplierId, UUID actorUserId) {
        requireGlobalAccess(actorUserId);
        return repository.findById(supplierId).orElseThrow(() -> new SupplierNotFoundException(supplierId));
    }

    @Override
    public List<Supplier> list(ListSuppliersQuery query, UUID actorUserId) {
        requireGlobalAccess(actorUserId);
        return repository.findAll(query);
    }

    @Override
    public Supplier update(UpdateSupplierCommand command, UUID actorUserId) {
        requireGlobalAccess(actorUserId);
        return transactionRunner.required(() -> {
            Supplier current = repository.findById(command.supplierId())
                .orElseThrow(() -> new SupplierNotFoundException(command.supplierId()));
            String code = normalizeCode(command.supplierCode());
            if (repository.existsByCodeAndIdNot(code, command.supplierId())) throw new SupplierCodeAlreadyExistsException(code);
            Supplier updated = repository.save(new Supplier(current.supplierId(), code, normalizeRequired(command.legalName(), "La razon social es obligatoria"), normalize(command.tradeName()), normalize(command.taxId()), normalize(command.email()), normalize(command.phone()), nonNegative(command.creditDays()), current.status(), current.createdAt(), Instant.now()));
            auditPort.record(new BusinessAuditEvent("SUPPLIER_UPDATED", "SUPPLIER", updated.supplierId(), state(current), state(updated), Map.of()));
            return updated;
        });
    }

    @Override
    public Supplier changeStatus(ChangeSupplierStatusCommand command, UUID actorUserId) {
        requireGlobalAccess(actorUserId);
        return transactionRunner.required(() -> {
            Supplier current = repository.findById(command.supplierId())
                .orElseThrow(() -> new SupplierNotFoundException(command.supplierId()));
            String status = normalizeStatus(command.status());
            Supplier updated = repository.save(new Supplier(current.supplierId(), current.supplierCode(), current.legalName(), current.tradeName(), current.taxId(), current.email(), current.phone(), current.creditDays(), status, current.createdAt(), Instant.now()));
            auditPort.record(new BusinessAuditEvent("SUPPLIER_STATUS_CHANGED", "SUPPLIER", updated.supplierId(), state(current), state(updated), Map.of()));
            return updated;
        });
    }

    private void requireGlobalAccess(UUID actorUserId) {
        if (!branchAccessPort.resolveScope(actorUserId).globalAccess()) {
            throw new BranchAccessDeniedException();
        }
    }

    private static Map<String, Object> state(Supplier supplier) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("supplierCode", supplier.supplierCode());
        state.put("legalName", supplier.legalName());
        state.put("status", supplier.status());
        state.put("creditDays", supplier.creditDays());
        return state;
    }

    private static String normalizeCode(String code) {
        String normalized = normalizeRequired(code, "El codigo del proveedor es obligatorio").toUpperCase(Locale.ROOT);
        if (normalized.length() > 50 || !CODE_PATTERN.matcher(normalized).matches()) throw new PurchasingException("El codigo del proveedor solo acepta letras, numeros, guiones y guiones bajos, maximo 50 caracteres");
        return normalized;
    }

    private static String normalizeStatus(String status) {
        String normalized = normalizeRequired(status, "El estado es obligatorio").toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "INACTIVE", "BLOCKED").contains(normalized)) throw new PurchasingException("Estado de proveedor invalido");
        return normalized;
    }

    private static int nonNegative(Integer value) {
        int normalized = value == null ? 0 : value;
        if (normalized < 0) throw new PurchasingException("Los dias de credito no pueden ser negativos");
        return normalized;
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) throw new PurchasingException(message);
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
