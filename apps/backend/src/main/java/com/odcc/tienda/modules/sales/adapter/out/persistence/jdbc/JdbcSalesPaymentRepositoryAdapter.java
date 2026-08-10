package com.odcc.tienda.modules.sales.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.sales.application.command.CreateSalesPaymentCommand;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.exception.SalesOrderNotFoundException;
import com.odcc.tienda.modules.sales.application.exception.SalesPaymentNotFoundException;
import com.odcc.tienda.modules.sales.application.exception.SalesPaymentOverpaidException;
import com.odcc.tienda.modules.sales.application.exception.SalesPaymentIdempotencyConflictException;
import com.odcc.tienda.modules.sales.application.model.SalesPayment;
import com.odcc.tienda.modules.sales.application.port.out.SalesPaymentRepositoryPort;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcSalesPaymentRepositoryAdapter implements SalesPaymentRepositoryPort {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Optional<SalesPayment> findByIdempotencyKey(UUID idempotencyKey, String fingerprint) {
        try {
            return Optional.of(jdbc.queryForObject(paymentSelect() + " WHERE p.idempotency_key = :idempotencyKey AND p.source_fingerprint = :fingerprint", new MapSqlParameterSource().addValue("idempotencyKey", idempotencyKey).addValue("fingerprint", fingerprint), this::mapPayment));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existsByIdempotencyKeyWithDifferentFingerprint(UUID idempotencyKey, String fingerprint) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sales.payments WHERE idempotency_key = :idempotencyKey AND COALESCE(source_fingerprint, '') <> :fingerprint", new MapSqlParameterSource().addValue("idempotencyKey", idempotencyKey).addValue("fingerprint", fingerprint), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public SalesPayment createCaptured(CreateSalesPaymentCommand command, String fingerprint) {
        OrderRow order = findOrderForUpdate(command.salesOrderId());
        if (!"CONFIRMED".equals(order.status())) throw new SalesException("Solo se pueden registrar pagos en ventas confirmadas");
        String method = normalize(command.paymentMethod(), "CASH");
        String currency = normalize(command.currencyCode(), order.currencyCode());
        if (!order.currencyCode().equals(currency)) throw new SalesException("La moneda del pago no coincide con la venta");
        BigDecimal paidBefore = paidAmount(command.salesOrderId());
        BigDecimal amount = money(command.amount());
        if (paidBefore.add(amount).compareTo(order.total()) > 0) throw new SalesPaymentOverpaidException();
        if ("CASH".equals(method)) ensureOpenCashSession(command.cashSessionId(), order.branchId());

        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();
        List<UUID> inserted = jdbc.query("""
            INSERT INTO sales.payments (
                payment_id, sales_order_id, payment_method, status, amount, provider_reference,
                idempotency_key, source_fingerprint, paid_at, created_at
            ) VALUES (
                :paymentId, :salesOrderId, :paymentMethod, 'CAPTURED', :amount, :reference,
                :idempotencyKey, :fingerprint, :paidAt, :createdAt
            )
            ON CONFLICT (idempotency_key) DO NOTHING
            RETURNING payment_id
            """, new MapSqlParameterSource()
            .addValue("paymentId", paymentId)
            .addValue("salesOrderId", command.salesOrderId())
            .addValue("paymentMethod", method)
            .addValue("amount", amount)
            .addValue("reference", command.reference())
            .addValue("idempotencyKey", command.idempotencyKey())
            .addValue("fingerprint", fingerprint)
            .addValue("paidAt", Timestamp.from(now))
            .addValue("createdAt", Timestamp.from(now)),
            (resultSet, rowNumber) -> resultSet.getObject("payment_id", UUID.class));

        if (inserted.isEmpty()) {
            Optional<SalesPayment> existing = findByIdempotencyKey(command.idempotencyKey(), fingerprint);
            if (existing.isPresent()) return existing.get();
            throw new SalesPaymentIdempotencyConflictException(command.idempotencyKey());
        }

        if ("CASH".equals(method)) insertCashMovement(command.cashSessionId(), "SALE", "IN", amount, paymentId, command.createdBy(), command.reference(), "Pago de venta en efectivo");
        updatePaymentStatus(command.salesOrderId(), paidBefore.add(amount), order.total());
        return findPayment(paymentId);
    }

    @Override
    public List<SalesPayment> findBySalesOrderId(UUID salesOrderId) {
        return jdbc.query(paymentSelect() + " WHERE p.sales_order_id = :salesOrderId ORDER BY p.created_at", new MapSqlParameterSource("salesOrderId", salesOrderId), this::mapPayment);
    }

    @Override
    public Optional<SalesPayment> findById(UUID paymentId) {
        try {
            return Optional.of(findPayment(paymentId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public SalesPayment cancel(UUID paymentId, UUID cancelledBy) {
        SalesPayment current = findPaymentForUpdate(paymentId);
        if (!"CAPTURED".equals(current.status())) throw new SalesException("Solo se pueden cancelar pagos capturados");
        OrderRow order = findOrderForUpdate(current.salesOrderId());
        int updated = jdbc.update("""
            UPDATE sales.payments
            SET status = 'CANCELLED'
            WHERE payment_id = :paymentId
              AND status = 'CAPTURED'
            """, new MapSqlParameterSource("paymentId", paymentId));
        if (updated != 1) throw new SalesException("El pago ya fue procesado");
        if ("CASH".equals(current.paymentMethod())) {
            if (current.cashSessionId() == null) throw new SalesException("El pago en efectivo no tiene sesion de caja asociada");
            ensureOpenCashSession(current.cashSessionId(), order.branchId());
            insertCashMovement(current.cashSessionId(), "REFUND", "OUT", money(current.amount()), paymentId, cancelledBy, current.reference(), "Cancelacion de pago en efectivo", "PAYMENT_CANCEL", paymentId);
        }
        updatePaymentStatusFromCapturedPayments(current.salesOrderId(), order.total());
        return findPayment(paymentId);
    }

    private OrderRow findOrder(UUID salesOrderId) {
        try {
            return jdbc.queryForObject("SELECT sales_order_id, branch_id, status, payment_status, currency_code, total FROM sales.sales_orders WHERE sales_order_id = :id", new MapSqlParameterSource("id", salesOrderId), (rs, rowNum) -> new OrderRow(rs.getObject("sales_order_id", UUID.class), rs.getObject("branch_id", UUID.class), rs.getString("status"), rs.getString("payment_status"), trim(rs.getString("currency_code")), rs.getBigDecimal("total")));
        } catch (EmptyResultDataAccessException exception) {
            throw new SalesOrderNotFoundException(salesOrderId);
        }
    }

    private OrderRow findOrderForUpdate(UUID salesOrderId) {
        try {
            return jdbc.queryForObject(
                "SELECT sales_order_id, branch_id, status, payment_status, currency_code, total FROM sales.sales_orders WHERE sales_order_id = :id FOR UPDATE",
                new MapSqlParameterSource("id", salesOrderId),
                (rs, rowNum) -> new OrderRow(rs.getObject("sales_order_id", UUID.class), rs.getObject("branch_id", UUID.class), rs.getString("status"), rs.getString("payment_status"), trim(rs.getString("currency_code")), rs.getBigDecimal("total"))
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new SalesOrderNotFoundException(salesOrderId);
        }
    }

    private SalesPayment findPaymentForUpdate(UUID paymentId) {
        try {
            return jdbc.queryForObject(
                paymentSelect() + " WHERE p.payment_id = :id FOR UPDATE OF p",
                new MapSqlParameterSource("id", paymentId),
                this::mapPayment
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new SalesPaymentNotFoundException(paymentId);
        }
    }

    private SalesPayment findPayment(UUID paymentId) {
        return jdbc.queryForObject(paymentSelect() + " WHERE p.payment_id = :id", new MapSqlParameterSource("id", paymentId), this::mapPayment);
    }

    private BigDecimal paidAmount(UUID salesOrderId) {
        BigDecimal value = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM sales.payments WHERE sales_order_id = :id AND status = 'CAPTURED'", new MapSqlParameterSource("id", salesOrderId), BigDecimal.class);
        return value == null ? ZERO : money(value);
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

    private void insertCashMovement(UUID cashSessionId, String movementType, String direction, BigDecimal amount, UUID paymentId, UUID createdBy, String reference, String reason) {
        insertCashMovement(cashSessionId, movementType, direction, amount, paymentId, createdBy, reference, reason, null, null);
    }

    private void insertCashMovement(UUID cashSessionId, String movementType, String direction, BigDecimal amount, UUID paymentId, UUID createdBy, String reference, String reason, String sourceType, UUID sourceId) {
        jdbc.update("""
            INSERT INTO cash.cash_movements (
                cash_movement_id, cash_session_id, movement_type, direction, amount,
                payment_id, reference, reason, created_by, source_type, source_id
            ) VALUES (
                :id, :cashSessionId, :movementType, :direction, :amount,
                :paymentId, :reference, :reason, :createdBy, :sourceType, :sourceId
            )
            """, new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("cashSessionId", cashSessionId)
            .addValue("movementType", movementType)
            .addValue("direction", direction)
            .addValue("amount", amount)
            .addValue("paymentId", paymentId)
            .addValue("reference", reference)
            .addValue("reason", reason)
            .addValue("createdBy", createdBy)
            .addValue("sourceType", sourceType)
            .addValue("sourceId", sourceId));
    }

    private void updatePaymentStatus(UUID salesOrderId, BigDecimal paid, BigDecimal total) {
        String status = paid.compareTo(total) >= 0 ? "PAID" : "PARTIAL";
        jdbc.update("UPDATE sales.sales_orders SET payment_status = :status WHERE sales_order_id = :id", new MapSqlParameterSource().addValue("status", status).addValue("id", salesOrderId));
    }

    private void updatePaymentStatusFromCapturedPayments(UUID salesOrderId, BigDecimal total) {
        BigDecimal paid = paidAmount(salesOrderId);
        String status = paid.compareTo(BigDecimal.ZERO) == 0 ? "PENDING" : paid.compareTo(total) >= 0 ? "PAID" : "PARTIAL";
        jdbc.update("UPDATE sales.sales_orders SET payment_status = :status WHERE sales_order_id = :id", new MapSqlParameterSource().addValue("status", status).addValue("id", salesOrderId));
    }

    private String paymentSelect() {
        return """
            SELECT p.*, so.currency_code, cm.cash_session_id
            FROM sales.payments p
            JOIN sales.sales_orders so ON so.sales_order_id = p.sales_order_id
            LEFT JOIN cash.cash_movements cm
              ON cm.payment_id = p.payment_id
             AND cm.movement_type = 'SALE'
            """;
    }

    private SalesPayment mapPayment(ResultSet rs, int rowNum) throws SQLException {
        return new SalesPayment(
            rs.getObject("payment_id", UUID.class),
            rs.getObject("sales_order_id", UUID.class),
            rs.getObject("cash_session_id", UUID.class),
            rs.getString("payment_method"),
            rs.getString("status"),
            rs.getBigDecimal("amount"),
            trim(rs.getString("currency_code")),
            rs.getString("provider_reference"),
            rs.getObject("idempotency_key", UUID.class),
            rs.getTimestamp("paid_at") == null ? null : rs.getTimestamp("paid_at").toInstant(),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private static String normalize(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private record OrderRow(UUID salesOrderId, UUID branchId, String status, String paymentStatus, String currencyCode, BigDecimal total) {}
}
