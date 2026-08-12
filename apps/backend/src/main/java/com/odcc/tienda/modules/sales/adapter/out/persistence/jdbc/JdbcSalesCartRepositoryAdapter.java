package com.odcc.tienda.modules.sales.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.sales.application.command.UpsertSalesCartCommand;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.model.SalesCart;
import com.odcc.tienda.modules.sales.application.model.SalesCart.SalesCartItem;
import com.odcc.tienda.modules.sales.application.port.out.SalesCartRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcSalesCartRepositoryAdapter implements SalesCartRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public boolean branchIsActive(UUID branchId) {
        return exists("SELECT EXISTS(SELECT 1 FROM organization.branches WHERE branch_id = :id AND status = 'ACTIVE')", branchId);
    }

    @Override
    public boolean customerIsActive(UUID customerId) {
        return exists("SELECT EXISTS(SELECT 1 FROM sales.customers WHERE customer_id = :id AND status = 'ACTIVE')", customerId);
    }

    @Override
    public boolean presentationIsActive(UUID productPresentationId) {
        return exists("SELECT EXISTS(SELECT 1 FROM catalog.product_presentations WHERE product_presentation_id = :id AND status = 'ACTIVE')", productPresentationId);
    }

    @Override
    public SalesCart upsert(UpsertSalesCartCommand command) {
        try {
            int claimed = jdbc.update("""
                INSERT INTO sales.carts (
                    cart_id, customer_id, branch_id, device_id, status,
                    currency_code, expires_at
                ) VALUES (
                    :id, :customerId, :branchId, :deviceId, 'ACTIVE',
                    :currencyCode, :expiresAt
                )
                ON CONFLICT (cart_id) DO UPDATE
                SET customer_id = EXCLUDED.customer_id,
                    status = 'ACTIVE',
                    currency_code = EXCLUDED.currency_code,
                    expires_at = EXCLUDED.expires_at,
                    updated_at = clock_timestamp()
                WHERE sales.carts.branch_id = EXCLUDED.branch_id
                  AND sales.carts.device_id = EXCLUDED.device_id
                  AND sales.carts.status = 'ACTIVE'
                """, new MapSqlParameterSource("id", command.cartId())
                .addValue("customerId", command.customerId())
                .addValue("branchId", command.branchId())
                .addValue("deviceId", command.deviceId())
                .addValue("currencyCode", command.currencyCode())
                .addValue("expiresAt", timestamp(command.expiresAt())));

            if (claimed != 1) {
                throw new SalesException("El carrito no pertenece al dispositivo, sucursal o estado editable");
            }

            jdbc.update("DELETE FROM sales.cart_items WHERE cart_id = :id", new MapSqlParameterSource("id", command.cartId()));
            for (UpsertSalesCartCommand.Item item : command.items()) {
                jdbc.update("""
                    INSERT INTO sales.cart_items (
                        cart_item_id, cart_id, product_presentation_id,
                        quantity, unit_price_snapshot
                    ) VALUES (
                        :itemId, :cartId, :presentationId, :quantity, :unitPrice
                    )
                    """, new MapSqlParameterSource("itemId", UUID.randomUUID())
                    .addValue("cartId", command.cartId())
                    .addValue("presentationId", item.productPresentationId())
                    .addValue("quantity", item.quantity())
                    .addValue("unitPrice", item.unitPriceSnapshot()));
            }
            return find(command.cartId());
        } catch (DataIntegrityViolationException exception) {
            throw new SalesException("No se pudo guardar el carrito por una restriccion de integridad");
        }
    }

    private SalesCart find(UUID cartId) {
        SalesCart cart = jdbc.queryForObject("""
            SELECT cart_id, customer_id, branch_id, device_id, status,
                   currency_code, created_at, updated_at, expires_at
            FROM sales.carts WHERE cart_id = :id
            """, new MapSqlParameterSource("id", cartId), (rs, rowNum) -> new SalesCart(
            uuid(rs, "cart_id"), uuid(rs, "customer_id"), uuid(rs, "branch_id"), uuid(rs, "device_id"),
            rs.getString("status"), rs.getString("currency_code").trim(), instant(rs, "created_at"),
            instant(rs, "updated_at"), instant(rs, "expires_at"), List.of()
        ));
        List<SalesCartItem> items = jdbc.query("""
            SELECT cart_item_id, product_presentation_id, quantity, unit_price_snapshot
            FROM sales.cart_items WHERE cart_id = :id ORDER BY cart_item_id
            """, new MapSqlParameterSource("id", cartId), (rs, rowNum) -> new SalesCartItem(
            uuid(rs, "cart_item_id"), uuid(rs, "product_presentation_id"),
            rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price_snapshot")
        ));
        return new SalesCart(cart.cartId(), cart.customerId(), cart.branchId(), cart.deviceId(), cart.status(),
            cart.currencyCode(), cart.createdAt(), cart.updatedAt(), cart.expiresAt(), items);
    }

    private boolean exists(String sql, UUID id) {
        Boolean value = jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
