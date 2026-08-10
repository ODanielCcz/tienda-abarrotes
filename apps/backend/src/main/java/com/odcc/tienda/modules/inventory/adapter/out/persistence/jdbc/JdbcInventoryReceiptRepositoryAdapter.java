package com.odcc.tienda.modules.inventory.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.inventory.application.command.CreateInventoryReceiptCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryReceiptItemCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryReceiptPalletCommand;
import com.odcc.tienda.modules.inventory.application.exception.InventoryReceiptException;
import com.odcc.tienda.modules.inventory.application.exception.InventoryReceiptAlreadyExistsException;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceiptItem;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceiptPallet;
import com.odcc.tienda.modules.inventory.application.port.out.InventoryReceiptRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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
public class JdbcInventoryReceiptRepositoryAdapter implements InventoryReceiptRepositoryPort {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public UUID findBranchIdByWarehouseId(UUID warehouseId) {
        return findWarehouse(warehouseId).branchId();
    }

    @Override
    public Optional<InventoryReceipt> findByIdempotencyKey(UUID idempotencyKey, String fingerprint) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        try {
            UUID receiptId = jdbc.queryForObject("""
                SELECT stock_movement_id
                FROM inventory.stock_movements
                WHERE source_type = 'INVENTORY_RECEIPT'
                  AND idempotency_key = :idempotencyKey
                  AND source_fingerprint = :fingerprint
                """, new MapSqlParameterSource()
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("fingerprint", fingerprint), UUID.class);
            return findById(receiptId);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existsByIdempotencyKeyWithDifferentFingerprint(UUID idempotencyKey, String fingerprint) {
        if (idempotencyKey == null) {
            return false;
        }
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM inventory.stock_movements
            WHERE source_type = 'INVENTORY_RECEIPT'
              AND idempotency_key = :idempotencyKey
              AND COALESCE(source_fingerprint, '') <> :fingerprint
            """, new MapSqlParameterSource()
            .addValue("idempotencyKey", idempotencyKey)
            .addValue("fingerprint", fingerprint), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public InventoryReceipt create(CreateInventoryReceiptCommand command, String fingerprint) {
        WarehouseRow warehouse = findWarehouse(command.warehouseId());
        UUID movementId = UUID.randomUUID();
        UUID idempotencyKey = command.idempotencyKey();
        Instant receivedAt = Instant.now();

        List<UUID> claimedMovementIds = jdbc.query("""
            INSERT INTO inventory.stock_movements (
                stock_movement_id, branch_id, warehouse_id, movement_type, status,
                source_type, source_id, reason, idempotency_key, source_fingerprint, confirmed_at
            ) VALUES (
                :movementId, :branchId, :warehouseId, 'PURCHASE_RECEIPT', 'CONFIRMED',
                'INVENTORY_RECEIPT', :movementId, :reason, :idempotencyKey, :fingerprint, :confirmedAt
            )
            ON CONFLICT (idempotency_key) DO NOTHING
            RETURNING stock_movement_id
            """, new MapSqlParameterSource()
            .addValue("movementId", movementId)
            .addValue("branchId", warehouse.branchId())
            .addValue("warehouseId", warehouse.warehouseId())
            .addValue("reason", command.reason())
            .addValue("idempotencyKey", idempotencyKey)
            .addValue("fingerprint", fingerprint)
            .addValue("confirmedAt", Timestamp.from(receivedAt)),
            (resultSet, rowNumber) -> resultSet.getObject("stock_movement_id", UUID.class));

        if (claimedMovementIds.isEmpty()) {
            Optional<InventoryReceipt> existing = findByIdempotencyKey(idempotencyKey, fingerprint);
            if (existing.isPresent()) return existing.get();
            throw new InventoryReceiptAlreadyExistsException(idempotencyKey);
        }

        List<InventoryReceiptItem> simpleItems = new ArrayList<>();
        if (command.items() != null) {
            for (InventoryReceiptItemCommand item : command.items()) {
                simpleItems.add(processItem(movementId, command.warehouseId(), command.supplierId(), item));
            }
        }

        List<InventoryReceiptPallet> pallets = new ArrayList<>();
        if (command.pallets() != null) {
            int index = 1;
            for (InventoryReceiptPalletCommand pallet : command.pallets()) {
                pallets.add(processPallet(movementId, command.warehouseId(), command.supplierId(), pallet, index++));
            }
        }

        return new InventoryReceipt(movementId, command.warehouseId(), command.supplierId(), "CONFIRMED", receivedAt, simpleItems, pallets);
    }

    @Override
    public Optional<InventoryReceipt> findById(UUID receiptId) {
        MovementRow movement;
        try {
            movement = jdbc.queryForObject("""
                SELECT stock_movement_id, warehouse_id, status, created_at, confirmed_at
                FROM inventory.stock_movements
                WHERE stock_movement_id = :receiptId
                  AND movement_type = 'PURCHASE_RECEIPT'
                """, new MapSqlParameterSource("receiptId", receiptId), this::mapMovement);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }

        List<InventoryReceiptItem> items = jdbc.query("""
            SELECT smi.stock_movement_item_id, smi.product_presentation_id, smi.lot_id,
                   lot.lot_number, smi.quantity, smi.unit_cost, smi.quantity_before,
                   smi.quantity_after, lot.manufactured_at, lot.expires_at
            FROM inventory.stock_movement_items smi
            LEFT JOIN inventory.lots lot ON lot.lot_id = smi.lot_id
            WHERE smi.stock_movement_id = :receiptId
            ORDER BY smi.created_at, smi.stock_movement_item_id
            """, new MapSqlParameterSource("receiptId", receiptId), this::mapItem);

        Map<UUID, InventoryReceiptPallet> palletMap = new LinkedHashMap<>();
        jdbc.query("""
            SELECT p.pallet_id, p.pallet_code, p.external_pallet_code, p.status,
                   pi.product_presentation_id, pi.lot_id, lot.lot_number, pi.quantity,
                   lot.manufactured_at, lot.expires_at
            FROM inventory.pallets p
            LEFT JOIN inventory.pallet_items pi ON pi.pallet_id = p.pallet_id
            LEFT JOIN inventory.lots lot ON lot.lot_id = pi.lot_id
            WHERE p.stock_movement_id = :receiptId
            ORDER BY p.created_at, p.pallet_id, pi.created_at
            """, new MapSqlParameterSource("receiptId", receiptId), rs -> {
                UUID palletId = rs.getObject("pallet_id", UUID.class);
                InventoryReceiptPallet current = palletMap.computeIfAbsent(palletId, id -> new InventoryReceiptPallet(
                    id,
                    readString(rs, "pallet_code"),
                    readString(rs, "external_pallet_code"),
                    readString(rs, "status"),
                    new ArrayList<>()
                ));
                UUID presentationId = rs.getObject("product_presentation_id", UUID.class);
                if (presentationId != null) {
                    current.items().add(new InventoryReceiptItem(
                        null,
                        presentationId,
                        rs.getObject("lot_id", UUID.class),
                        readString(rs, "lot_number"),
                        rs.getBigDecimal("quantity"),
                        ZERO,
                        ZERO,
                        ZERO,
                        readLocalDate(rs, "manufactured_at"),
                        readLocalDate(rs, "expires_at")
                    ));
                }
            });

        return Optional.of(new InventoryReceipt(
            movement.id(),
            movement.warehouseId(),
            null,
            movement.status(),
            movement.confirmedAt() == null ? movement.createdAt() : movement.confirmedAt(),
            items,
            new ArrayList<>(palletMap.values())
        ));
    }

    private InventoryReceiptPallet processPallet(
        UUID movementId,
        UUID warehouseId,
        UUID supplierId,
        InventoryReceiptPalletCommand command,
        int index
    ) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new InventoryReceiptException("El pallet debe incluir al menos un item");
        }
        UUID palletId = UUID.randomUUID();
        String palletCode = "PAL-" + movementId.toString().substring(0, 8).toUpperCase() + "-" + String.format("%03d", index);
        jdbc.update("""
            INSERT INTO inventory.pallets (
                pallet_id, warehouse_id, stock_movement_id, pallet_code, external_pallet_code, status, received_at
            ) VALUES (
                :palletId, :warehouseId, :movementId, :palletCode, :externalPalletCode, 'ACTIVE', clock_timestamp()
            )
            """, new MapSqlParameterSource()
            .addValue("palletId", palletId)
            .addValue("warehouseId", warehouseId)
            .addValue("movementId", movementId)
            .addValue("palletCode", palletCode)
            .addValue("externalPalletCode", normalize(command.externalPalletCode())));

        List<InventoryReceiptItem> items = new ArrayList<>();
        for (InventoryReceiptItemCommand item : command.items()) {
            InventoryReceiptItem receivedItem = processItem(movementId, warehouseId, supplierId, item);
            jdbc.update("""
                INSERT INTO inventory.pallet_items (
                    pallet_item_id, pallet_id, product_presentation_id, lot_id, quantity
                ) VALUES (
                    :palletItemId, :palletId, :presentationId, :lotId, :quantity
                )
                """, new MapSqlParameterSource()
                .addValue("palletItemId", UUID.randomUUID())
                .addValue("palletId", palletId)
                .addValue("presentationId", receivedItem.productPresentationId())
                .addValue("lotId", receivedItem.lotId())
                .addValue("quantity", receivedItem.quantity()));
            items.add(receivedItem);
        }

        return new InventoryReceiptPallet(palletId, palletCode, normalize(command.externalPalletCode()), "ACTIVE", items);
    }

    private InventoryReceiptItem processItem(
        UUID movementId,
        UUID warehouseId,
        UUID supplierId,
        InventoryReceiptItemCommand command
    ) {
        validateItem(command);
        PresentationRow presentation = findPresentation(command.productPresentationId());
        validateTrackingRules(presentation, command);

        UUID lotId = null;
        String lotNumber = normalizeLot(command.lotNumber());
        if (presentation.tracksLots()) {
            lotId = findOrCreateLot(presentation.id(), supplierId, lotNumber, command.manufacturedAt(), command.expiresAt());
        }

        BigDecimal quantityBefore = currentStock(warehouseId, presentation.id());
        BigDecimal quantityAfter = quantityBefore.add(command.quantity());
        upsertStockBalance(warehouseId, presentation.id(), command.quantity(), command.unitCost());
        if (lotId != null) {
            upsertLotBalance(warehouseId, lotId, command.quantity());
        }

        UUID itemId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO inventory.stock_movement_items (
                stock_movement_item_id, stock_movement_id, product_presentation_id, lot_id,
                direction, quantity, unit_cost, quantity_before, quantity_after
            ) VALUES (
                :itemId, :movementId, :presentationId, :lotId,
                'IN', :quantity, :unitCost, :quantityBefore, :quantityAfter
            )
            """, new MapSqlParameterSource()
            .addValue("itemId", itemId)
            .addValue("movementId", movementId)
            .addValue("presentationId", presentation.id())
            .addValue("lotId", lotId)
            .addValue("quantity", command.quantity())
            .addValue("unitCost", unitCost(command.unitCost()))
            .addValue("quantityBefore", quantityBefore)
            .addValue("quantityAfter", quantityAfter));

        return new InventoryReceiptItem(
            itemId,
            presentation.id(),
            lotId,
            lotNumber,
            command.quantity(),
            unitCost(command.unitCost()),
            quantityBefore,
            quantityAfter,
            command.manufacturedAt(),
            command.expiresAt()
        );
    }

    private void validateItem(InventoryReceiptItemCommand item) {
        if (item == null) {
            throw new InventoryReceiptException("El item de recepcion es obligatorio");
        }
        if (item.productPresentationId() == null) {
            throw new InventoryReceiptException("La presentacion del producto es obligatoria");
        }
        if (item.quantity() == null || item.quantity().compareTo(ZERO) <= 0) {
            throw new InventoryReceiptException("La cantidad recibida debe ser mayor a cero");
        }
        if (item.unitCost() != null && item.unitCost().compareTo(ZERO) < 0) {
            throw new InventoryReceiptException("El costo unitario no puede ser negativo");
        }
    }

    private void validateTrackingRules(PresentationRow presentation, InventoryReceiptItemCommand item) {
        if (!presentation.tracksInventory()) {
            throw new InventoryReceiptException("El producto de la presentacion " + presentation.id() + " no controla inventario");
        }
        if (presentation.tracksLots() && normalizeLot(item.lotNumber()) == null) {
            throw new InventoryReceiptException("El producto requiere numero de lote");
        }
        if (presentation.tracksExpiration() && item.expiresAt() == null) {
            throw new InventoryReceiptException("El producto requiere fecha de caducidad");
        }
        if (!presentation.tracksLots() && (normalizeLot(item.lotNumber()) != null || item.expiresAt() != null || item.manufacturedAt() != null)) {
            throw new InventoryReceiptException("El producto no controla lotes; no debe recibir lote, produccion o caducidad");
        }
        if (item.expiresAt() != null && item.manufacturedAt() != null && item.expiresAt().isBefore(item.manufacturedAt())) {
            throw new InventoryReceiptException("La caducidad no puede ser anterior a la fecha de produccion");
        }
    }

    private WarehouseRow findWarehouse(UUID warehouseId) {
        try {
            return jdbc.queryForObject("""
                SELECT warehouse_id, branch_id
                FROM organization.warehouses
                WHERE warehouse_id = :warehouseId
                  AND status = 'ACTIVE'
                """, new MapSqlParameterSource("warehouseId", warehouseId), (rs, rowNum) -> new WarehouseRow(
                rs.getObject("warehouse_id", UUID.class),
                rs.getObject("branch_id", UUID.class)
            ));
        } catch (EmptyResultDataAccessException exception) {
            throw new InventoryReceiptException("No existe un almacen activo con id " + warehouseId);
        }
    }

    private PresentationRow findPresentation(UUID presentationId) {
        try {
            return jdbc.queryForObject("""
                SELECT pp.product_presentation_id, p.tracks_inventory, p.tracks_lots, p.tracks_expiration
                FROM catalog.product_presentations pp
                JOIN catalog.products p ON p.product_id = pp.product_id
                WHERE pp.product_presentation_id = :presentationId
                  AND pp.status = 'ACTIVE'
                  AND p.status = 'ACTIVE'
                """, new MapSqlParameterSource("presentationId", presentationId), (rs, rowNum) -> new PresentationRow(
                rs.getObject("product_presentation_id", UUID.class),
                rs.getBoolean("tracks_inventory"),
                rs.getBoolean("tracks_lots"),
                rs.getBoolean("tracks_expiration")
            ));
        } catch (EmptyResultDataAccessException exception) {
            throw new InventoryReceiptException("No existe una presentacion activa con id " + presentationId);
        }
    }

    private UUID findOrCreateLot(
        UUID presentationId,
        UUID supplierId,
        String lotNumber,
        LocalDate manufacturedAt,
        LocalDate expiresAt
    ) {
        try {
            LotRow existing = jdbc.queryForObject("""
                SELECT lot_id, manufactured_at, expires_at
                FROM inventory.lots
                WHERE product_presentation_id = :presentationId
                  AND lot_number = :lotNumber
                """, new MapSqlParameterSource()
                .addValue("presentationId", presentationId)
                .addValue("lotNumber", lotNumber), (rs, rowNum) -> new LotRow(
                rs.getObject("lot_id", UUID.class),
                readLocalDate(rs, "manufactured_at"),
                readLocalDate(rs, "expires_at")
            ));
            if (!sameDate(existing.expiresAt(), expiresAt)) {
                throw new InventoryReceiptException("El lote " + lotNumber + " ya existe con una caducidad distinta");
            }
            return existing.id();
        } catch (EmptyResultDataAccessException exception) {
            UUID lotId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO inventory.lots (
                    lot_id, product_presentation_id, supplier_id, lot_number, manufactured_at, expires_at, status
                ) VALUES (
                    :lotId, :presentationId, :supplierId, :lotNumber, :manufacturedAt, :expiresAt, 'ACTIVE'
                )
                """, new MapSqlParameterSource()
                .addValue("lotId", lotId)
                .addValue("presentationId", presentationId)
                .addValue("supplierId", supplierId)
                .addValue("lotNumber", lotNumber)
                .addValue("manufacturedAt", manufacturedAt == null ? null : Date.valueOf(manufacturedAt))
                .addValue("expiresAt", expiresAt == null ? null : Date.valueOf(expiresAt)));
            return lotId;
        }
    }

    private BigDecimal currentStock(UUID warehouseId, UUID presentationId) {
        try {
            return jdbc.queryForObject("""
                SELECT on_hand_quantity
                FROM inventory.stock_balances
                WHERE warehouse_id = :warehouseId
                  AND product_presentation_id = :presentationId
                """, new MapSqlParameterSource()
                .addValue("warehouseId", warehouseId)
                .addValue("presentationId", presentationId), BigDecimal.class);
        } catch (EmptyResultDataAccessException exception) {
            return ZERO;
        }
    }

    private void upsertStockBalance(UUID warehouseId, UUID presentationId, BigDecimal quantity, BigDecimal unitCost) {
        jdbc.update("""
            INSERT INTO inventory.stock_balances (
                warehouse_id, product_presentation_id, on_hand_quantity, average_unit_cost, version, updated_at
            ) VALUES (
                :warehouseId, :presentationId, :quantity, :unitCost, 1, clock_timestamp()
            )
            ON CONFLICT (warehouse_id, product_presentation_id)
            DO UPDATE SET
                on_hand_quantity = inventory.stock_balances.on_hand_quantity + EXCLUDED.on_hand_quantity,
                average_unit_cost = EXCLUDED.average_unit_cost,
                version = inventory.stock_balances.version + 1,
                updated_at = clock_timestamp()
            """, new MapSqlParameterSource()
            .addValue("warehouseId", warehouseId)
            .addValue("presentationId", presentationId)
            .addValue("quantity", quantity)
            .addValue("unitCost", unitCost(unitCost)));
    }

    private void upsertLotBalance(UUID warehouseId, UUID lotId, BigDecimal quantity) {
        jdbc.update("""
            INSERT INTO inventory.lot_balances (
                warehouse_id, lot_id, on_hand_quantity, version, updated_at
            ) VALUES (
                :warehouseId, :lotId, :quantity, 1, clock_timestamp()
            )
            ON CONFLICT (warehouse_id, lot_id)
            DO UPDATE SET
                on_hand_quantity = inventory.lot_balances.on_hand_quantity + EXCLUDED.on_hand_quantity,
                version = inventory.lot_balances.version + 1,
                updated_at = clock_timestamp()
            """, new MapSqlParameterSource()
            .addValue("warehouseId", warehouseId)
            .addValue("lotId", lotId)
            .addValue("quantity", quantity));
    }

    private MovementRow mapMovement(ResultSet rs, int rowNum) throws SQLException {
        return new MovementRow(
            rs.getObject("stock_movement_id", UUID.class),
            rs.getObject("warehouse_id", UUID.class),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toInstant()
        );
    }

    private InventoryReceiptItem mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new InventoryReceiptItem(
            rs.getObject("stock_movement_item_id", UUID.class),
            rs.getObject("product_presentation_id", UUID.class),
            rs.getObject("lot_id", UUID.class),
            rs.getString("lot_number"),
            rs.getBigDecimal("quantity"),
            rs.getBigDecimal("unit_cost"),
            rs.getBigDecimal("quantity_before"),
            rs.getBigDecimal("quantity_after"),
            readLocalDate(rs, "manufactured_at"),
            readLocalDate(rs, "expires_at")
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeLot(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private static BigDecimal unitCost(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private static boolean sameDate(LocalDate left, LocalDate right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String readString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static LocalDate readLocalDate(ResultSet rs, String column) throws SQLException {
        Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    private record WarehouseRow(UUID warehouseId, UUID branchId) {
    }

    private record PresentationRow(UUID id, boolean tracksInventory, boolean tracksLots, boolean tracksExpiration) {
    }

    private record LotRow(UUID id, LocalDate manufacturedAt, LocalDate expiresAt) {
    }

    private record MovementRow(UUID id, UUID warehouseId, String status, Instant createdAt, Instant confirmedAt) {
    }
}

