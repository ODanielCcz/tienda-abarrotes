package com.odcc.tienda.modules.sales.support;

import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderItemCommand;
import com.odcc.tienda.modules.sales.application.model.SalesOrder;
import com.odcc.tienda.modules.sales.application.model.SalesOrderItem;
import com.odcc.tienda.modules.sales.application.port.out.SalesOrderRepositoryPort;
import com.odcc.tienda.modules.sales.application.query.ListSalesOrdersQuery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class InMemorySalesOrderRepository implements SalesOrderRepositoryPort {

    private final Map<UUID, SalesOrder> orders = new LinkedHashMap<>();
    private final Map<UUID, String> fingerprintsByIdempotencyKey = new LinkedHashMap<>();
    private final Set<UUID> activeCustomers = new java.util.HashSet<>();

    public void addActiveCustomer(UUID customerId) {
        activeCustomers.add(customerId);
    }

    @Override
    public Optional<SalesOrder> findByIdempotencyKey(UUID idempotencyKey, String fingerprint) {
        return orders.values().stream()
            .filter(order -> idempotencyKey.equals(order.idempotencyKey()))
            .filter(order -> fingerprint.equals(fingerprintsByIdempotencyKey.get(idempotencyKey)))
            .findFirst();
    }

    @Override
    public boolean existsByIdempotencyKeyWithDifferentFingerprint(UUID idempotencyKey, String fingerprint) {
        return fingerprintsByIdempotencyKey.containsKey(idempotencyKey)
            && !fingerprint.equals(fingerprintsByIdempotencyKey.get(idempotencyKey));
    }

    @Override
    public boolean customerIsActive(UUID customerId) {
        return customerId == null || activeCustomers.contains(customerId);
    }

    @Override
    public SalesOrder createConfirmed(CreateSalesOrderCommand command, String fingerprint) {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        List<SalesOrderItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        for (CreateSalesOrderItemCommand item : command.items()) {
            BigDecimal discount = item.discountAmount() == null ? BigDecimal.ZERO : item.discountAmount();
            BigDecimal lineSubtotal = item.quantity().multiply(item.unitPrice());
            BigDecimal lineTotal = lineSubtotal.subtract(discount);
            subtotal = subtotal.add(lineSubtotal);
            discountTotal = discountTotal.add(discount);
            items.add(new SalesOrderItem(
                UUID.randomUUID(),
                orderId,
                item.productPresentationId(),
                null,
                "Producto prueba",
                "SKU-TEST",
                item.quantity(),
                item.unitPrice(),
                BigDecimal.ZERO,
                discount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                lineTotal
            ));
        }
        SalesOrder order = new SalesOrder(
            orderId,
            "SO-TEST",
            UUID.randomUUID(),
            command.warehouseId(),
            command.customerId(),
            command.deviceId(),
            command.channel() == null ? "POS" : command.channel(),
            "CONFIRMED",
            "PENDING",
            command.currencyCode() == null ? "MXN" : command.currencyCode(),
            subtotal,
            discountTotal,
            BigDecimal.ZERO,
            subtotal.subtract(discountTotal),
            command.idempotencyKey(),
            now,
            now,
            null,
            items
        );
        orders.put(orderId, order);
        fingerprintsByIdempotencyKey.put(command.idempotencyKey(), fingerprint);
        return order;
    }

    @Override
    public Optional<SalesOrder> findById(UUID salesOrderId) {
        return Optional.ofNullable(orders.get(salesOrderId));
    }

    @Override
    public List<SalesOrder> findAll(ListSalesOrdersQuery query) {
        return List.copyOf(orders.values());
    }

    @Override
    public SalesOrder cancel(UUID salesOrderId) {
        SalesOrder order = orders.get(salesOrderId);
        SalesOrder cancelled = new SalesOrder(
            order.salesOrderId(),
            order.orderNumber(),
            order.branchId(),
            order.warehouseId(),
            order.customerId(),
            order.deviceId(),
            order.channel(),
            "CANCELLED",
            "CANCELLED",
            order.currencyCode(),
            order.subtotal(),
            order.discountTotal(),
            order.taxTotal(),
            order.total(),
            order.idempotencyKey(),
            order.createdAt(),
            order.confirmedAt(),
            Instant.now(),
            order.items()
        );
        orders.put(salesOrderId, cancelled);
        return cancelled;
    }
}
