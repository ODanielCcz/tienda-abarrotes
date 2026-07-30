package com.odcc.tienda.modules.sync.adapter.out.persistence.jdbc;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.odcc.tienda.modules.sync.application.command.SyncCommands.IngestOperationCommand;
import com.odcc.tienda.modules.sync.application.exception.SyncConflictException;
import com.odcc.tienda.modules.sync.application.exception.SyncException;
import com.odcc.tienda.modules.sync.application.exception.SyncNotFoundException;
import com.odcc.tienda.modules.sync.application.model.SyncModels.DeviceCheckpoint;
import com.odcc.tienda.modules.sync.application.model.SyncModels.DeviceContext;
import com.odcc.tienda.modules.sync.application.model.SyncModels.OutboxEvent;
import com.odcc.tienda.modules.sync.application.model.SyncModels.SyncConflict;
import com.odcc.tienda.modules.sync.application.model.SyncModels.SyncOperation;
import com.odcc.tienda.modules.sync.application.port.out.SyncRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcSyncRepositoryAdapter implements SyncRepositoryPort {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<DeviceContext> findDevice(UUID deviceId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT device_id, branch_id, warehouse_id, device_type, status
                FROM organization.devices WHERE device_id = :id
                """, params("id", deviceId), (rs, rowNum) -> new DeviceContext(
                uuid(rs, "device_id"), uuid(rs, "branch_id"), uuid(rs, "warehouse_id"),
                rs.getString("device_type"), rs.getString("status")
            )));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean userCanAccessBranch(UUID userId, UUID branchId) {
        Boolean allowed = jdbc.queryForObject("""
            SELECT EXISTS(
                SELECT 1
                FROM iam.users user_account
                WHERE user_account.user_id = :userId
                  AND user_account.status = 'ACTIVE'
                  AND (
                    EXISTS (
                        SELECT 1 FROM iam.user_roles ur
                        JOIN iam.roles role ON role.role_id = ur.role_id
                        WHERE ur.user_id = user_account.user_id
                          AND role.code = 'SYSTEM_ADMIN'
                          AND role.status = 'ACTIVE'
                    )
                    OR EXISTS (
                        SELECT 1 FROM iam.user_branch_access access
                        JOIN organization.branches branch ON branch.branch_id = access.branch_id
                        WHERE access.user_id = user_account.user_id
                          AND access.branch_id = :branchId
                          AND branch.status = 'ACTIVE'
                    )
                  )
            )
            """, new MapSqlParameterSource("userId", userId).addValue("branchId", branchId), Boolean.class);
        return Boolean.TRUE.equals(allowed);
    }

    @Override
    public Optional<SyncOperation> findByIdempotencyKey(UUID idempotencyKey) {
        return findOperationBy("idempotency_key = :value", idempotencyKey);
    }

    @Override
    public Optional<SyncOperation> findByDeviceSequence(UUID deviceId, long deviceSequence) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(operationSelect() + """
                WHERE device_id = :deviceId AND device_sequence = :sequence
                """, new MapSqlParameterSource("deviceId", deviceId).addValue("sequence", deviceSequence), this::mapOperation));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public SyncOperation insertReceived(IngestOperationCommand command, String requestHash) {
        try {
            jdbc.update("""
                INSERT INTO sync.inbox_operations (
                    operation_id, device_id, device_sequence, idempotency_key,
                    request_hash, operation_type, aggregate_type, aggregate_id,
                    payload, client_created_at, status
                ) VALUES (
                    :operationId, :deviceId, :deviceSequence, :idempotencyKey,
                    :requestHash, :operationType, :aggregateType, :aggregateId,
                    CAST(:payload AS jsonb), :clientCreatedAt, 'RECEIVED'
                )
                """, new MapSqlParameterSource()
                .addValue("operationId", command.operationId())
                .addValue("deviceId", command.deviceId())
                .addValue("deviceSequence", command.deviceSequence())
                .addValue("idempotencyKey", command.idempotencyKey())
                .addValue("requestHash", requestHash)
                .addValue("operationType", command.operationType().trim().toUpperCase())
                .addValue("aggregateType", command.aggregateType().trim().toUpperCase())
                .addValue("aggregateId", command.aggregateId())
                .addValue("payload", json(command.payload()))
                .addValue("clientCreatedAt", Timestamp.from(command.clientCreatedAt())));
            return findOperation(command.operationId()).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new SyncConflictException("La operacion, secuencia o llave de idempotencia ya existe");
        }
    }

    @Override
    public SyncOperation markAccepted(UUID operationId, UUID aggregateId, Map<String, Object> result) {
        updateOperation(operationId, "ACCEPTED", aggregateId, result, null, null);
        return findOperation(operationId).orElseThrow();
    }

    @Override
    public SyncOperation markRejected(UUID operationId, String errorCode, String errorMessage) {
        updateOperation(operationId, "REJECTED", null, null, errorCode, errorMessage);
        return findOperation(operationId).orElseThrow();
    }

    @Override
    public SyncOperation markConflict(UUID operationId, String errorCode, String errorMessage) {
        updateOperation(operationId, "CONFLICT", null, null, errorCode, errorMessage);
        return findOperation(operationId).orElseThrow();
    }

    @Override
    public DeviceCheckpoint getOrCreateCheckpoint(UUID deviceId) {
        jdbc.update("""
            INSERT INTO sync.device_checkpoints (device_id)
            VALUES (:deviceId)
            ON CONFLICT (device_id) DO NOTHING
            """, params("deviceId", deviceId));
        return findCheckpoint(deviceId);
    }

    @Override
    public DeviceCheckpoint advanceCheckpoint(UUID deviceId, long sequence) {
        jdbc.update("""
            UPDATE sync.device_checkpoints
            SET last_received_sequence = GREATEST(last_received_sequence, :sequence),
                last_processed_sequence = GREATEST(last_processed_sequence, :sequence),
                last_sync_at = clock_timestamp(),
                updated_at = clock_timestamp()
            WHERE device_id = :deviceId
            """, new MapSqlParameterSource("deviceId", deviceId).addValue("sequence", sequence));
        return findCheckpoint(deviceId);
    }

    @Override
    public DeviceCheckpoint acknowledgeOutbox(UUID deviceId, long sequence) {
        jdbc.update("""
            UPDATE sync.device_checkpoints
            SET last_acknowledged_outbox_sequence = GREATEST(last_acknowledged_outbox_sequence, :sequence),
                last_sync_at = clock_timestamp(),
                updated_at = clock_timestamp()
            WHERE device_id = :deviceId
            """, new MapSqlParameterSource("deviceId", deviceId).addValue("sequence", sequence));
        return findCheckpoint(deviceId);
    }

    @Override
    public long maxOutboxSequence(UUID branchId) {
        Long value = jdbc.queryForObject("""
            SELECT COALESCE(MAX(global_sequence), 0)
            FROM sync.outbox_events
            WHERE branch_id IS NULL OR branch_id = :branchId
            """, params("branchId", branchId), Long.class);
        return value == null ? 0 : value;
    }

    @Override
    public List<OutboxEvent> findOutbox(UUID branchId, long afterSequence, int limit) {
        return jdbc.query("""
            SELECT outbox_event_id, global_sequence, branch_id, warehouse_id,
                   aggregate_type, aggregate_id, event_type, payload,
                   correlation_id, created_at
            FROM sync.outbox_events
            WHERE global_sequence > :afterSequence
              AND (branch_id IS NULL OR branch_id = :branchId)
            ORDER BY global_sequence
            LIMIT :limit
            """, new MapSqlParameterSource("branchId", branchId)
            .addValue("afterSequence", afterSequence)
            .addValue("limit", limit), this::mapOutbox);
    }

    @Override
    public SyncConflict createConflict(
        UUID operationId,
        String conflictType,
        Map<String, Object> serverState,
        Map<String, Object> clientState
    ) {
        UUID conflictId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO sync.conflicts (
                conflict_id, operation_id, conflict_type, server_state, client_state
            ) VALUES (
                :conflictId, :operationId, :conflictType,
                CAST(:serverState AS jsonb), CAST(:clientState AS jsonb)
            )
            """, new MapSqlParameterSource("conflictId", conflictId)
            .addValue("operationId", operationId)
            .addValue("conflictType", conflictType)
            .addValue("serverState", json(serverState))
            .addValue("clientState", json(clientState)));
        return findConflict(conflictId).orElseThrow();
    }

    @Override
    public List<SyncConflict> findConflicts(UUID deviceId, Boolean resolved) {
        return jdbc.query(conflictSelect() + """
            WHERE operation.device_id = :deviceId
              AND (
                CAST(:resolved AS boolean) IS NULL
                OR (:resolved = TRUE AND conflict.resolved_at IS NOT NULL)
                OR (:resolved = FALSE AND conflict.resolved_at IS NULL)
              )
            ORDER BY conflict.created_at DESC
            """, new MapSqlParameterSource("deviceId", deviceId).addValue("resolved", resolved), this::mapConflict);
    }

    @Override
    public Optional<SyncConflict> findConflict(UUID conflictId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(conflictSelect() + """
                WHERE conflict.conflict_id = :id
                """, params("id", conflictId), this::mapConflict));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<SyncOperation> findOperation(UUID operationId) {
        return findOperationBy("operation_id = :value", operationId);
    }

    @Override
    public SyncConflict resolveConflict(UUID conflictId, String resolution, String notes, UUID resolvedBy) {
        int updated = jdbc.update("""
            UPDATE sync.conflicts
            SET resolution = :resolution,
                resolution_notes = :notes,
                resolved_by = :resolvedBy,
                resolved_at = clock_timestamp()
            WHERE conflict_id = :id AND resolved_at IS NULL
            """, new MapSqlParameterSource("id", conflictId)
            .addValue("resolution", resolution)
            .addValue("notes", notes)
            .addValue("resolvedBy", resolvedBy));
        if (updated != 1) throw new SyncConflictException("El conflicto ya fue resuelto");
        return findConflict(conflictId).orElseThrow();
    }

    private Optional<SyncOperation> findOperationBy(String predicate, Object value) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(operationSelect() + "WHERE " + predicate,
                params("value", value), this::mapOperation));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private DeviceCheckpoint findCheckpoint(UUID deviceId) {
        try {
            return jdbc.queryForObject("""
                SELECT device_id, last_received_sequence, last_processed_sequence,
                       last_acknowledged_outbox_sequence, last_sync_at, updated_at
                FROM sync.device_checkpoints WHERE device_id = :deviceId
                """, params("deviceId", deviceId), this::mapCheckpoint);
        } catch (EmptyResultDataAccessException exception) {
            throw new SyncNotFoundException("No se encontro el checkpoint del dispositivo");
        }
    }

    private void updateOperation(
        UUID operationId,
        String status,
        UUID aggregateId,
        Map<String, Object> result,
        String errorCode,
        String errorMessage
    ) {
        int updated = jdbc.update("""
            UPDATE sync.inbox_operations
            SET status = :status,
                aggregate_id = COALESCE(:aggregateId, aggregate_id),
                result = COALESCE(CAST(:result AS jsonb), result),
                error_code = :errorCode,
                error_message = :errorMessage,
                processed_at = clock_timestamp()
            WHERE operation_id = :id
            """, new MapSqlParameterSource("id", operationId)
            .addValue("status", status)
            .addValue("aggregateId", aggregateId)
            .addValue("result", result == null ? null : json(result))
            .addValue("errorCode", errorCode)
            .addValue("errorMessage", errorMessage));
        if (updated != 1) throw new SyncNotFoundException("No se encontro la operacion Sync");
    }

    private String operationSelect() {
        return """
            SELECT operation_id, device_id, device_sequence, idempotency_key,
                   request_hash, operation_type, aggregate_type, aggregate_id,
                   payload, client_created_at, received_at, processed_at,
                   status, result, error_code, error_message
            FROM sync.inbox_operations
            """;
    }

    private String conflictSelect() {
        return """
            SELECT conflict.conflict_id, conflict.operation_id,
                   operation.device_id, operation.device_sequence, operation.operation_type,
                   conflict.conflict_type, conflict.server_state, conflict.client_state,
                   conflict.resolution, conflict.resolution_notes, conflict.resolved_by,
                   conflict.created_at, conflict.resolved_at
            FROM sync.conflicts conflict
            JOIN sync.inbox_operations operation ON operation.operation_id = conflict.operation_id
            """;
    }

    private SyncOperation mapOperation(ResultSet rs, int rowNum) throws SQLException {
        return new SyncOperation(
            uuid(rs, "operation_id"), uuid(rs, "device_id"), rs.getLong("device_sequence"),
            uuid(rs, "idempotency_key"), rs.getString("request_hash"), rs.getString("operation_type"),
            rs.getString("aggregate_type"), uuid(rs, "aggregate_id"), map(rs.getString("payload")),
            instant(rs, "client_created_at"), instant(rs, "received_at"), instant(rs, "processed_at"),
            rs.getString("status"), map(rs.getString("result")), rs.getString("error_code"), rs.getString("error_message")
        );
    }

    private DeviceCheckpoint mapCheckpoint(ResultSet rs, int rowNum) throws SQLException {
        return new DeviceCheckpoint(
            uuid(rs, "device_id"), rs.getLong("last_received_sequence"),
            rs.getLong("last_processed_sequence"), rs.getLong("last_acknowledged_outbox_sequence"),
            instant(rs, "last_sync_at"), instant(rs, "updated_at")
        );
    }

    private OutboxEvent mapOutbox(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxEvent(
            uuid(rs, "outbox_event_id"), rs.getLong("global_sequence"), uuid(rs, "branch_id"),
            uuid(rs, "warehouse_id"), rs.getString("aggregate_type"), uuid(rs, "aggregate_id"),
            rs.getString("event_type"), map(rs.getString("payload")), uuid(rs, "correlation_id"),
            instant(rs, "created_at")
        );
    }

    private SyncConflict mapConflict(ResultSet rs, int rowNum) throws SQLException {
        return new SyncConflict(
            uuid(rs, "conflict_id"), uuid(rs, "operation_id"), uuid(rs, "device_id"),
            rs.getLong("device_sequence"), rs.getString("operation_type"), rs.getString("conflict_type"),
            map(rs.getString("server_state")), map(rs.getString("client_state")), rs.getString("resolution"),
            rs.getString("resolution_notes"), uuid(rs, "resolved_by"), instant(rs, "created_at"),
            instant(rs, "resolved_at")
        );
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JacksonException exception) {
            throw new SyncException("No se pudo serializar el payload Sync");
        }
    }

    private Map<String, Object> map(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JacksonException exception) {
            throw new SyncException("No se pudo leer el payload Sync almacenado");
        }
    }

    private MapSqlParameterSource params(String name, Object value) {
        return new MapSqlParameterSource(name, value);
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
