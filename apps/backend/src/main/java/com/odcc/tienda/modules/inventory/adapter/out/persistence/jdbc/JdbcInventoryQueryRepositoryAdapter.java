package com.odcc.tienda.modules.inventory.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.inventory.application.model.LotView;
import com.odcc.tienda.modules.inventory.application.model.PalletItemView;
import com.odcc.tienda.modules.inventory.application.model.PalletView;
import com.odcc.tienda.modules.inventory.application.model.StockBalanceView;
import com.odcc.tienda.modules.inventory.application.model.StockMovementItemView;
import com.odcc.tienda.modules.inventory.application.model.StockMovementView;
import com.odcc.tienda.modules.inventory.application.port.out.InventoryQueryRepositoryPort;
import com.odcc.tienda.modules.inventory.application.query.LotQuery;
import com.odcc.tienda.modules.inventory.application.query.PalletQuery;
import com.odcc.tienda.modules.inventory.application.query.StockMovementQuery;
import com.odcc.tienda.modules.inventory.application.query.StockQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcInventoryQueryRepositoryAdapter implements InventoryQueryRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<StockBalanceView> findStock(StockQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("warehouseId", query == null ? null : query.warehouseId())
            .addValue("presentationId", query == null ? null : query.productPresentationId());
        return jdbc.query("""
            SELECT sb.stock_balance_id, sb.warehouse_id, sb.product_presentation_id,
                   pp.sku, pp.name AS presentation_name, p.name AS product_name,
                   sb.on_hand_quantity, sb.reserved_quantity, sb.allocated_quantity,
                   sb.available_quantity, sb.average_unit_cost, sb.updated_at
            FROM inventory.stock_balances sb
            JOIN catalog.product_presentations pp ON pp.product_presentation_id = sb.product_presentation_id
            JOIN catalog.products p ON p.product_id = pp.product_id
            WHERE (CAST(:warehouseId AS uuid) IS NULL OR sb.warehouse_id = CAST(:warehouseId AS uuid))
              AND (CAST(:presentationId AS uuid) IS NULL OR sb.product_presentation_id = CAST(:presentationId AS uuid))
            ORDER BY p.name, pp.name, sb.warehouse_id
            """, params, this::mapStock);
    }

    @Override
    public List<LotView> findLots(LotQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("warehouseId", query == null ? null : query.warehouseId())
            .addValue("presentationId", query == null ? null : query.productPresentationId())
            .addValue("status", normalize(query == null ? null : query.status()))
            .addValue("expiresBefore", toDate(query == null ? null : query.expiresBefore()))
            .addValue("expiresAfter", toDate(query == null ? null : query.expiresAfter()));
        return jdbc.query("""
            SELECT lot.lot_id, lot.product_presentation_id, lot.supplier_id, lot.lot_number,
                   lot.manufactured_at, lot.expires_at, lot.status, lot.created_at,
                   lb.warehouse_id, lb.on_hand_quantity, lb.available_quantity
            FROM inventory.lots lot
            LEFT JOIN inventory.lot_balances lb ON lb.lot_id = lot.lot_id
            WHERE (CAST(:warehouseId AS uuid) IS NULL OR lb.warehouse_id = CAST(:warehouseId AS uuid))
              AND (CAST(:presentationId AS uuid) IS NULL OR lot.product_presentation_id = CAST(:presentationId AS uuid))
              AND (CAST(:status AS text) IS NULL OR lot.status = CAST(:status AS text))
              AND (CAST(:expiresBefore AS date) IS NULL OR lot.expires_at <= CAST(:expiresBefore AS date))
              AND (CAST(:expiresAfter AS date) IS NULL OR lot.expires_at >= CAST(:expiresAfter AS date))
            ORDER BY lot.expires_at NULLS LAST, lot.lot_number
            """, params, this::mapLot);
    }

    @Override
    public Optional<LotView> findLotById(UUID lotId) {
        List<LotView> lots = jdbc.query("""
            SELECT lot.lot_id, lot.product_presentation_id, lot.supplier_id, lot.lot_number,
                   lot.manufactured_at, lot.expires_at, lot.status, lot.created_at,
                   lb.warehouse_id, lb.on_hand_quantity, lb.available_quantity
            FROM inventory.lots lot
            LEFT JOIN inventory.lot_balances lb ON lb.lot_id = lot.lot_id
            WHERE lot.lot_id = :lotId
            ORDER BY lb.warehouse_id NULLS LAST
            """, new MapSqlParameterSource("lotId", lotId), this::mapLot);
        return lots.stream().findFirst();
    }

    @Override
    public List<PalletView> findPallets(PalletQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("warehouseId", query == null ? null : query.warehouseId())
            .addValue("status", normalize(query == null ? null : query.status()));
        return palletRows("""
            WHERE (CAST(:warehouseId AS uuid) IS NULL OR pallet.warehouse_id = CAST(:warehouseId AS uuid))
              AND (CAST(:status AS text) IS NULL OR pallet.status = CAST(:status AS text))
            ORDER BY pallet.received_at DESC, pallet.pallet_code
            """, params);
    }

    @Override
    public Optional<PalletView> findPalletById(UUID palletId) {
        List<PalletView> pallets = palletRows("""
            WHERE pallet.pallet_id = :palletId
            ORDER BY pallet.received_at DESC, pallet.pallet_code
            """, new MapSqlParameterSource("palletId", palletId));
        return pallets.stream().findFirst();
    }

    @Override
    public List<StockMovementView> findMovements(StockMovementQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("warehouseId", query == null ? null : query.warehouseId())
            .addValue("movementType", normalize(query == null ? null : query.movementType()))
            .addValue("status", normalize(query == null ? null : query.status()));
        List<StockMovementView> movements = jdbc.query("""
            SELECT stock_movement_id, branch_id, warehouse_id, movement_type, status,
                   source_type, source_id, reason, idempotency_key, created_at, confirmed_at
            FROM inventory.stock_movements
            WHERE (CAST(:warehouseId AS uuid) IS NULL OR warehouse_id = CAST(:warehouseId AS uuid))
              AND (CAST(:movementType AS text) IS NULL OR movement_type = CAST(:movementType AS text))
              AND (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
            ORDER BY created_at DESC
            LIMIT 200
            """, params, this::mapMovementWithoutItems);
        return movements.stream().map(this::withMovementItems).toList();
    }

    @Override
    public Optional<StockMovementView> findMovementById(UUID movementId) {
        List<StockMovementView> movements = jdbc.query("""
            SELECT stock_movement_id, branch_id, warehouse_id, movement_type, status,
                   source_type, source_id, reason, idempotency_key, created_at, confirmed_at
            FROM inventory.stock_movements
            WHERE stock_movement_id = :movementId
            """, new MapSqlParameterSource("movementId", movementId), this::mapMovementWithoutItems);
        return movements.stream().findFirst().map(this::withMovementItems);
    }

    private List<PalletView> palletRows(String whereSql, MapSqlParameterSource params) {
        Map<UUID, PalletView> pallets = new LinkedHashMap<>();
        jdbc.query("""
            SELECT pallet.pallet_id, pallet.warehouse_id, pallet.stock_movement_id,
                   pallet.pallet_code, pallet.external_pallet_code, pallet.status,
                   pallet.received_at,
                   item.pallet_item_id, item.product_presentation_id, item.lot_id,
                   item.quantity, pp.sku, pp.name AS presentation_name, lot.lot_number
            FROM inventory.pallets pallet
            LEFT JOIN inventory.pallet_items item ON item.pallet_id = pallet.pallet_id
            LEFT JOIN catalog.product_presentations pp ON pp.product_presentation_id = item.product_presentation_id
            LEFT JOIN inventory.lots lot ON lot.lot_id = item.lot_id
            """ + whereSql, params, rs -> {
            UUID palletId = rs.getObject("pallet_id", UUID.class);
            UUID warehouseId = getUuid(rs, "warehouse_id");
            UUID stockMovementId = getUuid(rs, "stock_movement_id");
            String palletCode = getString(rs, "pallet_code");
            String externalPalletCode = getString(rs, "external_pallet_code");
            String status = getString(rs, "status");
            Instant receivedAt = getInstant(rs, "received_at");
            PalletView pallet = pallets.computeIfAbsent(palletId, id -> new PalletView(
                id,
                warehouseId,
                stockMovementId,
                palletCode,
                externalPalletCode,
                status,
                receivedAt,
                new ArrayList<>()
            ));
            UUID itemId = getUuid(rs, "pallet_item_id");
            if (itemId != null) {
                pallet.items().add(new PalletItemView(
                    itemId,
                    getUuid(rs, "product_presentation_id"),
                    getString(rs, "sku"),
                    getString(rs, "presentation_name"),
                    getUuid(rs, "lot_id"),
                    getString(rs, "lot_number"),
                    rs.getBigDecimal("quantity")
                ));
            }
        });
        return new ArrayList<>(pallets.values());
    }

    private StockMovementView withMovementItems(StockMovementView movement) {
        List<StockMovementItemView> items = jdbc.query("""
            SELECT smi.stock_movement_item_id, smi.product_presentation_id, smi.lot_id,
                   smi.direction, smi.quantity, smi.unit_cost, smi.quantity_before, smi.quantity_after,
                   pp.sku, pp.name AS presentation_name, lot.lot_number
            FROM inventory.stock_movement_items smi
            JOIN catalog.product_presentations pp ON pp.product_presentation_id = smi.product_presentation_id
            LEFT JOIN inventory.lots lot ON lot.lot_id = smi.lot_id
            WHERE smi.stock_movement_id = :movementId
            ORDER BY smi.created_at, smi.stock_movement_item_id
            """, new MapSqlParameterSource("movementId", movement.stockMovementId()), this::mapMovementItem);
        return new StockMovementView(
            movement.stockMovementId(), movement.branchId(), movement.warehouseId(), movement.movementType(), movement.status(),
            movement.sourceType(), movement.sourceId(), movement.reason(), movement.idempotencyKey(), movement.createdAt(),
            movement.confirmedAt(), items
        );
    }

    private StockBalanceView mapStock(ResultSet rs, int rowNum) throws SQLException {
        return new StockBalanceView(
            getUuid(rs, "stock_balance_id"),
            getUuid(rs, "warehouse_id"),
            getUuid(rs, "product_presentation_id"),
            rs.getString("sku"),
            rs.getString("presentation_name"),
            rs.getString("product_name"),
            rs.getBigDecimal("on_hand_quantity"),
            rs.getBigDecimal("reserved_quantity"),
            rs.getBigDecimal("allocated_quantity"),
            rs.getBigDecimal("available_quantity"),
            rs.getBigDecimal("average_unit_cost"),
            getInstant(rs, "updated_at")
        );
    }

    private LotView mapLot(ResultSet rs, int rowNum) throws SQLException {
        return new LotView(
            getUuid(rs, "lot_id"),
            getUuid(rs, "product_presentation_id"),
            getUuid(rs, "supplier_id"),
            rs.getString("lot_number"),
            getLocalDate(rs, "manufactured_at"),
            getLocalDate(rs, "expires_at"),
            rs.getString("status"),
            getUuid(rs, "warehouse_id"),
            rs.getBigDecimal("on_hand_quantity"),
            rs.getBigDecimal("available_quantity"),
            getInstant(rs, "created_at")
        );
    }

    private StockMovementView mapMovementWithoutItems(ResultSet rs, int rowNum) throws SQLException {
        return new StockMovementView(
            getUuid(rs, "stock_movement_id"),
            getUuid(rs, "branch_id"),
            getUuid(rs, "warehouse_id"),
            rs.getString("movement_type"),
            rs.getString("status"),
            rs.getString("source_type"),
            getUuid(rs, "source_id"),
            rs.getString("reason"),
            getUuid(rs, "idempotency_key"),
            getInstant(rs, "created_at"),
            getInstant(rs, "confirmed_at"),
            List.of()
        );
    }

    private StockMovementItemView mapMovementItem(ResultSet rs, int rowNum) throws SQLException {
        return new StockMovementItemView(
            getUuid(rs, "stock_movement_item_id"),
            getUuid(rs, "product_presentation_id"),
            rs.getString("sku"),
            rs.getString("presentation_name"),
            getUuid(rs, "lot_id"),
            rs.getString("lot_number"),
            rs.getString("direction"),
            rs.getBigDecimal("quantity"),
            rs.getBigDecimal("unit_cost"),
            rs.getBigDecimal("quantity_before"),
            rs.getBigDecimal("quantity_after")
        );
    }

    private static UUID getUuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static String getString(ResultSet rs, String column) throws SQLException {
        return rs.getString(column);
    }

    private static Instant getInstant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static LocalDate getLocalDate(ResultSet rs, String column) throws SQLException {
        Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    private static Date toDate(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}


