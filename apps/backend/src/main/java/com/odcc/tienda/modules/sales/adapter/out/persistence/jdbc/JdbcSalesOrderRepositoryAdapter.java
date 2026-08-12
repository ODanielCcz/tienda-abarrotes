package com.odcc.tienda.modules.sales.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderItemCommand;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.exception.SalesOrderCancellationConflictException;
import com.odcc.tienda.modules.sales.application.exception.StockInsufficientException;
import com.odcc.tienda.modules.sales.application.model.SalesOrder;
import com.odcc.tienda.modules.sales.application.model.SalesOrderItem;
import com.odcc.tienda.modules.sales.application.port.out.SalesOrderRepositoryPort;
import com.odcc.tienda.modules.sales.application.query.ListSalesOrdersQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcSalesOrderRepositoryAdapter implements SalesOrderRepositoryPort {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Optional<SalesOrder> findByIdempotencyKey(UUID idempotencyKey, String fingerprint) {
        try {
            UUID id = jdbc.queryForObject("""
                SELECT sales_order_id FROM sales.sales_orders
                WHERE idempotency_key = :idempotencyKey AND source_fingerprint = :fingerprint
                """, new MapSqlParameterSource().addValue("idempotencyKey", idempotencyKey).addValue("fingerprint", fingerprint), UUID.class);
            return findById(id);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existsByIdempotencyKeyWithDifferentFingerprint(UUID idempotencyKey, String fingerprint) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM sales.sales_orders
            WHERE idempotency_key = :idempotencyKey
              AND COALESCE(source_fingerprint, '') <> :fingerprint
            """, new MapSqlParameterSource().addValue("idempotencyKey", idempotencyKey).addValue("fingerprint", fingerprint), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean customerIsActive(UUID customerId) {
        if (customerId == null) return true;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sales.customers WHERE customer_id = :customerId AND status = 'ACTIVE'", new MapSqlParameterSource("customerId", customerId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public Optional<BigDecimal> findCurrentPrice(
        UUID warehouseId,
        UUID productPresentationId,
        String currencyCode
    ) {
        try {
            BigDecimal amount = jdbc.queryForObject(
                """
                    SELECT price.amount
                    FROM organization.warehouses warehouse
                    JOIN catalog.price_lists price_list
                      ON price_list.code = 'GENERAL'
                     AND price_list.status = 'ACTIVE'
                     AND price_list.currency_code = :currencyCode
                    JOIN catalog.prices price
                      ON price.price_list_id = price_list.price_list_id
                     AND price.product_presentation_id = :presentationId
                     AND (price.branch_id = warehouse.branch_id OR price.branch_id IS NULL)
                     AND price.valid_from <= clock_timestamp()
                     AND (price.valid_until IS NULL OR price.valid_until > clock_timestamp())
                    WHERE warehouse.warehouse_id = :warehouseId
                      AND warehouse.status = 'ACTIVE'
                    ORDER BY
                      CASE WHEN price.branch_id = warehouse.branch_id THEN 0 ELSE 1 END,
                      price.valid_from DESC,
                      price.created_at DESC
                    LIMIT 1
                    """,
                new MapSqlParameterSource()
                    .addValue("warehouseId", warehouseId)
                    .addValue("presentationId", productPresentationId)
                    .addValue("currencyCode", normalize(currencyCode, "MXN")),
                BigDecimal.class
            );
            return Optional.ofNullable(amount);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public UUID findBranchIdByWarehouseId(UUID warehouseId) {
        return findWarehouse(warehouseId).branchId();
    }

    @Override
    public SalesOrder createConfirmed(CreateSalesOrderCommand command, String fingerprint) {
        WarehouseRow warehouse = findWarehouse(command.warehouseId());
        UUID orderId = UUID.randomUUID();
        UUID stockMovementId = UUID.randomUUID();
        String orderNumber = "SO-" + orderId.toString().substring(0, 8).toUpperCase();
        String channel = normalize(command.channel(), "POS");
        String currency = normalize(command.currencyCode(), "MXN");
        Totals totals = estimateTotals(command.items());
        Instant now = Instant.now();

        jdbc.update("""
            INSERT INTO sales.sales_orders (
                sales_order_id, order_number, branch_id, warehouse_id, customer_id, device_id,
                channel, status, payment_status, currency_code, subtotal, discount_total, tax_total,
                total, idempotency_key, source_fingerprint, created_at, confirmed_at
            ) VALUES (
                :orderId, :orderNumber, :branchId, :warehouseId, :customerId, :deviceId,
                :channel, 'CONFIRMED', 'PENDING', :currencyCode, :subtotal, :discountTotal, :taxTotal,
                :total, :idempotencyKey, :fingerprint, :createdAt, :confirmedAt
            )
            """, new MapSqlParameterSource()
            .addValue("orderId", orderId)
            .addValue("orderNumber", orderNumber)
            .addValue("branchId", warehouse.branchId())
            .addValue("warehouseId", command.warehouseId())
            .addValue("customerId", command.customerId())
            .addValue("deviceId", command.deviceId())
            .addValue("channel", channel)
            .addValue("currencyCode", currency)
            .addValue("subtotal", totals.subtotal())
            .addValue("discountTotal", totals.discount())
            .addValue("taxTotal", totals.tax())
            .addValue("total", totals.total())
            .addValue("idempotencyKey", command.idempotencyKey())
            .addValue("fingerprint", fingerprint)
            .addValue("createdAt", Timestamp.from(now))
            .addValue("confirmedAt", Timestamp.from(now)));

        boolean movementCreated = false;
        for (CreateSalesOrderItemCommand item : command.items()) {
            PresentationRow presentation = findPresentation(item.productPresentationId());
            List<Allocation> allocations = allocate(command.warehouseId(), presentation, item.quantity());
            if (presentation.tracksInventory() && !movementCreated) {
                insertStockMovement(stockMovementId, warehouse.branchId(), command.warehouseId(), "SALE", "SALES_ORDER", orderId, "Venta " + orderNumber);
                movementCreated = true;
            }
            if (allocations.isEmpty()) {
                insertSalesOrderItem(orderId, presentation, null, item.quantity(), item.unitPrice(), item.discountAmount(), ZERO);
            } else {
                for (Allocation allocation : allocations) {
                    SalesOrderItem inserted = insertSalesOrderItem(orderId, presentation, allocation.lotId(), allocation.quantity(), item.unitPrice(), proportionalDiscount(item.discountAmount(), item.quantity(), allocation.quantity()), allocation.unitCost());
                    insertStockMovementItem(stockMovementId, inserted.productPresentationId(), allocation.lotId(), allocation.quantity(), allocation.unitCost(), allocation.quantityBefore(), allocation.quantityAfter(), "OUT");
                }
            }
        }
        recalculateOrderTotals(orderId);
        return findById(orderId).orElseThrow();
    }

    @Override
    public Optional<SalesOrder> findById(UUID salesOrderId) {
        try {
            SalesOrder order = jdbc.queryForObject("SELECT * FROM sales.sales_orders WHERE sales_order_id = :id", new MapSqlParameterSource("id", salesOrderId), this::mapOrderWithoutItems);
            return Optional.of(withItems(order));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<SalesOrder> findAll(ListSalesOrdersQuery query) {
        List<SalesOrder> orders = jdbc.query("""
            SELECT * FROM sales.sales_orders
            WHERE (CAST(:warehouseId AS uuid) IS NULL OR warehouse_id = CAST(:warehouseId AS uuid))
              AND (CAST(:customerId AS uuid) IS NULL OR customer_id = CAST(:customerId AS uuid))
              AND (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
            ORDER BY created_at DESC
            LIMIT 200
            """, new MapSqlParameterSource()
            .addValue("warehouseId", query == null ? null : query.warehouseId())
            .addValue("customerId", query == null ? null : query.customerId())
            .addValue("status", query == null ? null : normalize(query.status(), null)), this::mapOrderWithoutItems);
        return orders.stream().map(this::withItems).toList();
    }

    @Override
    public SalesOrder cancel(UUID salesOrderId) {
        lockForCancellation(salesOrderId);
        int claimed = jdbc.update("""
            UPDATE sales.sales_orders
            SET status = 'CANCELLED',
                payment_status = 'CANCELLED',
                cancelled_at = clock_timestamp()
            WHERE sales_order_id = :id
              AND status = 'CONFIRMED'
              AND NOT EXISTS (
                  SELECT 1
                  FROM sales.returns r
                  WHERE r.sales_order_id = sales.sales_orders.sales_order_id
                    AND r.status <> 'CANCELLED'
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM sales.payments p
                  WHERE p.sales_order_id = sales.sales_orders.sales_order_id
                    AND p.status = 'CAPTURED'
              )
            """, new MapSqlParameterSource("id", salesOrderId));
        if (claimed != 1) throw new SalesOrderCancellationConflictException();

        SalesOrder order = findById(salesOrderId).orElseThrow(() -> new SalesException("No existe la venta " + salesOrderId));
        UUID movementId = UUID.randomUUID();
        insertStockMovement(movementId, order.branchId(), order.warehouseId(), "SALE_RETURN", "SALES_ORDER_CANCEL", order.salesOrderId(), "Cancelacion " + order.orderNumber());
        for (SalesOrderItem item : order.items()) {
            PresentationRow presentation = findPresentation(item.productPresentationId());
            if (!presentation.tracksInventory()) continue;
            BigDecimal before = currentStock(order.warehouseId(), item.productPresentationId());
            upStock(order.warehouseId(), item.productPresentationId(), item.quantity(), item.unitCost());
            BigDecimal after = before.add(item.quantity());
            if (item.lotId() != null) upLot(order.warehouseId(), item.lotId(), item.quantity());
            insertStockMovementItem(movementId, item.productPresentationId(), item.lotId(), item.quantity(), item.unitCost(), before, after, "IN");
        }
        return findById(salesOrderId).orElseThrow();
    }

    private void lockForCancellation(UUID salesOrderId) {
        try {
            jdbc.queryForObject(
                "SELECT sales_order_id FROM sales.sales_orders WHERE sales_order_id = :id FOR UPDATE",
                new MapSqlParameterSource("id", salesOrderId),
                UUID.class
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new SalesOrderCancellationConflictException();
        }
    }

    private List<Allocation> allocate(UUID warehouseId, PresentationRow presentation, BigDecimal quantity) {
        if (!presentation.tracksInventory()) return List.of();
        if (presentation.tracksLots()) return allocateLotsFefo(warehouseId, presentation, quantity);
        BigDecimal before = currentStock(warehouseId, presentation.id());
        int updated = jdbc.update("""
            UPDATE inventory.stock_balances
            SET on_hand_quantity = on_hand_quantity - :quantity,
                version = version + 1,
                updated_at = clock_timestamp()
            WHERE warehouse_id = :warehouseId
              AND product_presentation_id = :presentationId
              AND available_quantity >= :quantity
            """, new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("presentationId", presentation.id()).addValue("quantity", quantity));
        if (updated == 0) throw new StockInsufficientException(presentation.id());
        return List.of(new Allocation(null, quantity, presentation.averageUnitCost(), before, before.subtract(quantity)));
    }

    private List<Allocation> allocateLotsFefo(UUID warehouseId, PresentationRow presentation, BigDecimal requestedQuantity) {
        BigDecimal remaining = requestedQuantity;
        List<Allocation> allocations = new ArrayList<>();
        List<LotCandidate> candidates = jdbc.query("""
            SELECT lot.lot_id, lb.available_quantity, sb.on_hand_quantity AS stock_before, sb.average_unit_cost
            FROM inventory.lot_balances lb
            JOIN inventory.lots lot ON lot.lot_id = lb.lot_id
            JOIN inventory.stock_balances sb ON sb.warehouse_id = lb.warehouse_id AND sb.product_presentation_id = lot.product_presentation_id
            WHERE lb.warehouse_id = :warehouseId
              AND lot.product_presentation_id = :presentationId
              AND lot.status = 'ACTIVE'
              AND lb.available_quantity > 0
            ORDER BY lot.expires_at NULLS LAST, lot.created_at
            """, new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("presentationId", presentation.id()), (rs, rowNum) -> new LotCandidate(rs.getObject("lot_id", UUID.class), rs.getBigDecimal("available_quantity"), rs.getBigDecimal("stock_before"), rs.getBigDecimal("average_unit_cost")));
        for (LotCandidate candidate : candidates) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal take = candidate.availableQuantity().min(remaining);
            int lotUpdated = jdbc.update("""
                UPDATE inventory.lot_balances
                SET on_hand_quantity = on_hand_quantity - :quantity,
                    version = version + 1,
                    updated_at = clock_timestamp()
                WHERE warehouse_id = :warehouseId
                  AND lot_id = :lotId
                  AND available_quantity >= :quantity
                """, new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("lotId", candidate.lotId()).addValue("quantity", take));
            if (lotUpdated == 0) continue;
            int stockUpdated = jdbc.update("""
                UPDATE inventory.stock_balances
                SET on_hand_quantity = on_hand_quantity - :quantity,
                    version = version + 1,
                    updated_at = clock_timestamp()
                WHERE warehouse_id = :warehouseId
                  AND product_presentation_id = :presentationId
                  AND available_quantity >= :quantity
                """, new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("presentationId", presentation.id()).addValue("quantity", take));
            if (stockUpdated == 0) throw new StockInsufficientException(presentation.id());
            allocations.add(new Allocation(candidate.lotId(), take, candidate.averageUnitCost(), candidate.stockBefore(), candidate.stockBefore().subtract(take)));
            remaining = remaining.subtract(take);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) throw new StockInsufficientException(presentation.id());
        return allocations;
    }

    private SalesOrderItem insertSalesOrderItem(UUID orderId, PresentationRow presentation, UUID lotId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount, BigDecimal unitCost) {
        BigDecimal taxRate = presentation.taxRate();
        BigDecimal lineBase = money(unitPrice).multiply(scale3(quantity)).setScale(4, RoundingMode.HALF_UP).subtract(money(discount));
        BigDecimal taxAmount = lineBase.multiply(taxRate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal lineTotal = lineBase.add(taxAmount);
        UUID itemId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO sales.sales_order_items (
                sales_order_item_id, sales_order_id, product_presentation_id, lot_id,
                product_name_snapshot, sku_snapshot, quantity, unit_price, unit_cost,
                discount_amount, tax_rate, tax_amount, line_total
            ) VALUES (
                :itemId, :orderId, :presentationId, :lotId,
                :productName, :sku, :quantity, :unitPrice, :unitCost,
                :discount, :taxRate, :taxAmount, :lineTotal
            )
            """, new MapSqlParameterSource()
            .addValue("itemId", itemId)
            .addValue("orderId", orderId)
            .addValue("presentationId", presentation.id())
            .addValue("lotId", lotId)
            .addValue("productName", presentation.productName())
            .addValue("sku", presentation.sku())
            .addValue("quantity", scale3(quantity))
            .addValue("unitPrice", money(unitPrice))
            .addValue("unitCost", money(unitCost))
            .addValue("discount", money(discount))
            .addValue("taxRate", taxRate)
            .addValue("taxAmount", taxAmount)
            .addValue("lineTotal", lineTotal));
        return findItemById(itemId);
    }

    private void insertStockMovement(UUID movementId, UUID branchId, UUID warehouseId, String type, String sourceType, UUID sourceId, String reason) {
        jdbc.update("""
            INSERT INTO inventory.stock_movements (
                stock_movement_id, branch_id, warehouse_id, movement_type, status, source_type, source_id, reason, idempotency_key, confirmed_at
            ) VALUES (
                :id, :branchId, :warehouseId, :type, 'CONFIRMED', :sourceType, :sourceId, :reason, :idempotencyKey, clock_timestamp()
            )
            """, new MapSqlParameterSource().addValue("id", movementId).addValue("branchId", branchId).addValue("warehouseId", warehouseId).addValue("type", type).addValue("sourceType", sourceType).addValue("sourceId", sourceId).addValue("reason", reason).addValue("idempotencyKey", UUID.randomUUID()));
    }

    private void insertStockMovementItem(UUID movementId, UUID presentationId, UUID lotId, BigDecimal quantity, BigDecimal unitCost, BigDecimal before, BigDecimal after, String direction) {
        jdbc.update("""
            INSERT INTO inventory.stock_movement_items (
                stock_movement_item_id, stock_movement_id, product_presentation_id, lot_id, direction, quantity, unit_cost, quantity_before, quantity_after
            ) VALUES (
                :id, :movementId, :presentationId, :lotId, :direction, :quantity, :unitCost, :before, :after
            )
            """, new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("movementId", movementId).addValue("presentationId", presentationId).addValue("lotId", lotId).addValue("direction", direction).addValue("quantity", quantity).addValue("unitCost", unitCost).addValue("before", before).addValue("after", after));
    }

    private void recalculateOrderTotals(UUID orderId) {
        jdbc.update("""
            UPDATE sales.sales_orders order_header
            SET subtotal = totals.subtotal,
                discount_total = totals.discount_total,
                tax_total = totals.tax_total,
                total = totals.total
            FROM (
                SELECT sales_order_id,
                       COALESCE(SUM(quantity * unit_price), 0) AS subtotal,
                       COALESCE(SUM(discount_amount), 0) AS discount_total,
                       COALESCE(SUM(tax_amount), 0) AS tax_total,
                       COALESCE(SUM(line_total), 0) AS total
                FROM sales.sales_order_items
                WHERE sales_order_id = :orderId
                GROUP BY sales_order_id
            ) totals
            WHERE order_header.sales_order_id = totals.sales_order_id
            """, new MapSqlParameterSource("orderId", orderId));
    }

    private SalesOrder withItems(SalesOrder order) {
        List<SalesOrderItem> items = jdbc.query("SELECT * FROM sales.sales_order_items WHERE sales_order_id = :id ORDER BY sales_order_item_id", new MapSqlParameterSource("id", order.salesOrderId()), this::mapItem);
        return new SalesOrder(order.salesOrderId(), order.orderNumber(), order.branchId(), order.warehouseId(), order.customerId(), order.deviceId(), order.channel(), order.status(), order.paymentStatus(), order.currencyCode(), order.subtotal(), order.discountTotal(), order.taxTotal(), order.total(), order.idempotencyKey(), order.createdAt(), order.confirmedAt(), order.cancelledAt(), items);
    }

    private WarehouseRow findWarehouse(UUID warehouseId) {
        try {
            return jdbc.queryForObject("SELECT warehouse_id, branch_id FROM organization.warehouses WHERE warehouse_id = :id AND status = 'ACTIVE'", new MapSqlParameterSource("id", warehouseId), (rs, rowNum) -> new WarehouseRow(rs.getObject("warehouse_id", UUID.class), rs.getObject("branch_id", UUID.class)));
        } catch (EmptyResultDataAccessException exception) {
            throw new SalesException("No existe un almacen activo con id " + warehouseId);
        }
    }

    private PresentationRow findPresentation(UUID presentationId) {
        try {
            return jdbc.queryForObject("""
                SELECT pp.product_presentation_id, pp.sku, pp.name AS presentation_name,
                       p.name AS product_name, p.tracks_inventory, p.tracks_lots,
                       COALESCE(t.rate, 0) AS tax_rate,
                       COALESCE(sb.average_unit_cost, 0) AS average_unit_cost
                FROM catalog.product_presentations pp
                JOIN catalog.products p ON p.product_id = pp.product_id
                LEFT JOIN catalog.taxes t ON t.tax_id = pp.tax_id
                LEFT JOIN inventory.stock_balances sb ON sb.product_presentation_id = pp.product_presentation_id
                WHERE pp.product_presentation_id = :id
                  AND pp.status = 'ACTIVE'
                  AND p.status = 'ACTIVE'
                LIMIT 1
                """, new MapSqlParameterSource("id", presentationId), (rs, rowNum) -> new PresentationRow(
                rs.getObject("product_presentation_id", UUID.class),
                rs.getString("sku"),
                rs.getString("product_name") + " - " + rs.getString("presentation_name"),
                rs.getBoolean("tracks_inventory"),
                rs.getBoolean("tracks_lots"),
                rs.getBigDecimal("tax_rate"),
                rs.getBigDecimal("average_unit_cost")
            ));
        } catch (EmptyResultDataAccessException exception) {
            throw new SalesException("No existe una presentacion activa con id " + presentationId);
        }
    }

    private BigDecimal currentStock(UUID warehouseId, UUID presentationId) {
        try {
            return jdbc.queryForObject("SELECT on_hand_quantity FROM inventory.stock_balances WHERE warehouse_id = :warehouseId AND product_presentation_id = :presentationId", new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("presentationId", presentationId), BigDecimal.class);
        } catch (EmptyResultDataAccessException exception) {
            return ZERO;
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
            """, new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("presentationId", presentationId).addValue("quantity", quantity).addValue("unitCost", unitCost));
    }

    private void upLot(UUID warehouseId, UUID lotId, BigDecimal quantity) {
        jdbc.update("""
            INSERT INTO inventory.lot_balances (warehouse_id, lot_id, on_hand_quantity, version, updated_at)
            VALUES (:warehouseId, :lotId, :quantity, 1, clock_timestamp())
            ON CONFLICT (warehouse_id, lot_id)
            DO UPDATE SET on_hand_quantity = inventory.lot_balances.on_hand_quantity + EXCLUDED.on_hand_quantity,
                          version = inventory.lot_balances.version + 1,
                          updated_at = clock_timestamp()
            """, new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("lotId", lotId).addValue("quantity", quantity));
    }

    private SalesOrderItem findItemById(UUID itemId) {
        return jdbc.queryForObject("SELECT * FROM sales.sales_order_items WHERE sales_order_item_id = :id", new MapSqlParameterSource("id", itemId), this::mapItem);
    }

    private SalesOrder mapOrderWithoutItems(ResultSet rs, int rowNum) throws SQLException {
        return new SalesOrder(
            rs.getObject("sales_order_id", UUID.class), rs.getString("order_number"), rs.getObject("branch_id", UUID.class), rs.getObject("warehouse_id", UUID.class),
            rs.getObject("customer_id", UUID.class), rs.getObject("device_id", UUID.class), rs.getString("channel"), rs.getString("status"), rs.getString("payment_status"), rs.getString("currency_code"),
            rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount_total"), rs.getBigDecimal("tax_total"), rs.getBigDecimal("total"), rs.getObject("idempotency_key", UUID.class),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toInstant(), rs.getTimestamp("cancelled_at") == null ? null : rs.getTimestamp("cancelled_at").toInstant(), List.of()
        );
    }

    private SalesOrderItem mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new SalesOrderItem(
            rs.getObject("sales_order_item_id", UUID.class), rs.getObject("sales_order_id", UUID.class), rs.getObject("product_presentation_id", UUID.class), rs.getObject("lot_id", UUID.class),
            rs.getString("product_name_snapshot"), rs.getString("sku_snapshot"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("unit_cost"), rs.getBigDecimal("discount_amount"), rs.getBigDecimal("tax_rate"), rs.getBigDecimal("tax_amount"), rs.getBigDecimal("line_total")
        );
    }

    private Totals estimateTotals(List<CreateSalesOrderItemCommand> items) {
        BigDecimal subtotal = ZERO;
        BigDecimal discount = ZERO;
        for (CreateSalesOrderItemCommand item : items) {
            subtotal = subtotal.add(scale3(item.quantity()).multiply(money(item.unitPrice())).setScale(4, RoundingMode.HALF_UP));
            discount = discount.add(money(item.discountAmount()));
        }
        return new Totals(subtotal, discount, ZERO, subtotal.subtract(discount));
    }

    private static BigDecimal proportionalDiscount(BigDecimal discount, BigDecimal requested, BigDecimal allocated) {
        if (discount == null || discount.compareTo(BigDecimal.ZERO) == 0) return ZERO;
        return money(discount).multiply(allocated).divide(requested, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP); }
    private static BigDecimal scale3(BigDecimal value) { return value.setScale(3, RoundingMode.HALF_UP); }
    private static String normalize(String value, String defaultValue) { return value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase(); }

    private record WarehouseRow(UUID warehouseId, UUID branchId) {}
    private record PresentationRow(UUID id, String sku, String productName, boolean tracksInventory, boolean tracksLots, BigDecimal taxRate, BigDecimal averageUnitCost) {}
    private record Allocation(UUID lotId, BigDecimal quantity, BigDecimal unitCost, BigDecimal quantityBefore, BigDecimal quantityAfter) {}
    private record LotCandidate(UUID lotId, BigDecimal availableQuantity, BigDecimal stockBefore, BigDecimal averageUnitCost) {}
    private record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total) {}
}

