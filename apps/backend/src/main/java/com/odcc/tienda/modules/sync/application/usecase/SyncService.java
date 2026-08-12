package com.odcc.tienda.modules.sync.application.usecase;

import com.odcc.tienda.modules.inventory.application.command.CreateInventoryCountCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryCountItemCommand;
import com.odcc.tienda.modules.inventory.application.model.InventoryCountView;
import com.odcc.tienda.modules.inventory.application.port.in.AdvancedInventoryUseCases;
import com.odcc.tienda.modules.sales.application.command.UpsertSalesCartCommand;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.model.SalesCart;
import com.odcc.tienda.modules.sales.application.port.in.SalesCartUseCases;
import com.odcc.tienda.modules.sync.application.command.SyncCommands.AcknowledgeCheckpointCommand;
import com.odcc.tienda.modules.sync.application.command.SyncCommands.IngestOperationCommand;
import com.odcc.tienda.modules.sync.application.command.SyncCommands.ResolveConflictCommand;
import com.odcc.tienda.modules.sync.application.exception.SyncConflictException;
import com.odcc.tienda.modules.sync.application.exception.SyncException;
import com.odcc.tienda.modules.sync.application.exception.SyncNotFoundException;
import com.odcc.tienda.modules.sync.application.model.SyncModels.DeviceCheckpoint;
import com.odcc.tienda.modules.sync.application.model.SyncModels.DeviceContext;
import com.odcc.tienda.modules.sync.application.model.SyncModels.OutboxBatch;
import com.odcc.tienda.modules.sync.application.model.SyncModels.SyncConflict;
import com.odcc.tienda.modules.sync.application.model.SyncModels.SyncOperation;
import com.odcc.tienda.modules.sync.application.port.in.SyncUseCases;
import com.odcc.tienda.modules.sync.application.port.out.RequestFingerprintPort;
import com.odcc.tienda.modules.sync.application.port.out.SyncRepositoryPort;
import com.odcc.tienda.modules.sync.application.port.out.SyncRateLimitPort;
import com.odcc.tienda.modules.sync.application.exception.SyncPayloadInvalidException;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public final class SyncService implements SyncUseCases {

    private static final String INVENTORY_COUNT_CREATE = "INVENTORY_COUNT_CREATE";
    private static final String CART_UPSERT = "CART_UPSERT";
    private static final List<String> RESOLUTIONS = List.of("SERVER_WINS", "CLIENT_WINS", "MERGED", "REJECTED");

    private final SyncRepositoryPort repository;
    private final RequestFingerprintPort fingerprintPort;
    private final AdvancedInventoryUseCases inventoryUseCases;
    private final SalesCartUseCases salesCartUseCases;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;
    private final SyncRateLimitPort rateLimitPort;

    @Override
    public SyncOperation ingest(IngestOperationCommand command) {
        validateEnvelope(command);
        rateLimitPort.check(command.deviceId());
        DeviceContext device = requireAuthorizedDevice(command.deviceId(), command.actorUserId());
        String requestHash = fingerprint(command);
        return transactionRunner.required(() -> ingest(command, device, requestHash));
    }

    @Override
    public OutboxBatch getOutbox(UUID deviceId, Long afterSequence, int limit, UUID actorUserId) {
        DeviceContext device = requireAuthorizedDevice(deviceId, actorUserId);
        int boundedLimit = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500));
        DeviceCheckpoint checkpoint = repository.getOrCreateCheckpoint(deviceId);
        long cursor = afterSequence == null ? checkpoint.lastAcknowledgedOutboxSequence() : Math.max(0, afterSequence);
        var events = repository.findOutbox(device.branchId(), cursor, boundedLimit);
        long next = events.isEmpty() ? cursor : events.get(events.size() - 1).globalSequence();
        return new OutboxBatch(deviceId, cursor, next, events);
    }

    @Override
    public DeviceCheckpoint getCheckpoint(UUID deviceId, UUID actorUserId) {
        requireAuthorizedDevice(deviceId, actorUserId);
        return repository.getOrCreateCheckpoint(deviceId);
    }

    @Override
    public DeviceCheckpoint acknowledge(AcknowledgeCheckpointCommand command) {
        if (command == null || command.outboxSequence() < 0) throw new SyncException("El cursor de outbox es invalido");
        DeviceContext device = requireAuthorizedDevice(command.deviceId(), command.actorUserId());
        long maximum = repository.maxOutboxSequence(device.branchId());
        if (command.outboxSequence() > maximum) {
            throw new SyncConflictException("No se puede confirmar un evento de outbox que aun no existe");
        }
        return transactionRunner.required(() -> repository.acknowledgeOutbox(command.deviceId(), command.outboxSequence()));
    }

    @Override
    public List<SyncConflict> listConflicts(UUID deviceId, Boolean resolved, UUID actorUserId) {
        requireAuthorizedDevice(deviceId, actorUserId);
        return repository.findConflicts(deviceId, resolved);
    }

    @Override
    public SyncConflict resolveConflict(ResolveConflictCommand command) {
        if (command == null || command.conflictId() == null) throw new SyncException("El conflicto es obligatorio");
        String resolution = normalize(command.resolution());
        if (!RESOLUTIONS.contains(resolution)) throw new SyncException("La resolucion del conflicto es invalida");
        SyncConflict conflict = repository.findConflict(command.conflictId())
            .orElseThrow(() -> new SyncNotFoundException("No se encontro el conflicto " + command.conflictId()));
        if (conflict.resolvedAt() != null) throw new SyncConflictException("El conflicto ya fue resuelto");
        SyncOperation operation = repository.findOperation(conflict.operationId())
            .orElseThrow(() -> new SyncNotFoundException("No se encontro la operacion del conflicto"));
        DeviceContext device = requireAuthorizedDevice(operation.deviceId(), command.actorUserId());
        DeviceCheckpoint checkpoint = repository.getOrCreateCheckpoint(device.deviceId());
        if (operation.deviceSequence() != checkpoint.lastReceivedSequence() + 1) {
            throw new SyncConflictException("Primero deben resolverse las secuencias anteriores del dispositivo");
        }
        validateResolutionForOperation(operation.operationType(), resolution);
        return transactionRunner.required(() -> {
            if ("CLIENT_WINS".equals(resolution) || "MERGED".equals(resolution)) {
                Map<String, Object> payload = "MERGED".equals(resolution)
                    ? requireMergedPayload(command.mergedPayload())
                    : operation.payload();
                Map<String, Object> result = process(operation.operationType(), operation.aggregateId(), payload, device, command.actorUserId());
                repository.markAccepted(operation.operationId(), resultAggregateId(result), result);
            } else {
                repository.markRejected(operation.operationId(), "SYNC_CONFLICT_RESOLVED_" + resolution,
                    "La operacion fue resuelta con " + resolution);
            }
            repository.advanceCheckpoint(device.deviceId(), operation.deviceSequence());
            SyncConflict resolved = repository.resolveConflict(command.conflictId(), resolution, trimToNull(command.resolutionNotes()), command.actorUserId());
            audit("SYNC_CONFLICT_RESOLVED", "SYNC_CONFLICT", resolved.conflictId(),
                Map.of("resolution", "PENDING"), Map.of("resolution", resolution));
            return resolved;
        });
    }

    private SyncOperation ingest(IngestOperationCommand command, DeviceContext device, String requestHash) {
        var existingByKey = repository.findByIdempotencyKey(command.idempotencyKey());
        if (existingByKey.isPresent()) {
            if (!requestHash.equals(existingByKey.get().requestHash())) {
                throw new SyncConflictException("La llave de idempotencia ya fue usada con contenido diferente");
            }
            return existingByKey.get();
        }
        var existingBySequence = repository.findByDeviceSequence(command.deviceId(), command.deviceSequence());
        if (existingBySequence.isPresent()) {
            if (requestHash.equals(existingBySequence.get().requestHash())) return existingBySequence.get();
            throw new SyncConflictException("La secuencia del dispositivo ya fue usada por otra operacion");
        }

        DeviceCheckpoint checkpoint = repository.getOrCreateCheckpoint(command.deviceId());
        long expected = checkpoint.lastReceivedSequence() + 1;
        SyncOperation received = repository.insertReceived(command, requestHash);
        if (command.deviceSequence() != expected) {
            String type = command.deviceSequence() > expected ? "SEQUENCE_GAP" : "STALE_SEQUENCE";
            SyncOperation conflicted = repository.markConflict(received.operationId(), type,
                "Se esperaba la secuencia " + expected + " y se recibio " + command.deviceSequence());
            repository.createConflict(received.operationId(), type,
                Map.of("expectedSequence", expected, "lastReceivedSequence", checkpoint.lastReceivedSequence()),
                Map.of("deviceSequence", command.deviceSequence(), "requestHash", requestHash));
            audit("SYNC_OPERATION_CONFLICT", "SYNC_OPERATION", received.operationId(), Map.of(),
                Map.of("status", "CONFLICT", "conflictType", type));
            return conflicted;
        }

        String operationType = normalize(command.operationType());
        if (!List.of(INVENTORY_COUNT_CREATE, CART_UPSERT).contains(operationType)) {
            SyncOperation rejected = repository.markRejected(received.operationId(), "SYNC_OPERATION_UNSUPPORTED",
                "El tipo de operacion no esta permitido en Sync v1");
            repository.advanceCheckpoint(command.deviceId(), command.deviceSequence());
            audit("SYNC_OPERATION_REJECTED", "SYNC_OPERATION", received.operationId(), Map.of(),
                Map.of("status", "REJECTED", "operationType", operationType));
            return rejected;
        }

        Map<String, Object> result = process(operationType, command.aggregateId(), command.payload(), device, command.actorUserId());
        SyncOperation accepted = repository.markAccepted(received.operationId(), resultAggregateId(result), result);
        repository.advanceCheckpoint(command.deviceId(), command.deviceSequence());
        audit("SYNC_OPERATION_ACCEPTED", "SYNC_OPERATION", received.operationId(), Map.of(),
            Map.of("status", "ACCEPTED", "operationType", operationType));
        return accepted;
    }

    private Map<String, Object> process(
        String operationType,
        UUID aggregateId,
        Map<String, Object> payload,
        DeviceContext device,
        UUID actorUserId
    ) {
        if (INVENTORY_COUNT_CREATE.equals(operationType)) {
            return processInventoryCount(payload, device, actorUserId);
        }
        if (CART_UPSERT.equals(operationType)) {
            return processCart(aggregateId, payload, device);
        }
        throw new SyncException("Operacion Sync no soportada");
    }

    private Map<String, Object> processInventoryCount(Map<String, Object> payload, DeviceContext device, UUID actorUserId) {
        UUID warehouseId = uuid(payload, "warehouseId", true);
        if (device.warehouseId() == null || !device.warehouseId().equals(warehouseId)) {
            throw new SyncException("El conteo debe pertenecer al almacen asignado al dispositivo");
        }
        List<Map<String, Object>> rawItems = mapList(payload, "items");
        List<InventoryCountItemCommand> items = rawItems.stream().map(item -> new InventoryCountItemCommand(
            uuid(item, "productPresentationId", true),
            uuid(item, "lotId", false),
            decimal(item, "countedQuantity")
        )).toList();
        InventoryCountView count = inventoryUseCases.createCount(new CreateInventoryCountCommand(warehouseId, actorUserId, items));
        return Map.of(
            "aggregateId", count.inventoryCountId(),
            "inventoryCountId", count.inventoryCountId(),
            "status", count.status()
        );
    }

    private Map<String, Object> processCart(UUID aggregateId, Map<String, Object> payload, DeviceContext device) {
        UUID cartId = aggregateId == null ? uuid(payload, "cartId", false) : aggregateId;
        UUID branchId = uuid(payload, "branchId", true);
        if (!device.branchId().equals(branchId)) throw new SyncException("El carrito no pertenece a la sucursal del dispositivo");
        List<UpsertSalesCartCommand.Item> items = mapList(payload, "items").stream().map(item ->
            new UpsertSalesCartCommand.Item(
                uuid(item, "productPresentationId", true),
                decimal(item, "quantity"),
                decimal(item, "unitPriceSnapshot")
            )).toList();
        SalesCart cart;
        try {
            cart = salesCartUseCases.upsert(new UpsertSalesCartCommand(
                cartId,
                uuid(payload, "customerId", false),
                branchId,
                device.deviceId(),
                string(payload, "currencyCode", false),
                instant(payload, "expiresAt"),
                items
            ));
        } catch (SalesException exception) {
            throw new SyncConflictException(exception.getMessage());
        }
        return Map.of(
            "aggregateId", cart.cartId(),
            "cartId", cart.cartId(),
            "status", cart.status(),
            "itemCount", cart.items().size()
        );
    }

    private DeviceContext requireAuthorizedDevice(UUID deviceId, UUID actorUserId) {
        if (deviceId == null) throw new SyncException("El dispositivo es obligatorio");
        if (actorUserId == null) throw new SyncException("El usuario autenticado es obligatorio");
        DeviceContext device = repository.findDevice(deviceId)
            .orElseThrow(() -> new SyncNotFoundException("No se encontro el dispositivo " + deviceId));
        if (!"ACTIVE".equals(device.status())) throw new SyncConflictException("El dispositivo no esta activo");
        if (!"MOBILE_EMPLOYEE".equals(device.deviceType())) {
            throw new SyncConflictException("Sync v1 solo permite dispositivos MOBILE_EMPLOYEE");
        }
        if (!repository.userOwnsDevice(actorUserId, device.deviceId())) {
            throw new SyncConflictException("El dispositivo no esta vinculado al usuario autenticado");
        }
        if (!repository.userCanAccessBranch(actorUserId, device.branchId())) {
            throw new SyncConflictException("El usuario no tiene acceso a la sucursal del dispositivo");
        }
        return device;
    }

    private static void validateEnvelope(IngestOperationCommand command) {
        if (command == null) throw new SyncException("La operacion Sync es obligatoria");
        if (command.operationId() == null) throw new SyncException("El operationId es obligatorio");
        if (command.deviceId() == null) throw new SyncException("El deviceId es obligatorio");
        if (command.deviceSequence() <= 0) throw new SyncException("La secuencia debe ser mayor a cero");
        if (command.idempotencyKey() == null) throw new SyncException("La llave de idempotencia es obligatoria");
        if (command.operationType() == null || command.operationType().isBlank()) throw new SyncException("El tipo de operacion es obligatorio");
        if (command.aggregateType() == null || command.aggregateType().isBlank()) throw new SyncException("El tipo de agregado es obligatorio");
        if (command.payload() == null) throw new SyncException("El payload es obligatorio");
        if (command.clientCreatedAt() == null) throw new SyncException("La fecha del cliente es obligatoria");
        if (command.actorUserId() == null) throw new SyncException("El usuario autenticado es obligatorio");
        String operationType = normalize(command.operationType());
        if (!List.of(INVENTORY_COUNT_CREATE, CART_UPSERT).contains(operationType)) {
            throw new SyncPayloadInvalidException("El tipo de operacion no esta permitido en Sync v1");
        }
        validatePayload(command.payload(), 1, new int[] {0});
    }

    private static void validatePayload(Object value, int depth, int[] nodes) {
        if (depth > 20) throw new SyncPayloadInvalidException("El payload supera la profundidad maxima de 20");
        if (++nodes[0] > 1_000) throw new SyncPayloadInvalidException("El payload supera el maximo de 1000 nodos");
        if (value instanceof String text && text.length() > 16 * 1024) {
            throw new SyncPayloadInvalidException("El payload contiene un texto mayor a 16 KiB");
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getKey().toString().length() > 16 * 1024) {
                    throw new SyncPayloadInvalidException("El payload contiene una clave mayor a 16 KiB");
                }
                validatePayload(entry.getValue(), depth + 1, nodes);
            }
        } else if (value instanceof List<?> list) {
            list.forEach(item -> validatePayload(item, depth + 1, nodes));
        }
    }

    private static void validateResolutionForOperation(String operationType, String resolution) {
        if (INVENTORY_COUNT_CREATE.equals(operationType) && !List.of("SERVER_WINS", "REJECTED").contains(resolution)) {
            throw new SyncConflictException("Los conteos solo permiten SERVER_WINS o REJECTED");
        }
        if (!CART_UPSERT.equals(operationType) && List.of("CLIENT_WINS", "MERGED").contains(resolution)) {
            throw new SyncConflictException("CLIENT_WINS y MERGED solo se permiten para carritos");
        }
    }

    private static Map<String, Object> requireMergedPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) throw new SyncException("MERGED requiere un payload combinado");
        validatePayload(payload, 1, new int[] {0});
        return payload;
    }

    private static UUID resultAggregateId(Map<String, Object> result) {
        Object value = result.get("aggregateId");
        if (value instanceof UUID id) return id;
        return value == null ? null : UUID.fromString(value.toString());
    }

    private String fingerprint(IngestOperationCommand command) {
        String canonical = command.operationId() + "|" + command.deviceId() + "|" + command.deviceSequence()
            + "|" + normalize(command.operationType()) + "|" + normalize(command.aggregateType())
            + "|" + Objects.toString(command.aggregateId(), "") + "|" + canonical(command.payload());
        return fingerprintPort.sha256(canonical);
    }

    private static String canonical(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .map(entry -> entry.getKey() + ":" + canonical(entry.getValue()))
                .reduce("{", (left, right) -> left + right + ",") + "}";
        }
        if (value instanceof List<?> list) {
            return list.stream().map(SyncService::canonical).reduce("[", (left, right) -> left + right + ",") + "]";
        }
        if (value instanceof BigDecimal decimal) return decimal.stripTrailingZeros().toPlainString();
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof List<?> list) || list.isEmpty()) throw new SyncException("El campo " + key + " debe contener elementos");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) throw new SyncException("Los elementos de " + key + " deben ser objetos");
            result.add((Map<String, Object>) map);
        }
        return result;
    }

    private static UUID uuid(Map<String, Object> payload, String key, boolean required) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) {
            if (required) throw new SyncException("El campo " + key + " es obligatorio");
            return null;
        }
        try {
            return value instanceof UUID id ? id : UUID.fromString(value.toString());
        } catch (IllegalArgumentException exception) {
            throw new SyncException("El campo " + key + " debe ser un UUID valido");
        }
    }

    private static BigDecimal decimal(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) throw new SyncException("El campo " + key + " es obligatorio");
        try {
            return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            throw new SyncException("El campo " + key + " debe ser numerico");
        }
    }

    private static String string(Map<String, Object> payload, String key, boolean required) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) {
            if (required) throw new SyncException("El campo " + key + " es obligatorio");
            return null;
        }
        return value.toString().trim();
    }

    private static Instant instant(Map<String, Object> payload, String key) {
        String value = string(payload, key, false);
        if (value == null) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new SyncException("El campo " + key + " debe ser una fecha ISO-8601 valida");
        }
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void audit(String eventType, String aggregateType, UUID aggregateId, Map<String, Object> before, Map<String, Object> after) {
        auditPort.record(new BusinessAuditEvent(eventType, aggregateType, aggregateId, before, after, Map.of()));
    }
}
