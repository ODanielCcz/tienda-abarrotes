package com.odcc.tienda.modules.inventory.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.inventory.application.command.ConfirmInventoryCountCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateInventoryAdjustmentCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateInventoryCountCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateInventoryTransferCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateReservationCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryAdjustmentItemCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryCountItemCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryTransferItemCommand;
import com.odcc.tienda.modules.inventory.application.command.ReleaseReservationCommand;
import com.odcc.tienda.modules.inventory.application.command.ReservationItemCommand;
import com.odcc.tienda.modules.inventory.application.exception.InventoryReceiptAlreadyExistsException;
import com.odcc.tienda.modules.inventory.application.exception.InventoryReceiptException;
import com.odcc.tienda.modules.inventory.application.exception.InventoryResourceNotFoundException;
import com.odcc.tienda.modules.inventory.application.model.InventoryCountItemView;
import com.odcc.tienda.modules.inventory.application.model.InventoryCountView;
import com.odcc.tienda.modules.inventory.application.model.LotView;
import com.odcc.tienda.modules.inventory.application.model.ReservationItemView;
import com.odcc.tienda.modules.inventory.application.model.ReservationView;
import com.odcc.tienda.modules.inventory.application.model.StockMovementItemView;
import com.odcc.tienda.modules.inventory.application.model.StockMovementView;
import com.odcc.tienda.modules.inventory.application.port.out.AdvancedInventoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcAdvancedInventoryRepositoryAdapter implements AdvancedInventoryRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public StockMovementView adjust(CreateInventoryAdjustmentCommand command) {
        WarehouseRow warehouse = findWarehouse(command.warehouseId());
        UUID movementId = UUID.randomUUID();
        String movementType = "OUT".equals(normalize(command.items().getFirst().direction())) ? "ADJUSTMENT_OUT" : "ADJUSTMENT_IN";
        insertMovement(movementId, warehouse.branchId(), command.warehouseId(), movementType, movementId, "INVENTORY_ADJUSTMENT", null, reason(command.reason(), "Ajuste manual de inventario"), command.createdBy());
        for (InventoryAdjustmentItemCommand item : command.items()) {
            String direction = normalize(item.direction());
            BigDecimal before = stock(command.warehouseId(), item.productPresentationId());
            if ("IN".equals(direction)) {
                addStock(command.warehouseId(), item.productPresentationId(), item.quantity(), item.unitCost());
                if (item.lotId() != null) addLotStock(command.warehouseId(), item.lotId(), item.quantity());
                insertMovementItem(movementId, item.productPresentationId(), item.lotId(), direction, item.quantity(), item.unitCost(), before, before.add(scale3(item.quantity())));
            } else {
                subtractStock(command.warehouseId(), item.productPresentationId(), item.quantity());
                if (item.lotId() != null) subtractLotStock(command.warehouseId(), item.lotId(), item.quantity());
                insertMovementItem(movementId, item.productPresentationId(), item.lotId(), direction, item.quantity(), item.unitCost(), before, before.subtract(scale3(item.quantity())));
            }
        }
        return findMovement(movementId);
    }

    @Override
    public List<StockMovementView> transfer(CreateInventoryTransferCommand command) {
        WarehouseRow from = findWarehouse(command.fromWarehouseId());
        WarehouseRow to = findWarehouse(command.toWarehouseId());
        UUID transferId = UUID.randomUUID();
        UUID outMovementId = UUID.randomUUID();
        UUID inMovementId = UUID.randomUUID();
        insertMovement(outMovementId, from.branchId(), command.fromWarehouseId(), "TRANSFER_OUT", transferId, "INVENTORY_TRANSFER", null, reason(command.reason(), "Traspaso de salida"), command.createdBy());
        insertMovement(inMovementId, to.branchId(), command.toWarehouseId(), "TRANSFER_IN", transferId, "INVENTORY_TRANSFER", null, reason(command.reason(), "Traspaso de entrada"), command.createdBy());
        for (InventoryTransferItemCommand item : command.items()) {
            BigDecimal beforeOut = stock(command.fromWarehouseId(), item.productPresentationId());
            subtractStock(command.fromWarehouseId(), item.productPresentationId(), item.quantity());
            if (item.lotId() != null) subtractLotStock(command.fromWarehouseId(), item.lotId(), item.quantity());
            insertMovementItem(outMovementId, item.productPresentationId(), item.lotId(), "OUT", item.quantity(), item.unitCost(), beforeOut, beforeOut.subtract(scale3(item.quantity())));

            BigDecimal beforeIn = stock(command.toWarehouseId(), item.productPresentationId());
            addStock(command.toWarehouseId(), item.productPresentationId(), item.quantity(), item.unitCost());
            if (item.lotId() != null) addLotStock(command.toWarehouseId(), item.lotId(), item.quantity());
            insertMovementItem(inMovementId, item.productPresentationId(), item.lotId(), "IN", item.quantity(), item.unitCost(), beforeIn, beforeIn.add(scale3(item.quantity())));
        }
        return List.of(findMovement(outMovementId), findMovement(inMovementId));
    }

    @Override
    public InventoryCountView createCount(CreateInventoryCountCommand command) {
        findWarehouse(command.warehouseId());
        UUID countId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO inventory.inventory_counts (inventory_count_id, warehouse_id, status, started_by)
            VALUES (:id, :warehouseId, 'DRAFT', :startedBy)
            """, new MapSqlParameterSource().addValue("id", countId).addValue("warehouseId", command.warehouseId()).addValue("startedBy", command.startedBy()));
        for (InventoryCountItemCommand item : command.items()) {
            BigDecimal expected = item.lotId() == null ? stock(command.warehouseId(), item.productPresentationId()) : lotStock(command.warehouseId(), item.lotId());
            jdbc.update("""
                INSERT INTO inventory.inventory_count_items (
                    inventory_count_item_id, inventory_count_id, product_presentation_id, lot_id, expected_quantity, counted_quantity
                ) VALUES (:itemId, :countId, :presentationId, :lotId, :expected, :counted)
                """, new MapSqlParameterSource()
                .addValue("itemId", UUID.randomUUID())
                .addValue("countId", countId)
                .addValue("presentationId", item.productPresentationId())
                .addValue("lotId", item.lotId())
                .addValue("expected", expected)
                .addValue("counted", scale3(item.countedQuantity())));
        }
        return findCount(countId);
    }

    @Override
    public InventoryCountView confirmCount(ConfirmInventoryCountCommand command) {
        InventoryCountView count = findCount(command.inventoryCountId());
        if (!"DRAFT".equals(count.status())) throw new InventoryReceiptException("Solo se pueden confirmar conteos en borrador");
        WarehouseRow warehouse = findWarehouse(count.warehouseId());
        UUID movementId = UUID.randomUUID();
        boolean hasDifferences = false;
        insertMovement(movementId, warehouse.branchId(), count.warehouseId(), "ADJUSTMENT_IN", count.inventoryCountId(), "INVENTORY_COUNT", null, "Ajuste por conteo fisico", command.confirmedBy());
        for (InventoryCountItemView item : count.items()) {
            BigDecimal expected = scale3(item.expectedQuantity());
            BigDecimal counted = scale3(item.countedQuantity());
            int cmp = counted.compareTo(expected);
            if (cmp == 0) continue;
            hasDifferences = true;
            BigDecimal diff = counted.subtract(expected).abs();
            if (cmp > 0) {
                addStock(count.warehouseId(), item.productPresentationId(), diff, BigDecimal.ZERO);
                if (item.lotId() != null) addLotStock(count.warehouseId(), item.lotId(), diff);
                insertMovementItem(movementId, item.productPresentationId(), item.lotId(), "IN", diff, BigDecimal.ZERO, expected, counted);
            } else {
                subtractStock(count.warehouseId(), item.productPresentationId(), diff);
                if (item.lotId() != null) subtractLotStock(count.warehouseId(), item.lotId(), diff);
                insertMovementItem(movementId, item.productPresentationId(), item.lotId(), "OUT", diff, BigDecimal.ZERO, expected, counted);
            }
        }
        if (!hasDifferences) {
            jdbc.update("DELETE FROM inventory.stock_movements WHERE stock_movement_id = :id", new MapSqlParameterSource("id", movementId));
        }
        jdbc.update("""
            UPDATE inventory.inventory_counts
            SET status = 'CONFIRMED', confirmed_by = :confirmedBy, confirmed_at = clock_timestamp()
            WHERE inventory_count_id = :id
            """, new MapSqlParameterSource().addValue("id", count.inventoryCountId()).addValue("confirmedBy", command.confirmedBy()));
        return findCount(count.inventoryCountId());
    }

    @Override
    public ReservationView reserve(CreateReservationCommand command) {
        try {
            UUID branchId = branchFromReservation(command.items());
            UUID reservationId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO inventory.reservations (
                    reservation_id, branch_id, customer_id, source_type, source_id, status, idempotency_key, expires_at
                ) VALUES (:id, :branchId, :customerId, :sourceType, :sourceId, 'ACTIVE', :idempotencyKey, :expiresAt)
                """, new MapSqlParameterSource()
                .addValue("id", reservationId)
                .addValue("branchId", branchId)
                .addValue("customerId", command.customerId())
                .addValue("sourceType", normalize(command.sourceType()))
                .addValue("sourceId", command.sourceId())
                .addValue("idempotencyKey", command.idempotencyKey())
                .addValue("expiresAt", Timestamp.from(command.expiresAt())));
            UUID movementId = UUID.randomUUID();
            insertMovement(movementId, branchId, command.items().getFirst().warehouseId(), "RESERVATION", reservationId, "INVENTORY_RESERVATION", command.idempotencyKey(), "Reserva de inventario", command.createdBy());
            for (ReservationItemCommand item : command.items()) {
                BigDecimal before = availableStock(item.warehouseId(), item.productPresentationId());
                reserveStock(item.warehouseId(), item.productPresentationId(), item.quantity());
                if (item.lotId() != null) reserveLotStock(item.warehouseId(), item.lotId(), item.quantity());
                jdbc.update("""
                    INSERT INTO inventory.reservation_items (
                        reservation_item_id, reservation_id, warehouse_id, product_presentation_id, lot_id, quantity
                    ) VALUES (:id, :reservationId, :warehouseId, :presentationId, :lotId, :quantity)
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("reservationId", reservationId)
                    .addValue("warehouseId", item.warehouseId())
                    .addValue("presentationId", item.productPresentationId())
                    .addValue("lotId", item.lotId())
                    .addValue("quantity", scale3(item.quantity())));
                insertMovementItem(movementId, item.productPresentationId(), item.lotId(), "OUT", item.quantity(), BigDecimal.ZERO, before, before.subtract(scale3(item.quantity())));
            }
            return findReservation(reservationId);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("idempotency_key")) {
                throw new InventoryReceiptAlreadyExistsException(command.idempotencyKey());
            }
            throw exception;
        }
    }

    @Override
    public ReservationView releaseReservation(ReleaseReservationCommand command) {
        ReservationView reservation = findReservation(command.reservationId());
        if (!"ACTIVE".equals(reservation.status())) throw new InventoryReceiptException("Solo se pueden liberar reservas activas");
        UUID movementId = UUID.randomUUID();
        UUID warehouseId = reservation.items().getFirst().warehouseId();
        insertMovement(movementId, reservation.branchId(), warehouseId, "RESERVATION_RELEASE", reservation.reservationId(), "INVENTORY_RESERVATION", null, "Liberacion de reserva", command.releasedBy());
        for (ReservationItemView item : reservation.items()) {
            BigDecimal before = availableStock(item.warehouseId(), item.productPresentationId());
            releaseStock(item.warehouseId(), item.productPresentationId(), item.quantity());
            if (item.lotId() != null) releaseLotStock(item.warehouseId(), item.lotId(), item.quantity());
            insertMovementItem(movementId, item.productPresentationId(), item.lotId(), "IN", item.quantity(), BigDecimal.ZERO, before, before.add(scale3(item.quantity())));
        }
        jdbc.update("UPDATE inventory.reservations SET status = 'CANCELLED', updated_at = clock_timestamp() WHERE reservation_id = :id", new MapSqlParameterSource("id", reservation.reservationId()));
        return findReservation(reservation.reservationId());
    }

    @Override
    public List<LotView> findExpiringLots(LocalDate expiresBefore) {
        return jdbc.query("""
            SELECT lot.lot_id, lot.product_presentation_id, lot.supplier_id, lot.lot_number,
                   lot.manufactured_at, lot.expires_at, lot.status, lot.created_at,
                   lb.warehouse_id, lb.on_hand_quantity, lb.available_quantity
            FROM inventory.lots lot
            JOIN inventory.lot_balances lb ON lb.lot_id = lot.lot_id
            WHERE lot.status = 'ACTIVE'
              AND lot.expires_at IS NOT NULL
              AND lot.expires_at <= :expiresBefore
              AND lb.available_quantity > 0
            ORDER BY lot.expires_at, lot.lot_number
            LIMIT 200
            """, new MapSqlParameterSource("expiresBefore", Date.valueOf(expiresBefore)), this::mapLot);
    }

    @Override
    public UUID findBranchIdByWarehouseId(UUID warehouseId) {
        return findWarehouse(warehouseId).branchId();
    }

    @Override
    public UUID findBranchIdByCountId(UUID inventoryCountId) {
        return findBranchIdByWarehouseId(findCount(inventoryCountId).warehouseId());
    }

    @Override
    public UUID findBranchIdByReservationId(UUID reservationId) {
        return findReservation(reservationId).branchId();
    }

    private WarehouseRow findWarehouse(UUID warehouseId) {
        try {
            return jdbc.queryForObject("SELECT warehouse_id, branch_id FROM organization.warehouses WHERE warehouse_id = :id AND status = 'ACTIVE'", new MapSqlParameterSource("id", warehouseId), (rs, rowNum) -> new WarehouseRow(rs.getObject("warehouse_id", UUID.class), rs.getObject("branch_id", UUID.class)));
        } catch (EmptyResultDataAccessException exception) {
            throw new InventoryResourceNotFoundException("un almacen activo", warehouseId);
        }
    }

    private UUID branchFromReservation(List<ReservationItemCommand> items) {
        UUID branchId = null;
        for (ReservationItemCommand item : items) {
            UUID current = findWarehouse(item.warehouseId()).branchId();
            if (branchId == null) branchId = current;
            if (!branchId.equals(current)) throw new InventoryReceiptException("Una reserva no puede mezclar sucursales");
        }
        return branchId;
    }

    private void subtractStock(UUID warehouseId, UUID presentationId, BigDecimal quantity) {
        int updated = jdbc.update("""
            UPDATE inventory.stock_balances
            SET on_hand_quantity = on_hand_quantity - :quantity, version = version + 1, updated_at = clock_timestamp()
            WHERE warehouse_id = :warehouseId
              AND product_presentation_id = :presentationId
              AND available_quantity >= :quantity
            """, params(warehouseId, presentationId).addValue("quantity", scale3(quantity)));
        if (updated != 1) throw new InventoryReceiptException("Stock insuficiente para la operacion");
    }

    private void addStock(UUID warehouseId, UUID presentationId, BigDecimal quantity, BigDecimal unitCost) {
        jdbc.update("""
            INSERT INTO inventory.stock_balances (warehouse_id, product_presentation_id, on_hand_quantity, average_unit_cost, version, updated_at)
            VALUES (:warehouseId, :presentationId, :quantity, :unitCost, 1, clock_timestamp())
            ON CONFLICT (warehouse_id, product_presentation_id)
            DO UPDATE SET on_hand_quantity = inventory.stock_balances.on_hand_quantity + EXCLUDED.on_hand_quantity,
                          average_unit_cost = CASE WHEN EXCLUDED.average_unit_cost > 0 THEN EXCLUDED.average_unit_cost ELSE inventory.stock_balances.average_unit_cost END,
                          version = inventory.stock_balances.version + 1,
                          updated_at = clock_timestamp()
            """, params(warehouseId, presentationId).addValue("quantity", scale3(quantity)).addValue("unitCost", money(unitCost)));
    }

    private void subtractLotStock(UUID warehouseId, UUID lotId, BigDecimal quantity) {
        int updated = jdbc.update("""
            UPDATE inventory.lot_balances
            SET on_hand_quantity = on_hand_quantity - :quantity, version = version + 1, updated_at = clock_timestamp()
            WHERE warehouse_id = :warehouseId AND lot_id = :lotId AND available_quantity >= :quantity
            """, new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("lotId", lotId).addValue("quantity", scale3(quantity)));
        if (updated != 1) throw new InventoryReceiptException("Stock por lote insuficiente para la operacion");
    }

    private void addLotStock(UUID warehouseId, UUID lotId, BigDecimal quantity) {
        jdbc.update("""
            INSERT INTO inventory.lot_balances (warehouse_id, lot_id, on_hand_quantity, version, updated_at)
            VALUES (:warehouseId, :lotId, :quantity, 1, clock_timestamp())
            ON CONFLICT (warehouse_id, lot_id)
            DO UPDATE SET on_hand_quantity = inventory.lot_balances.on_hand_quantity + EXCLUDED.on_hand_quantity,
                          version = inventory.lot_balances.version + 1,
                          updated_at = clock_timestamp()
            """, new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("lotId", lotId).addValue("quantity", scale3(quantity)));
    }

    private void reserveStock(UUID warehouseId, UUID presentationId, BigDecimal quantity) {
        int updated = jdbc.update("""
            UPDATE inventory.stock_balances
            SET reserved_quantity = reserved_quantity + :quantity, version = version + 1, updated_at = clock_timestamp()
            WHERE warehouse_id = :warehouseId AND product_presentation_id = :presentationId AND available_quantity >= :quantity
            """, params(warehouseId, presentationId).addValue("quantity", scale3(quantity)));
        if (updated != 1) throw new InventoryReceiptException("Stock insuficiente para reservar");
    }

    private void releaseStock(UUID warehouseId, UUID presentationId, BigDecimal quantity) {
        int updated = jdbc.update("""
            UPDATE inventory.stock_balances
            SET reserved_quantity = reserved_quantity - :quantity, version = version + 1, updated_at = clock_timestamp()
            WHERE warehouse_id = :warehouseId AND product_presentation_id = :presentationId AND reserved_quantity >= :quantity
            """, params(warehouseId, presentationId).addValue("quantity", scale3(quantity)));
        if (updated != 1) throw new InventoryReceiptException("La reserva excede el stock reservado");
    }

    private void reserveLotStock(UUID warehouseId, UUID lotId, BigDecimal quantity) {
        int updated = jdbc.update("""
            UPDATE inventory.lot_balances
            SET reserved_quantity = reserved_quantity + :quantity, version = version + 1, updated_at = clock_timestamp()
            WHERE warehouse_id = :warehouseId AND lot_id = :lotId AND available_quantity >= :quantity
            """, new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("lotId", lotId).addValue("quantity", scale3(quantity)));
        if (updated != 1) throw new InventoryReceiptException("Stock por lote insuficiente para reservar");
    }

    private void releaseLotStock(UUID warehouseId, UUID lotId, BigDecimal quantity) {
        int updated = jdbc.update("""
            UPDATE inventory.lot_balances
            SET reserved_quantity = reserved_quantity - :quantity, version = version + 1, updated_at = clock_timestamp()
            WHERE warehouse_id = :warehouseId AND lot_id = :lotId AND reserved_quantity >= :quantity
            """, new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("lotId", lotId).addValue("quantity", scale3(quantity)));
        if (updated != 1) throw new InventoryReceiptException("La reserva excede el stock reservado por lote");
    }

    private void insertMovement(UUID id, UUID branchId, UUID warehouseId, String type, UUID sourceId, String sourceType, UUID idempotencyKey, String reason, UUID createdBy) {
        jdbc.update("""
            INSERT INTO inventory.stock_movements (
                stock_movement_id, branch_id, warehouse_id, movement_type, status, source_type, source_id, reason, idempotency_key, created_by, confirmed_at
            ) VALUES (:id, :branchId, :warehouseId, :type, 'CONFIRMED', :sourceType, :sourceId, :reason, :idempotencyKey, :createdBy, clock_timestamp())
            """, new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("branchId", branchId)
            .addValue("warehouseId", warehouseId)
            .addValue("type", type)
            .addValue("sourceType", sourceType)
            .addValue("sourceId", sourceId)
            .addValue("reason", reason)
            .addValue("idempotencyKey", idempotencyKey)
            .addValue("createdBy", createdBy));
    }

    private void insertMovementItem(UUID movementId, UUID presentationId, UUID lotId, String direction, BigDecimal quantity, BigDecimal unitCost, BigDecimal before, BigDecimal after) {
        jdbc.update("""
            INSERT INTO inventory.stock_movement_items (
                stock_movement_item_id, stock_movement_id, product_presentation_id, lot_id,
                direction, quantity, unit_cost, quantity_before, quantity_after
            ) VALUES (:id, :movementId, :presentationId, :lotId, :direction, :quantity, :unitCost, :before, :after)
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

    private StockMovementView findMovement(UUID movementId) {
        StockMovementView movement = jdbc.queryForObject("""
            SELECT stock_movement_id, branch_id, warehouse_id, movement_type, status, source_type, source_id, reason, idempotency_key, created_at, confirmed_at
            FROM inventory.stock_movements WHERE stock_movement_id = :id
            """, new MapSqlParameterSource("id", movementId), this::mapMovementWithoutItems);
        return withMovementItems(movement);
    }

    private StockMovementView withMovementItems(StockMovementView movement) {
        List<StockMovementItemView> items = jdbc.query("""
            SELECT smi.stock_movement_item_id, smi.product_presentation_id, smi.lot_id, smi.direction, smi.quantity, smi.unit_cost, smi.quantity_before, smi.quantity_after,
                   pp.sku, pp.name AS presentation_name, lot.lot_number
            FROM inventory.stock_movement_items smi
            JOIN catalog.product_presentations pp ON pp.product_presentation_id = smi.product_presentation_id
            LEFT JOIN inventory.lots lot ON lot.lot_id = smi.lot_id
            WHERE smi.stock_movement_id = :id
            ORDER BY smi.created_at, smi.stock_movement_item_id
            """, new MapSqlParameterSource("id", movement.stockMovementId()), this::mapMovementItem);
        return new StockMovementView(movement.stockMovementId(), movement.branchId(), movement.warehouseId(), movement.movementType(), movement.status(), movement.sourceType(), movement.sourceId(), movement.reason(), movement.idempotencyKey(), movement.createdAt(), movement.confirmedAt(), items);
    }

    private InventoryCountView findCount(UUID countId) {
        try {
            InventoryCountView count = jdbc.queryForObject("""
                SELECT inventory_count_id, warehouse_id, status, started_by, confirmed_by, started_at, confirmed_at
                FROM inventory.inventory_counts WHERE inventory_count_id = :id
                """, new MapSqlParameterSource("id", countId), this::mapCountWithoutItems);
            List<InventoryCountItemView> items = jdbc.query("""
                SELECT inventory_count_item_id, product_presentation_id, lot_id, expected_quantity, counted_quantity
                FROM inventory.inventory_count_items WHERE inventory_count_id = :id
                ORDER BY inventory_count_item_id
                """, new MapSqlParameterSource("id", countId), this::mapCountItem);
            return new InventoryCountView(count.inventoryCountId(), count.warehouseId(), count.status(), count.startedBy(), count.confirmedBy(), count.startedAt(), count.confirmedAt(), items);
        } catch (EmptyResultDataAccessException exception) {
            throw new InventoryResourceNotFoundException("un conteo de inventario", countId);
        }
    }

    private ReservationView findReservation(UUID reservationId) {
        try {
            ReservationView reservation = jdbc.queryForObject("""
                SELECT reservation_id, branch_id, customer_id, source_type, source_id, status, idempotency_key, expires_at, created_at, updated_at
                FROM inventory.reservations WHERE reservation_id = :id
                """, new MapSqlParameterSource("id", reservationId), this::mapReservationWithoutItems);
            List<ReservationItemView> items = jdbc.query("""
                SELECT reservation_item_id, warehouse_id, product_presentation_id, lot_id, quantity
                FROM inventory.reservation_items WHERE reservation_id = :id
                ORDER BY reservation_item_id
                """, new MapSqlParameterSource("id", reservationId), this::mapReservationItem);
            return new ReservationView(reservation.reservationId(), reservation.branchId(), reservation.customerId(), reservation.sourceType(), reservation.sourceId(), reservation.status(), reservation.idempotencyKey(), reservation.expiresAt(), reservation.createdAt(), reservation.updatedAt(), items);
        } catch (EmptyResultDataAccessException exception) {
            throw new InventoryResourceNotFoundException("una reserva de inventario", reservationId);
        }
    }

    private BigDecimal stock(UUID warehouseId, UUID presentationId) {
        try {
            return scale3(jdbc.queryForObject("SELECT on_hand_quantity FROM inventory.stock_balances WHERE warehouse_id = :warehouseId AND product_presentation_id = :presentationId", params(warehouseId, presentationId), BigDecimal.class));
        } catch (EmptyResultDataAccessException exception) {
            return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal lotStock(UUID warehouseId, UUID lotId) {
        try {
            return scale3(jdbc.queryForObject("SELECT on_hand_quantity FROM inventory.lot_balances WHERE warehouse_id = :warehouseId AND lot_id = :lotId", new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("lotId", lotId), BigDecimal.class));
        } catch (EmptyResultDataAccessException exception) {
            return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal availableStock(UUID warehouseId, UUID presentationId) {
        try {
            return scale3(jdbc.queryForObject("SELECT available_quantity FROM inventory.stock_balances WHERE warehouse_id = :warehouseId AND product_presentation_id = :presentationId", params(warehouseId, presentationId), BigDecimal.class));
        } catch (EmptyResultDataAccessException exception) {
            return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        }
    }

    private StockMovementView mapMovementWithoutItems(ResultSet rs, int rowNum) throws SQLException {
        return new StockMovementView(getUuid(rs, "stock_movement_id"), getUuid(rs, "branch_id"), getUuid(rs, "warehouse_id"), rs.getString("movement_type"), rs.getString("status"), rs.getString("source_type"), getUuid(rs, "source_id"), rs.getString("reason"), getUuid(rs, "idempotency_key"), getInstant(rs, "created_at"), getInstant(rs, "confirmed_at"), List.of());
    }

    private StockMovementItemView mapMovementItem(ResultSet rs, int rowNum) throws SQLException {
        return new StockMovementItemView(getUuid(rs, "stock_movement_item_id"), getUuid(rs, "product_presentation_id"), rs.getString("sku"), rs.getString("presentation_name"), getUuid(rs, "lot_id"), rs.getString("lot_number"), rs.getString("direction").trim(), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_cost"), rs.getBigDecimal("quantity_before"), rs.getBigDecimal("quantity_after"));
    }

    private InventoryCountView mapCountWithoutItems(ResultSet rs, int rowNum) throws SQLException {
        return new InventoryCountView(getUuid(rs, "inventory_count_id"), getUuid(rs, "warehouse_id"), rs.getString("status"), getUuid(rs, "started_by"), getUuid(rs, "confirmed_by"), getInstant(rs, "started_at"), getInstant(rs, "confirmed_at"), List.of());
    }

    private InventoryCountItemView mapCountItem(ResultSet rs, int rowNum) throws SQLException {
        return new InventoryCountItemView(getUuid(rs, "inventory_count_item_id"), getUuid(rs, "product_presentation_id"), getUuid(rs, "lot_id"), rs.getBigDecimal("expected_quantity"), rs.getBigDecimal("counted_quantity"));
    }

    private ReservationView mapReservationWithoutItems(ResultSet rs, int rowNum) throws SQLException {
        return new ReservationView(getUuid(rs, "reservation_id"), getUuid(rs, "branch_id"), getUuid(rs, "customer_id"), rs.getString("source_type"), getUuid(rs, "source_id"), rs.getString("status"), getUuid(rs, "idempotency_key"), getInstant(rs, "expires_at"), getInstant(rs, "created_at"), getInstant(rs, "updated_at"), List.of());
    }

    private ReservationItemView mapReservationItem(ResultSet rs, int rowNum) throws SQLException {
        return new ReservationItemView(getUuid(rs, "reservation_item_id"), getUuid(rs, "warehouse_id"), getUuid(rs, "product_presentation_id"), getUuid(rs, "lot_id"), rs.getBigDecimal("quantity"));
    }

    private LotView mapLot(ResultSet rs, int rowNum) throws SQLException {
        return new LotView(getUuid(rs, "lot_id"), getUuid(rs, "product_presentation_id"), getUuid(rs, "supplier_id"), rs.getString("lot_number"), getLocalDate(rs, "manufactured_at"), getLocalDate(rs, "expires_at"), rs.getString("status"), getUuid(rs, "warehouse_id"), rs.getBigDecimal("on_hand_quantity"), rs.getBigDecimal("available_quantity"), getInstant(rs, "created_at"));
    }

    private MapSqlParameterSource params(UUID warehouseId, UUID presentationId) {
        return new MapSqlParameterSource().addValue("warehouseId", warehouseId).addValue("presentationId", presentationId);
    }

    private static UUID getUuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static Instant getInstant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static LocalDate getLocalDate(ResultSet rs, String column) throws SQLException {
        Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private static String reason(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static BigDecimal scale3(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private record WarehouseRow(UUID warehouseId, UUID branchId) {
    }
}
