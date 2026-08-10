package com.odcc.tienda.modules.cash.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.cash.application.command.CloseCashSessionCommand;
import com.odcc.tienda.modules.cash.application.command.CreateCashMovementCommand;
import com.odcc.tienda.modules.cash.application.command.OpenCashSessionCommand;
import com.odcc.tienda.modules.cash.application.exception.CashException;
import com.odcc.tienda.modules.cash.application.exception.CashSessionAlreadyOpenException;
import com.odcc.tienda.modules.cash.application.exception.CashSessionAlreadyClosedException;
import com.odcc.tienda.modules.cash.application.model.CashMovement;
import com.odcc.tienda.modules.cash.application.model.CashSession;
import com.odcc.tienda.modules.cash.application.port.out.CashSessionRepositoryPort;
import com.odcc.tienda.modules.cash.application.query.ListCashSessionsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcCashSessionRepositoryAdapter implements CashSessionRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public CashSession open(OpenCashSessionCommand command) {
        ensureActiveCashRegister(command.cashRegisterId());
        ensureNoOpenSession(command.cashRegisterId());
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                INSERT INTO cash.cash_sessions (
                    cash_session_id, cash_register_id, opened_by, status, opening_amount, notes
                ) VALUES (
                    :id, :cashRegisterId, :openedBy, 'OPEN', :openingAmount, :notes
                )
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("cashRegisterId", command.cashRegisterId())
                .addValue("openedBy", command.openedBy())
                .addValue("openingAmount", command.openingAmount())
                .addValue("notes", command.notes()));
        } catch (DuplicateKeyException exception) {
            throw new CashSessionAlreadyOpenException(command.cashRegisterId());
        }
        if (command.openingAmount().compareTo(BigDecimal.ZERO) > 0) {
            insertMovement(id, "OPENING", "IN", command.openingAmount(), null, "Apertura de caja", command.notes(), command.openedBy());
        }
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<CashSession> findById(UUID cashSessionId) {
        try {
            return Optional.of(jdbc.queryForObject(baseSelect() + " WHERE cs.cash_session_id = :id", new MapSqlParameterSource("id", cashSessionId), this::mapSession));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<CashSession> findAll(ListCashSessionsQuery query) {
        return jdbc.query(baseSelect() + """
            WHERE (CAST(:cashRegisterId AS uuid) IS NULL OR cs.cash_register_id = CAST(:cashRegisterId AS uuid))
              AND (CAST(:status AS text) IS NULL OR cs.status = CAST(:status AS text))
            ORDER BY cs.opened_at DESC
            LIMIT 200
            """, new MapSqlParameterSource()
            .addValue("cashRegisterId", query == null ? null : query.cashRegisterId())
            .addValue("status", query == null ? null : normalize(query.status())), this::mapSession);
    }

    @Override
    public CashSession close(CloseCashSessionCommand command) {
        CashSession current = findByIdForUpdate(command.cashSessionId());
        if (!"OPEN".equals(current.status())) throw new CashSessionAlreadyClosedException(command.cashSessionId());
        BigDecimal expected = expectedAmount(command.cashSessionId());
        BigDecimal difference = command.countedCashAmount().subtract(expected);
        int updated = jdbc.update("""
            UPDATE cash.cash_sessions
            SET status = 'CLOSED',
                closed_by = :closedBy,
                expected_amount = :expectedAmount,
                counted_amount = :countedAmount,
                difference_amount = :differenceAmount,
                closed_at = clock_timestamp(),
                notes = COALESCE(:notes, notes)
            WHERE cash_session_id = :id
              AND status = 'OPEN'
            """, new MapSqlParameterSource()
            .addValue("id", command.cashSessionId())
            .addValue("closedBy", command.closedBy())
            .addValue("expectedAmount", expected)
            .addValue("countedAmount", command.countedCashAmount())
            .addValue("differenceAmount", difference)
            .addValue("notes", command.notes()));
        if (updated != 1) throw new CashSessionAlreadyClosedException(command.cashSessionId());
        if (command.countedCashAmount().compareTo(BigDecimal.ZERO) > 0) {
            insertMovement(command.cashSessionId(), "CLOSING", "OUT", command.countedCashAmount(), null, "Cierre de caja", command.notes(), command.closedBy());
        }
        return findById(command.cashSessionId()).orElseThrow();
    }

    @Override
    public List<CashMovement> findMovements(UUID cashSessionId) {
        return jdbc.query("SELECT * FROM cash.cash_movements WHERE cash_session_id = :id ORDER BY created_at", new MapSqlParameterSource("id", cashSessionId), this::mapMovement);
    }

    @Override
    public CashMovement createManualMovement(CreateCashMovementCommand command) {
        CashSession session = findByIdForUpdate(command.cashSessionId());
        if (!"OPEN".equals(session.status())) throw new CashException("Solo se pueden registrar movimientos en sesiones de caja abiertas");
        UUID movementId = insertMovement(command.cashSessionId(), normalize(command.movementType()), normalize(command.direction()), command.amount(), null, command.reference(), command.reason(), command.createdBy());
        return findMovement(movementId);
    }

    @Override
    public UUID findBranchIdByCashRegisterId(UUID cashRegisterId) {
        try {
            return jdbc.queryForObject(
                "SELECT branch_id FROM organization.cash_registers WHERE cash_register_id = :id AND status = 'ACTIVE'",
                new MapSqlParameterSource("id", cashRegisterId),
                UUID.class
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new CashException("No existe una caja registradora activa con id " + cashRegisterId);
        }
    }

    private CashSession findByIdForUpdate(UUID cashSessionId) {
        try {
            return jdbc.queryForObject(
                baseSelect() + " WHERE cs.cash_session_id = :id FOR UPDATE OF cs",
                new MapSqlParameterSource("id", cashSessionId),
                this::mapSession
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new CashException("No existe la sesion de caja " + cashSessionId);
        }
    }

    private CashMovement findMovement(UUID movementId) {
        return jdbc.queryForObject("SELECT * FROM cash.cash_movements WHERE cash_movement_id = :id", new MapSqlParameterSource("id", movementId), this::mapMovement);
    }

    private void ensureActiveCashRegister(UUID cashRegisterId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM organization.cash_registers WHERE cash_register_id = :id AND status = 'ACTIVE'", new MapSqlParameterSource("id", cashRegisterId), Integer.class);
        if (count == null || count == 0) throw new CashException("No existe una caja registradora activa con id " + cashRegisterId);
    }

    private void ensureNoOpenSession(UUID cashRegisterId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM cash.cash_sessions WHERE cash_register_id = :cashRegisterId AND status = 'OPEN'", new MapSqlParameterSource("cashRegisterId", cashRegisterId), Integer.class);
        if (count != null && count > 0) throw new CashSessionAlreadyOpenException(cashRegisterId);
    }

    private BigDecimal expectedAmount(UUID cashSessionId) {
        return jdbc.queryForObject("""
            SELECT cs.opening_amount
                   + COALESCE(SUM(CASE WHEN TRIM(cm.direction) = 'IN' AND cm.movement_type <> 'OPENING' THEN cm.amount ELSE 0 END), 0)
                   - COALESCE(SUM(CASE WHEN TRIM(cm.direction) = 'OUT' AND cm.movement_type <> 'CLOSING' THEN cm.amount ELSE 0 END), 0) AS expected_amount
            FROM cash.cash_sessions cs
            LEFT JOIN cash.cash_movements cm ON cm.cash_session_id = cs.cash_session_id
            WHERE cs.cash_session_id = :id
            GROUP BY cs.cash_session_id, cs.opening_amount
            """, new MapSqlParameterSource("id", cashSessionId), BigDecimal.class);
    }

    private UUID insertMovement(UUID sessionId, String type, String direction, BigDecimal amount, UUID paymentId, String reference, String reason, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO cash.cash_movements (
                cash_movement_id, cash_session_id, movement_type, direction, amount, payment_id, reference, reason, created_by
            ) VALUES (
                :id, :sessionId, :type, :direction, :amount, :paymentId, :reference, :reason, :createdBy
            )
            """, new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("sessionId", sessionId)
            .addValue("type", type)
            .addValue("direction", direction)
            .addValue("amount", amount)
            .addValue("paymentId", paymentId)
            .addValue("reference", reference)
            .addValue("reason", reason)
            .addValue("createdBy", createdBy));
        return id;
    }

    private String baseSelect() {
        return """
            SELECT cs.*, cr.branch_id, cr.code AS cash_register_code, cr.name AS cash_register_name
            FROM cash.cash_sessions cs
            JOIN organization.cash_registers cr ON cr.cash_register_id = cs.cash_register_id
            """;
    }

    private CashSession mapSession(ResultSet rs, int rowNum) throws SQLException {
        return new CashSession(
            rs.getObject("cash_session_id", UUID.class),
            rs.getObject("cash_register_id", UUID.class),
            rs.getObject("branch_id", UUID.class),
            rs.getString("cash_register_code"),
            rs.getString("cash_register_name"),
            rs.getObject("opened_by", UUID.class),
            rs.getObject("closed_by", UUID.class),
            rs.getString("status"),
            rs.getBigDecimal("opening_amount"),
            rs.getBigDecimal("expected_amount"),
            rs.getBigDecimal("counted_amount"),
            rs.getBigDecimal("difference_amount"),
            rs.getTimestamp("opened_at").toInstant(),
            rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toInstant(),
            rs.getString("notes")
        );
    }

    private CashMovement mapMovement(ResultSet rs, int rowNum) throws SQLException {
        return new CashMovement(
            rs.getObject("cash_movement_id", UUID.class),
            rs.getObject("cash_session_id", UUID.class),
            rs.getString("movement_type"),
            trim(rs.getString("direction")),
            rs.getBigDecimal("amount"),
            rs.getObject("payment_id", UUID.class),
            rs.getString("reference"),
            rs.getString("reason"),
            rs.getObject("created_by", UUID.class),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
