package com.odcc.tienda.modules.cash.application.usecase;

import com.odcc.tienda.modules.cash.application.command.CloseCashSessionCommand;
import com.odcc.tienda.modules.cash.application.command.CreateCashMovementCommand;
import com.odcc.tienda.modules.cash.application.command.OpenCashSessionCommand;
import com.odcc.tienda.modules.cash.application.exception.CashException;
import com.odcc.tienda.modules.cash.application.exception.CashSessionNotFoundException;
import com.odcc.tienda.modules.cash.application.model.CashMovement;
import com.odcc.tienda.modules.cash.application.model.CashSession;
import com.odcc.tienda.modules.cash.application.port.in.CashSessionUseCases;
import com.odcc.tienda.modules.cash.application.port.out.CashSessionRepositoryPort;
import com.odcc.tienda.modules.cash.application.query.ListCashSessionsQuery;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class CashSessionService implements CashSessionUseCases {

    private final CashSessionRepositoryPort repository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public CashSession open(OpenCashSessionCommand command) {
        validateOpen(command);
        return transactionRunner.required(() -> {
            CashSession session = repository.open(command);
            auditPort.record(new BusinessAuditEvent("CASH_SESSION_OPENED", "CASH_SESSION", session.cashSessionId(), Map.of(), Map.of("status", session.status(), "openingAmount", session.openingAmount()), Map.of()));
            return session;
        });
    }

    @Override
    public CashSession getById(UUID cashSessionId) {
        return repository.findById(cashSessionId).orElseThrow(() -> new CashSessionNotFoundException(cashSessionId));
    }

    @Override
    public List<CashSession> list(ListCashSessionsQuery query) {
        return repository.findAll(query);
    }

    @Override
    public CashSession close(CloseCashSessionCommand command) {
        validateClose(command);
        return transactionRunner.required(() -> {
            CashSession before = getById(command.cashSessionId());
            CashSession closed = repository.close(command);
            auditPort.record(new BusinessAuditEvent("CASH_SESSION_CLOSED", "CASH_SESSION", closed.cashSessionId(), Map.of("status", before.status()), Map.of("status", closed.status(), "expectedAmount", closed.expectedAmount(), "countedAmount", closed.countedAmount(), "differenceAmount", closed.differenceAmount()), Map.of()));
            return closed;
        });
    }

    @Override
    public List<CashMovement> listMovements(UUID cashSessionId) {
        getById(cashSessionId);
        return repository.findMovements(cashSessionId);
    }

    @Override
    public CashMovement createMovement(CreateCashMovementCommand command) {
        validateMovement(command);
        return transactionRunner.required(() -> {
            CashMovement movement = repository.createManualMovement(command);
            auditPort.record(new BusinessAuditEvent("CASH_MOVEMENT_CREATED", "CASH_MOVEMENT", movement.cashMovementId(), Map.of(), Map.of("cashSessionId", movement.cashSessionId(), "movementType", movement.movementType(), "direction", movement.direction(), "amount", movement.amount()), Map.of()));
            return movement;
        });
    }

    private void validateOpen(OpenCashSessionCommand command) {
        if (command == null) throw new CashException("La apertura de caja es obligatoria");
        if (command.cashRegisterId() == null) throw new CashException("La caja registradora es obligatoria");
        if (command.openedBy() == null) throw new CashException("El usuario de apertura es obligatorio");
        if (command.openingAmount() == null || command.openingAmount().compareTo(BigDecimal.ZERO) < 0) throw new CashException("El monto inicial no puede ser negativo");
    }

    private void validateClose(CloseCashSessionCommand command) {
        if (command == null) throw new CashException("El cierre de caja es obligatorio");
        if (command.cashSessionId() == null) throw new CashException("La sesion de caja es obligatoria");
        if (command.closedBy() == null) throw new CashException("El usuario de cierre es obligatorio");
        if (command.countedCashAmount() == null || command.countedCashAmount().compareTo(BigDecimal.ZERO) < 0) throw new CashException("El efectivo contado no puede ser negativo");
    }

    private void validateMovement(CreateCashMovementCommand command) {
        if (command == null) throw new CashException("El movimiento de caja es obligatorio");
        if (command.cashSessionId() == null) throw new CashException("La sesion de caja es obligatoria");
        if (command.createdBy() == null) throw new CashException("El usuario del movimiento es obligatorio");
        if (command.amount() == null || command.amount().compareTo(BigDecimal.ZERO) <= 0) throw new CashException("El monto del movimiento debe ser mayor a cero");
        String type = normalize(command.movementType());
        String direction = normalize(command.direction());
        if (!List.of("CASH_IN", "CASH_OUT", "ADJUSTMENT").contains(type)) throw new CashException("Tipo de movimiento manual invalido");
        if (!List.of("IN", "OUT").contains(direction)) throw new CashException("Direccion de movimiento invalida");
        if ("CASH_IN".equals(type) && !"IN".equals(direction)) throw new CashException("CASH_IN debe tener direccion IN");
        if ("CASH_OUT".equals(type) && !"OUT".equals(direction)) throw new CashException("CASH_OUT debe tener direccion OUT");
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}