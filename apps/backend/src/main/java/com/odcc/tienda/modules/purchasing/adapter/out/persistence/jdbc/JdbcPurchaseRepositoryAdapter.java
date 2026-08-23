package com.odcc.tienda.modules.purchasing.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseItemCommand;
import com.odcc.tienda.modules.purchasing.application.exception.PurchaseItemNotFoundException;
import com.odcc.tienda.modules.purchasing.application.exception.PurchaseItemMismatchException;
import com.odcc.tienda.modules.purchasing.application.exception.PurchasingException;
import com.odcc.tienda.modules.purchasing.application.model.Purchase;
import com.odcc.tienda.modules.purchasing.application.model.PurchaseItem;
import com.odcc.tienda.modules.purchasing.application.port.out.PurchaseRepositoryPort;
import com.odcc.tienda.modules.purchasing.application.query.ListPurchasesQuery;
import com.odcc.tienda.shared.application.authorization.BranchScope;
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

@Repository
@RequiredArgsConstructor
public class JdbcPurchaseRepositoryAdapter implements PurchaseRepositoryPort {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public UUID findBranchIdByWarehouseId(UUID warehouseId) {
        try {
            return jdbc.queryForObject("""
                SELECT branch_id
                FROM organization.warehouses
                WHERE warehouse_id = :warehouseId
                """, new MapSqlParameterSource("warehouseId", warehouseId), UUID.class);
        } catch (EmptyResultDataAccessException exception) {
            throw new PurchasingException("El almacen no existe");
        }
    }

    @Override
    public Optional<Purchase> findByIdempotencyKey(UUID idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();
        try {
            UUID id = jdbc.queryForObject("SELECT purchase_id FROM purchasing.purchases WHERE idempotency_key = :key", new MapSqlParameterSource("key", idempotencyKey), UUID.class);
            return findById(id);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean inventoryReceiptExists(UUID idempotencyKey) {
        if (idempotencyKey == null) return false;
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM inventory.stock_movements
            WHERE source_type = 'INVENTORY_RECEIPT'
              AND idempotency_key = :key
            """, new MapSqlParameterSource("key", idempotencyKey), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public Purchase create(CreatePurchaseCommand command) {
        WarehouseRow warehouse = findWarehouse(command.warehouseId());
        UUID purchaseId = UUID.randomUUID();
        Totals totals = totals(command.items());
        jdbc.update("""
            INSERT INTO purchasing.purchases (
                purchase_id, branch_id, warehouse_id, supplier_id, supplier_document, status,
                payment_status, currency_code, subtotal, discount_total, tax_total, total,
                idempotency_key, purchased_at, created_at
            ) VALUES (
                :purchaseId, :branchId, :warehouseId, :supplierId, :supplierDocument, 'DRAFT',
                'PENDING', :currencyCode, :subtotal, :discountTotal, :taxTotal, :total,
                :idempotencyKey, clock_timestamp(), clock_timestamp()
            )
            """, new MapSqlParameterSource()
            .addValue("purchaseId", purchaseId)
            .addValue("branchId", warehouse.branchId())
            .addValue("warehouseId", command.warehouseId())
            .addValue("supplierId", command.supplierId())
            .addValue("supplierDocument", normalize(command.supplierDocument()))
            .addValue("currencyCode", normalizeCurrency(command.currencyCode()))
            .addValue("subtotal", totals.subtotal())
            .addValue("discountTotal", totals.discount())
            .addValue("taxTotal", totals.tax())
            .addValue("total", totals.total())
            .addValue("idempotencyKey", command.idempotencyKey()));
        for (CreatePurchaseItemCommand item : command.items()) {
            insertItem(purchaseId, item);
        }
        return findById(purchaseId).orElseThrow();
    }

    @Override
    public void lockIdempotencyKey(UUID idempotencyKey) {
        jdbc.queryForObject(
            "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:key AS text), 0))",
            new MapSqlParameterSource("key", idempotencyKey),
            (resultSet, rowNumber) -> Boolean.TRUE
        );
    }

    @Override
    public Optional<Purchase> findById(UUID purchaseId) {
        try {
            Purchase header = jdbc.queryForObject("SELECT * FROM purchasing.purchases WHERE purchase_id = :id", new MapSqlParameterSource("id", purchaseId), this::mapPurchaseWithoutItems);
            return Optional.of(withItems(header));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<Purchase> findAll(ListPurchasesQuery query) {
        return findAll(query, BranchScope.global());
    }

    @Override
    public List<Purchase> findAll(
        ListPurchasesQuery query,
        BranchScope scope
    ) {
        BranchScope effectiveScope = scope == null ? BranchScope.restricted(java.util.Set.of()) : scope;
        if (!effectiveScope.globalAccess() && effectiveScope.branchIds().isEmpty()) return List.of();
        List<Purchase> headers = jdbc.query("""
            SELECT *
            FROM purchasing.purchases
            WHERE (:supplierId IS NULL OR supplier_id = :supplierId)
              AND (:warehouseId IS NULL OR warehouse_id = :warehouseId)
              AND (:status IS NULL OR status = :status)
              AND (CAST(:globalAccess AS boolean) OR branch_id IN (:authorizedBranchIds))
            ORDER BY purchased_at DESC
            LIMIT 200
            """, new MapSqlParameterSource()
            .addValue("supplierId", query == null ? null : query.supplierId())
            .addValue("warehouseId", query == null ? null : query.warehouseId())
            .addValue("status", normalize(query == null ? null : query.status()))
            .addValue("globalAccess", effectiveScope.globalAccess())
            .addValue("authorizedBranchIds", effectiveScope.globalAccess()
                ? List.of(new UUID(0L, 0L))
                : List.copyOf(effectiveScope.branchIds())), this::mapPurchaseWithoutItems);
        return headers.stream().map(this::withItems).toList();
    }

    @Override
    public Purchase confirm(UUID purchaseId) {
        int updated = jdbc.update("""
            UPDATE purchasing.purchases
            SET status = 'CONFIRMED', confirmed_at = clock_timestamp()
            WHERE purchase_id = :id AND status = 'DRAFT'
            """, new MapSqlParameterSource("id", purchaseId));
        if (updated == 0) throw new PurchasingException("No fue posible confirmar la compra");
        return findById(purchaseId).orElseThrow();
    }

    @Override
    public PurchaseItem findItemById(UUID purchaseId, UUID purchaseItemId) {
        try {
            return jdbc.queryForObject(
                "SELECT * FROM purchasing.purchase_items WHERE purchase_id = :purchaseId AND purchase_item_id = :id",
                new MapSqlParameterSource("purchaseId", purchaseId).addValue("id", purchaseItemId),
                this::mapItem
            );
        } catch (EmptyResultDataAccessException exception) {
            Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM purchasing.purchase_items WHERE purchase_item_id = :id",
                new MapSqlParameterSource("id", purchaseItemId),
                Integer.class
            );
            if (existing != null && existing > 0) {
                throw new PurchaseItemMismatchException(purchaseId, purchaseItemId);
            }
            throw new PurchaseItemNotFoundException(purchaseItemId);
        }
    }

    @Override
    public void addReceivedQuantity(UUID purchaseId, UUID purchaseItemId, BigDecimal quantity) {
        int updated = jdbc.update("""
            UPDATE purchasing.purchase_items
            SET received_quantity = received_quantity + :quantity
            WHERE purchase_id = :purchaseId
              AND purchase_item_id = :id
              AND received_quantity + :quantity <= quantity
            """, new MapSqlParameterSource().addValue("purchaseId", purchaseId).addValue("id", purchaseItemId).addValue("quantity", quantity));
        if (updated == 0) throw new PurchasingException("No fue posible actualizar la cantidad recibida del item " + purchaseItemId);
    }

    @Override
    public Purchase refreshStatusAfterReceive(UUID purchaseId) {
        jdbc.update("""
            UPDATE purchasing.purchases
            SET status = CASE
                WHEN NOT EXISTS (
                    SELECT 1 FROM purchasing.purchase_items item
                    WHERE item.purchase_id = :purchaseId AND item.received_quantity < item.quantity
                ) THEN 'RECEIVED'
                ELSE 'PARTIALLY_RECEIVED'
            END
            WHERE purchase_id = :purchaseId
            """, new MapSqlParameterSource("purchaseId", purchaseId));
        return findById(purchaseId).orElseThrow();
    }

    private void insertItem(UUID purchaseId, CreatePurchaseItemCommand command) {
        PresentationRow presentation = findPresentation(command.productPresentationId());
        BigDecimal quantity = scale3(command.quantity());
        BigDecimal unitCost = money(command.unitCost());
        BigDecimal discount = money(command.discountAmount());
        BigDecimal tax = money(command.taxAmount());
        BigDecimal lineTotal = quantity.multiply(unitCost).setScale(4, RoundingMode.HALF_UP).subtract(discount).add(tax);
        if (lineTotal.compareTo(ZERO) < 0) throw new PurchasingException("El total del item no puede ser negativo");
        jdbc.update("""
            INSERT INTO purchasing.purchase_items (
                purchase_item_id, purchase_id, product_presentation_id, product_name_snapshot, sku_snapshot,
                quantity, received_quantity, unit_cost, discount_amount, tax_amount, line_total
            ) VALUES (
                :itemId, :purchaseId, :presentationId, :productName, :sku,
                :quantity, 0, :unitCost, :discount, :tax, :lineTotal
            )
            """, new MapSqlParameterSource()
            .addValue("itemId", UUID.randomUUID())
            .addValue("purchaseId", purchaseId)
            .addValue("presentationId", command.productPresentationId())
            .addValue("productName", presentation.productName())
            .addValue("sku", presentation.sku())
            .addValue("quantity", quantity)
            .addValue("unitCost", unitCost)
            .addValue("discount", discount)
            .addValue("tax", tax)
            .addValue("lineTotal", lineTotal));
    }

    private Purchase withItems(Purchase header) {
        List<PurchaseItem> items = jdbc.query("SELECT * FROM purchasing.purchase_items WHERE purchase_id = :id ORDER BY purchase_item_id", new MapSqlParameterSource("id", header.purchaseId()), this::mapItem);
        return new Purchase(header.purchaseId(), header.branchId(), header.warehouseId(), header.supplierId(), header.supplierDocument(), header.status(), header.paymentStatus(), header.currencyCode(), header.subtotal(), header.discountTotal(), header.taxTotal(), header.total(), header.idempotencyKey(), header.purchasedAt(), header.confirmedAt(), header.createdAt(), items);
    }

    private WarehouseRow findWarehouse(UUID warehouseId) {
        try {
            return jdbc.queryForObject("SELECT warehouse_id, branch_id FROM organization.warehouses WHERE warehouse_id = :id AND status = 'ACTIVE'", new MapSqlParameterSource("id", warehouseId), (rs, rowNum) -> new WarehouseRow(rs.getObject("warehouse_id", UUID.class), rs.getObject("branch_id", UUID.class)));
        } catch (EmptyResultDataAccessException exception) {
            throw new PurchasingException("No existe un almacen activo con id " + warehouseId);
        }
    }

    private PresentationRow findPresentation(UUID presentationId) {
        try {
            return jdbc.queryForObject("""
                SELECT pp.sku, pp.name AS presentation_name, p.name AS product_name
                FROM catalog.product_presentations pp
                JOIN catalog.products p ON p.product_id = pp.product_id
                WHERE pp.product_presentation_id = :id AND pp.status = 'ACTIVE' AND p.status = 'ACTIVE'
                """, new MapSqlParameterSource("id", presentationId), (rs, rowNum) -> new PresentationRow(rs.getString("sku"), rs.getString("product_name") + " - " + rs.getString("presentation_name")));
        } catch (EmptyResultDataAccessException exception) {
            throw new PurchasingException("No existe una presentacion activa con id " + presentationId);
        }
    }

    private Purchase mapPurchaseWithoutItems(ResultSet rs, int rowNum) throws SQLException {
        return new Purchase(
            rs.getObject("purchase_id", UUID.class),
            rs.getObject("branch_id", UUID.class),
            rs.getObject("warehouse_id", UUID.class),
            rs.getObject("supplier_id", UUID.class),
            rs.getString("supplier_document"),
            rs.getString("status"),
            rs.getString("payment_status"),
            rs.getString("currency_code"),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("discount_total"),
            rs.getBigDecimal("tax_total"),
            rs.getBigDecimal("total"),
            rs.getObject("idempotency_key", UUID.class),
            rs.getTimestamp("purchased_at").toInstant(),
            rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toInstant(),
            rs.getTimestamp("created_at").toInstant(),
            List.of()
        );
    }

    private PurchaseItem mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new PurchaseItem(
            rs.getObject("purchase_item_id", UUID.class),
            rs.getObject("purchase_id", UUID.class),
            rs.getObject("product_presentation_id", UUID.class),
            rs.getObject("lot_id", UUID.class),
            rs.getString("product_name_snapshot"),
            rs.getString("sku_snapshot"),
            rs.getBigDecimal("quantity"),
            rs.getBigDecimal("received_quantity"),
            rs.getBigDecimal("unit_cost"),
            rs.getBigDecimal("discount_amount"),
            rs.getBigDecimal("tax_amount"),
            rs.getBigDecimal("line_total")
        );
    }

    private Totals totals(List<CreatePurchaseItemCommand> items) {
        BigDecimal subtotal = ZERO;
        BigDecimal discount = ZERO;
        BigDecimal tax = ZERO;
        for (CreatePurchaseItemCommand item : items) {
            subtotal = subtotal.add(scale3(item.quantity()).multiply(money(item.unitCost())).setScale(4, RoundingMode.HALF_UP));
            discount = discount.add(money(item.discountAmount()));
            tax = tax.add(money(item.taxAmount()));
        }
        return new Totals(subtotal, discount, tax, subtotal.subtract(discount).add(tax));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale3(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeCurrency(String value) {
        return value == null || value.isBlank() ? "MXN" : value.trim().toUpperCase();
    }

    private record WarehouseRow(UUID warehouseId, UUID branchId) {
    }

    private record PresentationRow(String sku, String productName) {
    }

    private record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total) {
    }
}
