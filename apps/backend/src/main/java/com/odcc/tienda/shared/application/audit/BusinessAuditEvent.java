package com.odcc.tienda.shared.application.audit;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record BusinessAuditEvent(
    String eventType,
    String aggregateType,
    UUID aggregateId,
    Map<String, Object> beforeState,
    Map<String, Object> afterState,
    Map<String, Object> metadata
) {

    public BusinessAuditEvent {
        Objects.requireNonNull(eventType, "El tipo de evento es obligatorio");
        Objects.requireNonNull(aggregateType, "El tipo de agregado es obligatorio");
        Objects.requireNonNull(aggregateId, "El id del agregado es obligatorio");
        beforeState = immutable(beforeState);
        afterState = immutable(afterState);
        metadata = immutable(metadata);
    }

    private static Map<String, Object> immutable(Map<String, Object> values) {
        return values == null ? Map.of() : Map.copyOf(values);
    }
}
