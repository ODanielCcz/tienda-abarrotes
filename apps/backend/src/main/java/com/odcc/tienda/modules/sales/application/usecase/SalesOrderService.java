package com.odcc.tienda.modules.sales.application.usecase;

import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderItemCommand;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.exception.SalesOrderIdempotencyConflictException;
import com.odcc.tienda.modules.sales.application.exception.SalesOrderNotFoundException;
import com.odcc.tienda.modules.sales.application.exception.PriceNotConfiguredException;
import com.odcc.tienda.modules.sales.application.exception.SalesPriceChangedException;
import com.odcc.tienda.modules.sales.application.model.SalesOrder;
import com.odcc.tienda.modules.sales.application.model.SalesOrderExecution;
import com.odcc.tienda.modules.sales.application.port.in.SalesOrderUseCases;
import com.odcc.tienda.modules.sales.application.port.out.SalesOrderRepositoryPort;
import com.odcc.tienda.modules.sales.application.query.ListSalesOrdersQuery;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.authorization.BranchScope;
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
    private final BranchAccessPort branchAccessPort;

    @Override
    public SalesOrder create(CreateSalesOrderCommand command, UUID actorUserId) {
        validateCreate(command);
        branchAccessPort.requireAccess(actorUserId, repository.findBranchIdByWarehouseId(command.warehouseId()));
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
            SalesOrderExecution execution = repository.createConfirmedWithOutcome(
                resolveServerPrices(command),
                fingerprint
            );
            SalesOrder order = execution.salesOrder();
            if (!execution.wasCreated()) return order;
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("orderNumber", order.orderNumber());
            after.put("total", order.total());
            if (order.customerId() != null) after.put("customerId", order.customerId());
            auditPort.record(new BusinessAuditEvent("SALES_ORDER_CREATED", "SALES_ORDER", order.salesOrderId(), Map.of(), after, Map.of()));
            return order;
        });
    }

    @Override
    public SalesOrder getById(UUID salesOrderId, UUID actorUserId) {
        SalesOrder order = findById(salesOrderId);
        branchAccessPort.requireAccess(actorUserId, order.branchId());
        return order;
    }

    @Override
    public List<SalesOrder> list(ListSalesOrdersQuery query, UUID actorUserId) {
        BranchScope scope = branchAccessPort.resolveScope(actorUserId);
        if (query != null && query.warehouseId() != null) {
            branchAccessPort.requireAccess(actorUserId, repository.findBranchIdByWarehouseId(query.warehouseId()));
        }
        return repository.findAll(query, scope);
    }

    @Override
    public SalesOrder cancel(UUID salesOrderId, UUID actorUserId) {
        return transactionRunner.required(() -> {
            SalesOrder before = getById(salesOrderId, actorUserId);
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
            if (item.discountAmount() != null && item.discountAmount().compareTo(ZERO) != 0) {
                throw new SalesException("Los descuentos manuales no estan soportados");
            }
        });
    }

    private SalesOrder findById(UUID salesOrderId) {
        return repository.findById(salesOrderId).orElseThrow(() -> new SalesOrderNotFoundException(salesOrderId));
    }

    private CreateSalesOrderCommand resolveServerPrices(CreateSalesOrderCommand command) {
        String currency = normalize(command.currencyCode(), "MXN");
        List<CreateSalesOrderItemCommand> items = command.items().stream()
            .map(item -> {
                BigDecimal currentPrice = repository.findCurrentPrice(
                    command.warehouseId(),
                    item.productPresentationId(),
                    currency
                ).orElseThrow(() -> new PriceNotConfiguredException(item.productPresentationId()));
                if (item.unitPrice().compareTo(currentPrice) != 0) {
                    throw new SalesPriceChangedException(
                        item.productPresentationId(),
                        item.unitPrice(),
                        currentPrice
                    );
                }
                return new CreateSalesOrderItemCommand(
                    item.productPresentationId(),
                    item.quantity(),
                    currentPrice,
                    ZERO
                );
            })
            .toList();
        return new CreateSalesOrderCommand(
            command.warehouseId(),
            command.customerId(),
            command.deviceId(),
            command.channel(),
            currency,
            command.idempotencyKey(),
            items
        );
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
