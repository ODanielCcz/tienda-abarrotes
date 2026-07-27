package com.odcc.tienda.modules.sales.application.usecase;

import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderItemCommand;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.exception.SalesOrderIdempotencyConflictException;
import com.odcc.tienda.modules.sales.application.exception.SalesOrderNotFoundException;
import com.odcc.tienda.modules.sales.application.model.SalesOrder;
import com.odcc.tienda.modules.sales.application.port.in.SalesOrderUseCases;
import com.odcc.tienda.modules.sales.application.port.out.SalesOrderRepositoryPort;
import com.odcc.tienda.modules.sales.application.query.ListSalesOrdersQuery;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public class SalesOrderService implements SalesOrderUseCases {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final SalesOrderRepositoryPort repository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public SalesOrder create(CreateSalesOrderCommand command) {
        validateCreate(command);
        String fingerprint = fingerprint(command);
        return transactionRunner.required(() -> {
            if (repository.existsByIdempotencyKeyWithDifferentFingerprint(command.idempotencyKey(), fingerprint)) {
                throw new SalesOrderIdempotencyConflictException(command.idempotencyKey());
            }
            var existing = repository.findByIdempotencyKey(command.idempotencyKey(), fingerprint);
            if (existing.isPresent()) return existing.get();
            if (command.customerId() != null && !repository.customerIsActive(command.customerId())) {
                throw new SalesException("El cliente no existe o no esta activo");
            }
            SalesOrder order = repository.createConfirmed(command, fingerprint);
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("orderNumber", order.orderNumber());
            after.put("total", order.total());
            if (order.customerId() != null) after.put("customerId", order.customerId());
            auditPort.record(new BusinessAuditEvent("SALES_ORDER_CREATED", "SALES_ORDER", order.salesOrderId(), Map.of(), after, Map.of()));
            return order;
        });
    }

    @Override
    public SalesOrder getById(UUID salesOrderId) {
        return repository.findById(salesOrderId).orElseThrow(() -> new SalesOrderNotFoundException(salesOrderId));
    }

    @Override
    public List<SalesOrder> list(ListSalesOrdersQuery query) {
        return repository.findAll(query);
    }

    @Override
    public SalesOrder cancel(UUID salesOrderId) {
        return transactionRunner.required(() -> {
            SalesOrder before = getById(salesOrderId);
            SalesOrder cancelled = repository.cancel(salesOrderId);
            auditPort.record(new BusinessAuditEvent("SALES_ORDER_CANCELLED", "SALES_ORDER", cancelled.salesOrderId(), Map.of("status", before.status()), Map.of("status", cancelled.status()), Map.of()));
            return cancelled;
        });
    }

    private void validateCreate(CreateSalesOrderCommand command) {
        if (command == null) throw new SalesException("La venta es obligatoria");
        if (command.warehouseId() == null) throw new SalesException("El almacen es obligatorio");
        if (command.idempotencyKey() == null) throw new SalesException("La llave de idempotencia es obligatoria");
        String channel = command.channel() == null ? "POS" : command.channel().trim().toUpperCase();
        if (!List.of("POS", "WEB", "MOBILE").contains(channel)) throw new SalesException("Canal de venta invalido");
        if (command.items() == null || command.items().isEmpty()) throw new SalesException("La venta debe incluir items");
        command.items().forEach(item -> {
            if (item.productPresentationId() == null) throw new SalesException("La presentacion es obligatoria");
            if (item.quantity() == null || item.quantity().compareTo(ZERO) <= 0) throw new SalesException("La cantidad debe ser mayor a cero");
            if (item.unitPrice() == null || item.unitPrice().compareTo(ZERO) < 0) throw new SalesException("El precio unitario no puede ser negativo");
            if (item.discountAmount() != null && item.discountAmount().compareTo(ZERO) < 0) throw new SalesException("El descuento no puede ser negativo");
        });
    }

    private static String fingerprint(CreateSalesOrderCommand command) {
        StringBuilder builder = new StringBuilder();
        builder.append(command.warehouseId()).append('|')
            .append(command.customerId()).append('|')
            .append(command.deviceId()).append('|')
            .append(normalize(command.channel(), "POS")).append('|')
            .append(normalize(command.currencyCode(), "MXN")).append('|');
        command.items().stream()
            .map(SalesOrderService::itemFingerprint)
            .sorted()
            .forEach(value -> builder.append(value).append('|'));
        return UUID.nameUUIDFromBytes(builder.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String itemFingerprint(CreateSalesOrderItemCommand item) {
        return Objects.toString(item.productPresentationId(), "") + ':'
            + number(item.quantity()) + ':'
            + number(item.unitPrice()) + ':'
            + number(item.discountAmount());
    }

    private static String normalize(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase();
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}
