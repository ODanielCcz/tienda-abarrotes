package com.odcc.tienda.modules.sales.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.sales.application.command.ConfirmSalesReturnCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesReturnCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesReturnItemCommand;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.exception.SalesOrderNotFoundException;
import com.odcc.tienda.modules.sales.application.exception.SalesReturnNotFoundException;
import com.odcc.tienda.modules.sales.application.exception.SalesReturnAlreadyProcessedException;
import com.odcc.tienda.modules.sales.application.exception.SalesReturnOrderConflictException;
import com.odcc.tienda.modules.sales.application.exception.SalesReturnRefundUnfundedException;
import com.odcc.tienda.modules.sales.application.model.SalesReturn;
import com.odcc.tienda.modules.sales.application.model.SalesReturnItem;
import com.odcc.tienda.modules.sales.application.port.out.SalesReturnRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;

@Repository
@RequiredArgsConstructor
public class JdbcSalesReturnRepositoryAdapter implements SalesReturnRepositoryPort {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public SalesReturn createDraft(CreateSalesReturnCommand command) {
        OrderRow order = findOrder(command.salesOrderId());
        if (!List.of("CONFIRMED", "PARTIALLY_RETURNED").contains(order.status())) throw new SalesException("Solo se pueden devolver ventas confirmadas");
        UUID returnId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO sales.returns (return_id, sales_order_id, status, reason, total, created_by)
            VALUES (:returnId, :salesOrderId, 'DRAFT', :reason, 0, :createdBy)
            """, new MapSqlParameterSource()
            .addValue("returnId", returnId)
            .addValue("salesOrderId", command.salesOrderId())
            .addValue("reason", command.reason().trim())
            .addValue("createdBy", command.createdBy()));

        BigDecimal total = ZERO;
        for (CreateSalesReturnItemCommand itemCommand : command.items()) {
            OrderItemRow item = findOrderItem(command.salesOrderId(), itemCommand.salesOrderItemId());
            ensureReturnQuantityAvailableForDraft(item, itemCommand.quantity());
            BigDecimal amount = proratedAmount(item.lineTotal(), item.quantity(), itemCommand.quantity());
            total = total.add(amount);
            jdbc.update("""
                INSERT INTO sales.return_items (return_item_id, return_id, sales_order_item_id, quantity, amount)
                VALUES (:returnItemId, :returnId, :salesOrderItemId, :quantity, :amount)
                """, new MapSqlParameterSource()
                .addValue("returnItemId", UUID.randomUUID())
                .addValue("returnId", returnId)
                .addValue("salesOrderItemId", item.salesOrderItemId())
                .addValue("quantity", scale3(itemCommand.quantity()))
                .addValue("amount", amount));
        }
        jdbc.update("UPDATE sales.returns SET total = :total WHERE return_id = :returnId", new MapSqlParameterSource().addValue("returnId", returnId).addValue("total", total));
        return findById(returnId).orElseThrow();
    }

    @Override
    public Optional<SalesReturn> findById(UUID returnId) {
        try {
            SalesReturn salesReturn = jdbc.queryForObject("SELECT * FROM sales.returns WHERE return_id = :returnId", new MapSqlParameterSource("returnId", returnId), this::mapReturnWithoutItems);
            return Optional.of(withItems(salesReturn));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public SalesReturn confirm(ConfirmSalesReturnCommand command) {
        SalesReturn salesReturn = findByIdForUpdate(command.returnId());
        if (!"DRAFT".equals(salesReturn.status())) throw new SalesReturnAlreadyProcessedException(command.returnId());
        OrderRow order = findOrder(salesReturn.salesOrderId());
        if (!List.of("CONFIRMED", "PARTIALLY_RETURNED").contains(order.status())) {
            throw new SalesReturnOrderConflictException();
        }
        List<SalesReturnItem> lockedItems = salesReturn.items().stream()
            .sorted(Comparator.comparing(SalesReturnItem::salesOrderItemId))
            .toList();
        for (SalesReturnItem item : lockedItems) {
            findOrderItemForUpdate(order.salesOrderId(), item.salesOrderItemId());
        }
        for (SalesReturnItem item : lockedItems) {
            OrderItemRow orderItem = findOrderItem(order.salesOrderId(), item.salesOrderItemId());
            ensureReturnQuantityAvailableForConfirm(salesReturn.returnId(), orderItem, item.quantity());
        }
        int claimed = jdbc.update(
            "UPDATE sales.returns SET status = 'CONFIRMED', confirmed_at = clock_timestamp() WHERE return_id = :returnId AND status = 'DRAFT'",
            new MapSqlParameterSource("returnId", salesReturn.returnId())
        );
        if (claimed != 1) throw new SalesReturnAlreadyProcessedException(command.returnId());
        UUID movementId = UUID.randomUUID();
        insertStockMovement(movementId, order.branchId(), order.warehouseId(), "SALE_RETURN", "SALES_RETURN", salesReturn.returnId(), "Devolucion de venta " + order.orderNumber(), command.confirmedBy());

        for (SalesReturnItem item : lockedItems) {
            OrderItemRow orderItem = findOrderItem(order.salesOrderId(), item.salesOrderItemId());
            BigDecimal before = currentStock(order.warehouseId(), orderItem.productPresentationId());
            upStock(order.warehouseId(), orderItem.productPresentationId(), item.quantity(), orderItem.unitCost());
            BigDecimal after = before.add(item.quantity());
            if (orderItem.lotId() != null) upLot(order.warehouseId(), orderItem.lotId(), item.quantity());
            insertStockMovementItem(movementId, orderItem.productPresentationId(), orderItem.lotId(), item.quantity(), orderItem.unitCost(), before, after, "IN");
        }

        SalesReturn confirmedReturn = findById(salesReturn.returnId()).orElseThrow();
        updateSalesOrderReturnStatus(order.salesOrderId());
        allocateRefunds(order, confirmedReturn, command.cashSessionId(), command.confirmedBy());
        return confirmedReturn;
    }

    @Override
    public SalesReturn cancel(UUID returnId) {
        SalesReturn salesReturn = findByIdForUpdate(returnId);
        if (!"DRAFT".equals(salesReturn.status())) throw new SalesReturnAlreadyProcessedException(returnId);
        int updated = jdbc.update("UPDATE sales.returns SET status = 'CANCELLED' WHERE return_id = :returnId AND status = 'DRAFT'", new MapSqlParameterSource("returnId", returnId));
        if (updated != 1) throw new SalesReturnAlreadyProcessedException(returnId);
        return findById(returnId).orElseThrow();
    }

    private SalesReturn findByIdForUpdate(UUID returnId) {
        try {
            SalesReturn salesReturn = jdbc.queryForObject(
                "SELECT * FROM sales.returns WHERE return_id = :returnId FOR UPDATE",
                new MapSqlParameterSource("returnId", returnId),
                this::mapReturnWithoutItems
            );
            return withItems(salesReturn);
        } catch (EmptyResultDataAccessException exception) {
            throw new SalesReturnNotFoundException(returnId);
        }
    }

    private void findOrderItemForUpdate(UUID salesOrderId, UUID salesOrderItemId) {
        jdbc.queryForObject(
            "SELECT sales_order_item_id FROM sales.sales_order_items WHERE sales_order_id = :salesOrderId AND sales_order_item_id = :salesOrderItemId FOR UPDATE",
            new MapSqlParameterSource().addValue("salesOrderId", salesOrderId).addValue("salesOrderItemId", salesOrderItemId),
            UUID.class
        );
    }

    private OrderRow findOrder(UUID salesOrderId) {
        try {
            return jdbc.queryForObject("""
                SELECT sales_order_id, order_number, branch_id, warehouse_id, status, payment_status, total
                FROM sales.sales_orders
                WHERE sales_order_id = :salesOrderId
                FOR UPDATE
                """, new MapSqlParameterSource("salesOrderId", salesOrderId), (rs, rowNum) -> new OrderRow(
                rs.getObject("sales_order_id", UUID.class),
                rs.getString("order_number"),
                rs.getObject("branch_id", UUID.class),
                rs.getObject("warehouse_id", UUID.class),
                rs.getString("status"),
                rs.getString("payment_status"),
                rs.getBigDecimal("total")
            ));
        } catch (EmptyResultDataAccessException exception) {
            throw new SalesOrderNotFoundException(salesOrderId);
        }
    }

    private OrderItemRow findOrderItem(UUID salesOrderId, UUID salesOrderItemId) {
        try {
            return jdbc.queryForObject("""
                SELECT sales_order_item_id, sales_order_id, product_presentation_id, lot_id,
                       product_name_snapshot, sku_snapshot, quantity, unit_cost, line_total
                FROM sales.sales_order_items
                WHERE sales_order_id = :salesOrderId
                  AND sales_order_item_id = :salesOrderItemId
                """, new MapSqlParameterSource().addValue("salesOrderId", salesOrderId).addValue("salesOrderItemId", salesOrderItemId), (rs, rowNum) -> new OrderItemRow(
                rs.getObject("sales_order_item_id", UUID.class),
                rs.getObject("sales_order_id", UUID.class),
                rs.getObject("product_presentation_id", UUID.class),
                rs.getObject("lot_id", UUID.class),
                rs.getString("product_name_snapshot"),
                rs.getString("sku_snapshot"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("unit_cost"),
                rs.getBigDecimal("line_total")
            ));
        } catch (EmptyResultDataAccessException exception) {
            throw new SalesException("El item de venta no pertenece a la venta indicada");
        }
    }

    private void ensureReturnQuantityAvailableForDraft(OrderItemRow item, BigDecimal requestedQuantity) {
        BigDecimal alreadyReturned = returnedQuantity(item.salesOrderItemId(), null, false);
        if (alreadyReturned.add(scale3(requestedQuantity)).compareTo(item.quantity()) > 0) throw new SalesException("La cantidad a devolver excede lo vendido");
    }

    private void ensureReturnQuantityAvailableForConfirm(UUID returnId, OrderItemRow item, BigDecimal requestedQuantity) {
        BigDecimal alreadyConfirmed = returnedQuantity(item.salesOrderItemId(), returnId, true);
        if (alreadyConfirmed.add(scale3(requestedQuantity)).compareTo(item.quantity()) > 0) throw new SalesException("La cantidad a devolver excede lo vendido");
    }

    private BigDecimal returnedQuantity(UUID salesOrderItemId, UUID excludedReturnId, boolean confirmedOnly) {
        BigDecimal value = jdbc.queryForObject("""
            SELECT COALESCE(SUM(ri.quantity), 0)
            FROM sales.return_items ri
            JOIN sales.returns r ON r.return_id = ri.return_id
            WHERE ri.sales_order_item_id = :salesOrderItemId
              AND (:confirmedOnly = FALSE OR r.status = 'CONFIRMED')
              AND (:confirmedOnly = TRUE OR r.status <> 'CANCELLED')
              AND (CAST(:excludedReturnId AS uuid) IS NULL OR r.return_id <> CAST(:excludedReturnId AS uuid))
            """, new MapSqlParameterSource()
            .addValue("salesOrderItemId", salesOrderItemId)
            .addValue("excludedReturnId", excludedReturnId)
            .addValue("confirmedOnly", confirmedOnly), BigDecimal.class);
        return value == null ? BigDecimal.ZERO : scale3(value);
    }

    private void allocateRefunds(OrderRow order, SalesReturn salesReturn, UUID cashSessionId, UUID createdBy) {
        BigDecimal remaining = money(salesReturn.total());
        boolean cashSessionValidated = false;
        for (PaymentRow payment : findCapturedPaymentsForUpdate(order.salesOrderId())) {
            if (remaining.signum() <= 0) break;
            BigDecimal available = money(payment.amount()).subtract(refundedAmount(payment.paymentId())).max(ZERO);
            if (available.signum() <= 0) continue;
            BigDecimal allocationAmount = remaining.min(available);
            UUID allocationId = UUID.randomUUID();
            insertRefundAllocation(allocationId, salesReturn.returnId(), payment.paymentId(), allocationAmount);
            if ("CASH".equals(payment.paymentMethod())) {
                if (cashSessionId == null) {
                    throw new SalesException("Las devoluciones con importe reembolsable en efectivo requieren una sesion de caja abierta");
                }
                if (!cashSessionValidated) {
                    ensureOpenCashSession(cashSessionId, order.branchId());
                    cashSessionValidated = true;
                }
                insertCashRefund(cashSessionId, salesReturn, payment.paymentId(), allocationId, allocationAmount, createdBy);
            }
            remaining = remaining.subtract(allocationAmount);
        }
        if (remaining.signum() > 0) {
            throw new SalesReturnRefundUnfundedException(money(remaining));
        }
        if (isFullReturn(order.salesOrderId())) {
            jdbc.update("UPDATE sales.sales_orders SET payment_status = 'REFUNDED' WHERE sales_order_id = :salesOrderId", new MapSqlParameterSource("salesOrderId", order.salesOrderId()));
        }
    }

    private void insertRefundAllocation(UUID allocationId, UUID returnId, UUID paymentId, BigDecimal amount) {
        jdbc.update("""
            INSERT INTO sales.refund_allocations (refund_allocation_id, return_id, payment_id, amount)
            VALUES (:allocationId, :returnId, :paymentId, :amount)
            """, new MapSqlParameterSource()
            .addValue("allocationId", allocationId)
            .addValue("returnId", returnId)
            .addValue("paymentId", paymentId)
            .addValue("amount", money(amount)));
    }

    private void insertCashRefund(
        UUID cashSessionId,
        SalesReturn salesReturn,
        UUID paymentId,
        UUID allocationId,
        BigDecimal amount,
        UUID createdBy
    ) {
        jdbc.update("""
            INSERT INTO cash.cash_movements (
                cash_movement_id, cash_session_id, movement_type, direction, amount,
                payment_id, reference, reason, created_by, source_type, source_id
            ) VALUES (
                :id, :cashSessionId, 'REFUND', 'OUT', :amount,
                :paymentId, :reference, :reason, :createdBy, 'SALES_REFUND_ALLOCATION', :sourceId
            )
            """, new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("cashSessionId", cashSessionId)
            .addValue("amount", money(amount))
            .addValue("paymentId", paymentId)
            .addValue("reference", "Devolucion " + salesReturn.returnId())
            .addValue("reason", salesReturn.reason())
            .addValue("createdBy", createdBy)
            .addValue("sourceId", allocationId));
    }

    private List<PaymentRow> findCapturedPaymentsForUpdate(UUID salesOrderId) {
        return jdbc.query("""
            SELECT payment_id, payment_method, amount
            FROM sales.payments
            WHERE sales_order_id = :salesOrderId
              AND status = 'CAPTURED'
            ORDER BY created_at, payment_id
            FOR UPDATE
            """, new MapSqlParameterSource("salesOrderId", salesOrderId), (rs, rowNum) -> new PaymentRow(
            rs.getObject("payment_id", UUID.class),
            rs.getString("payment_method"),
            rs.getBigDecimal("amount")
        ));
    }

    private BigDecimal refundedAmount(UUID paymentId) {
        BigDecimal refunded = jdbc.queryForObject("""
            SELECT COALESCE(SUM(amount), 0)
            FROM sales.refund_allocations
            WHERE payment_id = :paymentId
            """, new MapSqlParameterSource("paymentId", paymentId), BigDecimal.class);
        return money(refunded);
    }

    private void ensureOpenCashSession(UUID cashSessionId, UUID branchId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM cash.cash_sessions cs
            JOIN organization.cash_registers cr ON cr.cash_register_id = cs.cash_register_id
            WHERE cs.cash_session_id = :cashSessionId
              AND cs.status = 'OPEN'
              AND cr.branch_id = :branchId
            """, new MapSqlParameterSource().addValue("cashSessionId", cashSessionId).addValue("branchId", branchId), Integer.class);
        if (count == null || count == 0) throw new SalesException("No existe una sesion de caja abierta para la sucursal de la venta");
    }

    private boolean isFullReturn(UUID salesOrderId) {
        Integer remaining = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM sales.sales_order_items soi
            WHERE soi.sales_order_id = :salesOrderId
              AND soi.quantity > (
                  SELECT COALESCE(SUM(ri.quantity), 0)
                  FROM sales.return_items ri
                  JOIN sales.returns r ON r.return_id = ri.return_id
                  WHERE ri.sales_order_item_id = soi.sales_order_item_id
                    AND r.status = 'CONFIRMED'
              )
            """, new MapSqlParameterSource("salesOrderId", salesOrderId), Integer.class);
        return remaining != null && remaining == 0;
    }

    private void updateSalesOrderReturnStatus(UUID salesOrderId) {
        String status = isFullReturn(salesOrderId) ? "RETURNED" : "PARTIALLY_RETURNED";
        jdbc.update("UPDATE sales.sales_orders SET status = :status WHERE sales_order_id = :salesOrderId", new MapSqlParameterSource().addValue("status", status).addValue("salesOrderId", salesOrderId));
    }

    private BigDecimal currentStock(UUID warehouseId, UUID presentationId) {
        try {
            return jdbc.queryForObject("SELECT on_hand_quantity FROM inventory.stock_balances WHERE warehouse_id = :warehouseId AND product_presentation_id = :presentationId", new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("presentationId", presentationId), BigDecimal.class);
        } catch (EmptyResultDataAccessException exception) {
            return BigDecimal.ZERO;
        }
    }

    private void upStock(UUID warehouseId, UUID presentationId, BigDecimal quantity, BigDecimal unitCost) {
        jdbc.update("""
            INSERT INTO inventory.stock_balances (warehouse_id, product_presentation_id, on_hand_quantity, average_unit_cost, version, updated_at)
            VALUES (:warehouseId, :presentationId, :quantity, :unitCost, 1, clock_timestamp())
            ON CONFLICT (warehouse_id, product_presentation_id)
            DO UPDATE SET on_hand_quantity = inventory.stock_balances.on_hand_quantity + EXCLUDED.on_hand_quantity,
                          version = inventory.stock_balances.version + 1,
                          updated_at = clock_timestamp()
            """, new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("presentationId", presentationId).addValue("quantity", scale3(quantity)).addValue("unitCost", money(unitCost)));
    }

    private void upLot(UUID warehouseId, UUID lotId, BigDecimal quantity) {
        jdbc.update("""
            INSERT INTO inventory.lot_balances (warehouse_id, lot_id, on_hand_quantity, version, updated_at)
            VALUES (:warehouseId, :lotId, :quantity, 1, clock_timestamp())
            ON CONFLICT (warehouse_id, lot_id)
            DO UPDATE SET on_hand_quantity = inventory.lot_balances.on_hand_quantity + EXCLUDED.on_hand_quantity,
                          version = inventory.lot_balances.version + 1,
                          updated_at = clock_timestamp()
            """, new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("lotId", lotId).addValue("quantity", scale3(quantity)));
    }

    private void insertStockMovement(UUID movementId, UUID branchId, UUID warehouseId, String type, String sourceType, UUID sourceId, String reason, UUID createdBy) {
        jdbc.update("""
            INSERT INTO inventory.stock_movements (
                stock_movement_id, branch_id, warehouse_id, movement_type, status, source_type, source_id, reason, created_by, confirmed_at
            ) VALUES (
                :movementId, :branchId, :warehouseId, :type, 'CONFIRMED', :sourceType, :sourceId, :reason, :createdBy, clock_timestamp()
            )
            """, new MapSqlParameterSource()
            .addValue("movementId", movementId)
            .addValue("branchId", branchId)
            .addValue("warehouseId", warehouseId)
            .addValue("type", type)
            .addValue("sourceType", sourceType)
            .addValue("sourceId", sourceId)
            .addValue("reason", reason)
            .addValue("createdBy", createdBy));
    }

    private void insertStockMovementItem(UUID movementId, UUID presentationId, UUID lotId, BigDecimal quantity, BigDecimal unitCost, BigDecimal before, BigDecimal after, String direction) {
        jdbc.update("""
            INSERT INTO inventory.stock_movement_items (
                stock_movement_item_id, stock_movement_id, product_presentation_id, lot_id,
                direction, quantity, unit_cost, quantity_before, quantity_after
            ) VALUES (
                :id, :movementId, :presentationId, :lotId,
                :direction, :quantity, :unitCost, :before, :after
            )
            """, new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("movementId", movementId)
            .addValue("presentationId", presentationId)
            .addValue("lotId", lotId)
            .addValue("direction", direction)
            .addValue("quantity", scale3(quantity))
            .addValue("unitCost", money(unitCost))
            .addValue("before", scale3(before))
            .addValue("after", scale3(after)));
    }

    private SalesReturn withItems(SalesReturn salesReturn) {
        List<SalesReturnItem> items = jdbc.query("""
            SELECT ri.return_item_id, ri.return_id, ri.sales_order_item_id, ri.quantity, ri.amount,
                   soi.product_presentation_id, soi.lot_id, soi.product_name_snapshot, soi.sku_snapshot
            FROM sales.return_items ri
            JOIN sales.sales_order_items soi ON soi.sales_order_item_id = ri.sales_order_item_id
            WHERE ri.return_id = :returnId
            ORDER BY ri.return_item_id
            """, new MapSqlParameterSource("returnId", salesReturn.returnId()), this::mapItem);
        return new SalesReturn(salesReturn.returnId(), salesReturn.salesOrderId(), salesReturn.status(), salesReturn.reason(), salesReturn.total(), salesReturn.createdBy(), salesReturn.createdAt(), salesReturn.confirmedAt(), items);
    }

    private SalesReturn mapReturnWithoutItems(ResultSet rs, int rowNum) throws SQLException {
        return new SalesReturn(
            rs.getObject("return_id", UUID.class),
            rs.getObject("sales_order_id", UUID.class),
            rs.getString("status"),
            rs.getString("reason"),
            rs.getBigDecimal("total"),
            rs.getObject("created_by", UUID.class),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toInstant(),
            List.of()
        );
    }

    private SalesReturnItem mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new SalesReturnItem(
            rs.getObject("return_item_id", UUID.class),
            rs.getObject("return_id", UUID.class),
            rs.getObject("sales_order_item_id", UUID.class),
            rs.getObject("product_presentation_id", UUID.class),
            rs.getObject("lot_id", UUID.class),
            rs.getString("product_name_snapshot"),
            rs.getString("sku_snapshot"),
            rs.getBigDecimal("quantity"),
            rs.getBigDecimal("amount")
        );
    }

    private static BigDecimal proratedAmount(BigDecimal lineTotal, BigDecimal soldQuantity, BigDecimal returnedQuantity) {
        return money(lineTotal).multiply(scale3(returnedQuantity)).divide(scale3(soldQuantity), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale3(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
    }

    private record OrderRow(UUID salesOrderId, String orderNumber, UUID branchId, UUID warehouseId, String status, String paymentStatus, BigDecimal total) {}
    private record OrderItemRow(UUID salesOrderItemId, UUID salesOrderId, UUID productPresentationId, UUID lotId, String productNameSnapshot, String skuSnapshot, BigDecimal quantity, BigDecimal unitCost, BigDecimal lineTotal) {}
    private record PaymentRow(UUID paymentId, String paymentMethod, BigDecimal amount) {}
}
