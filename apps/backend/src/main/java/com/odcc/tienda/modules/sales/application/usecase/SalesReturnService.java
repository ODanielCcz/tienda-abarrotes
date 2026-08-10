package com.odcc.tienda.modules.sales.application.usecase;

import com.odcc.tienda.modules.sales.application.command.ConfirmSalesReturnCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesReturnCommand;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.exception.SalesReturnNotFoundException;
import com.odcc.tienda.modules.sales.application.model.SalesReturn;
import com.odcc.tienda.modules.sales.application.port.in.SalesReturnUseCases;
import com.odcc.tienda.modules.sales.application.port.out.SalesReturnRepositoryPort;
import com.odcc.tienda.modules.sales.application.port.out.SalesOrderRepositoryPort;
import com.odcc.tienda.modules.sales.application.exception.SalesOrderNotFoundException;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class SalesReturnService implements SalesReturnUseCases {

    private final SalesReturnRepositoryPort repository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;
    private final SalesOrderRepositoryPort salesOrderRepository;
    private final BranchAccessPort branchAccessPort;

    @Override
    public SalesReturn create(CreateSalesReturnCommand command) {
        validateCreate(command);
        requireOrderAccess(command.salesOrderId(), command.createdBy());
        return transactionRunner.required(() -> {
            SalesReturn salesReturn = repository.createDraft(command);
            auditPort.record(new BusinessAuditEvent("SALES_RETURN_CREATED", "SALES_RETURN", salesReturn.returnId(), Map.of(), state(salesReturn), Map.of()));
            return salesReturn;
        });
    }

    @Override
    public SalesReturn getById(UUID returnId, UUID actorUserId) {
        if (returnId == null) throw new SalesException("La devolucion es obligatoria");
        SalesReturn salesReturn = findById(returnId);
        requireOrderAccess(salesReturn.salesOrderId(), actorUserId);
        return salesReturn;
    }

    @Override
    public SalesReturn confirm(ConfirmSalesReturnCommand command) {
        if (command == null || command.returnId() == null) throw new SalesException("La devolucion es obligatoria");
        if (command.confirmedBy() == null) throw new SalesException("El usuario que confirma es obligatorio");
        return transactionRunner.required(() -> {
            SalesReturn before = getById(command.returnId(), command.confirmedBy());
            SalesReturn confirmed = repository.confirm(command);
            auditPort.record(new BusinessAuditEvent("SALES_RETURN_CONFIRMED", "SALES_RETURN", confirmed.returnId(), state(before), state(confirmed), Map.of()));
            return confirmed;
        });
    }

    @Override
    public SalesReturn cancel(UUID returnId, UUID actorUserId) {
        if (returnId == null) throw new SalesException("La devolucion es obligatoria");
        return transactionRunner.required(() -> {
            SalesReturn before = getById(returnId, actorUserId);
            SalesReturn cancelled = repository.cancel(returnId);
            auditPort.record(new BusinessAuditEvent("SALES_RETURN_CANCELLED", "SALES_RETURN", cancelled.returnId(), state(before), state(cancelled), Map.of()));
            return cancelled;
        });
    }

    private SalesReturn findById(UUID returnId) {
        return repository.findById(returnId).orElseThrow(() -> new SalesReturnNotFoundException(returnId));
    }

    private void requireOrderAccess(UUID salesOrderId, UUID actorUserId) {
        var order = salesOrderRepository.findById(salesOrderId)
            .orElseThrow(() -> new SalesOrderNotFoundException(salesOrderId));
        branchAccessPort.requireAccess(actorUserId, order.branchId());
    }

    private void validateCreate(CreateSalesReturnCommand command) {
        if (command == null) throw new SalesException("La devolucion es obligatoria");
        if (command.salesOrderId() == null) throw new SalesException("La venta es obligatoria");
        if (command.createdBy() == null) throw new SalesException("El usuario que crea la devolucion es obligatorio");
        if (command.reason() == null || command.reason().isBlank()) throw new SalesException("El motivo de devolucion es obligatorio");
        if (command.items() == null || command.items().isEmpty()) throw new SalesException("La devolucion requiere al menos un producto");
        command.items().forEach(item -> {
            if (item.salesOrderItemId() == null) throw new SalesException("El item de venta es obligatorio");
            if (item.quantity() == null || item.quantity().compareTo(BigDecimal.ZERO) <= 0) throw new SalesException("La cantidad a devolver debe ser mayor a cero");
        });
    }

    private Map<String, Object> state(SalesReturn salesReturn) {
        return Map.of(
            "salesOrderId", salesReturn.salesOrderId(),
            "status", salesReturn.status(),
            "total", salesReturn.total()
        );
    }
}
