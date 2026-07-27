package com.odcc.tienda.modules.sales.application.usecase;

import com.odcc.tienda.modules.sales.application.command.CreateSalesPaymentCommand;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.exception.SalesOrderNotFoundException;
import com.odcc.tienda.modules.sales.application.exception.SalesPaymentIdempotencyConflictException;
import com.odcc.tienda.modules.sales.application.model.SalesPayment;
import com.odcc.tienda.modules.sales.application.port.in.SalesPaymentUseCases;
import com.odcc.tienda.modules.sales.application.port.out.SalesOrderRepositoryPort;
import com.odcc.tienda.modules.sales.application.port.out.SalesPaymentRepositoryPort;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public class SalesPaymentService implements SalesPaymentUseCases {

    private final SalesPaymentRepositoryPort repository;
    private final SalesOrderRepositoryPort salesOrderRepository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public SalesPayment create(CreateSalesPaymentCommand command) {
        validate(command);
        String fingerprint = fingerprint(command);
        return transactionRunner.required(() -> {
            if (repository.existsByIdempotencyKeyWithDifferentFingerprint(command.idempotencyKey(), fingerprint)) {
                throw new SalesPaymentIdempotencyConflictException(command.idempotencyKey());
            }
            var existing = repository.findByIdempotencyKey(command.idempotencyKey(), fingerprint);
            if (existing.isPresent()) return existing.get();
            SalesPayment payment = repository.createCaptured(command, fingerprint);
            auditPort.record(new BusinessAuditEvent("SALES_PAYMENT_CREATED", "SALES_PAYMENT", payment.paymentId(), Map.of(), Map.of("salesOrderId", payment.salesOrderId(), "amount", payment.amount(), "paymentMethod", payment.paymentMethod()), Map.of()));
            return payment;
        });
    }

    @Override
    public List<SalesPayment> listBySalesOrder(UUID salesOrderId) {
        if (salesOrderId == null) throw new SalesException("La venta es obligatoria");
        if (salesOrderRepository.findById(salesOrderId).isEmpty()) throw new SalesOrderNotFoundException(salesOrderId);
        return repository.findBySalesOrderId(salesOrderId);
    }

    private void validate(CreateSalesPaymentCommand command) {
        if (command == null) throw new SalesException("El pago es obligatorio");
        if (command.salesOrderId() == null) throw new SalesException("La venta es obligatoria");
        if (command.idempotencyKey() == null) throw new SalesException("La llave de idempotencia es obligatoria");
        if (command.createdBy() == null) throw new SalesException("El usuario que registra el pago es obligatorio");
        if (command.amount() == null || command.amount().compareTo(BigDecimal.ZERO) <= 0) throw new SalesException("El monto del pago debe ser mayor a cero");
        String method = normalize(command.paymentMethod(), null);
        if (!List.of("CASH", "CARD", "TRANSFER", "CREDIT", "MIXED", "ONLINE_GATEWAY").contains(method)) throw new SalesException("Metodo de pago invalido");
        if ("CASH".equals(method) && command.cashSessionId() == null) throw new SalesException("Los pagos en efectivo requieren una sesion de caja abierta");
    }

    private static String fingerprint(CreateSalesPaymentCommand command) {
        String value = Objects.toString(command.salesOrderId(), "") + '|'
            + Objects.toString(command.cashSessionId(), "") + '|'
            + normalize(command.paymentMethod(), "") + '|'
            + number(command.amount()) + '|'
            + normalize(command.currencyCode(), "MXN") + '|'
            + Objects.toString(command.reference(), "");
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String normalize(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase();
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}
