package com.odcc.tienda.modules.sync.adapter.in.rest.mapper;

import com.odcc.tienda.modules.sync.adapter.in.rest.request.SyncRequests.IngestOperationRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SyncRestMapperTest {

    private final SyncRestMapper mapper = Mappers.getMapper(SyncRestMapper.class);

    @Test
    void preservesEnvelopeAndPayloadAndAddsAuthenticatedActor() {
        UUID operationId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
            "branchId", UUID.randomUUID().toString(),
            "items", Map.of("count", 1)
        );
        var request = new IngestOperationRequest(
            operationId,
            deviceId,
            7,
            UUID.randomUUID(),
            "CART_UPSERT",
            "CART",
            aggregateId,
            payload,
            Instant.parse("2026-08-24T01:00:00Z")
        );

        var command = mapper.toIngestCommand(request, actorId);

        assertThat(command.operationId()).isEqualTo(operationId);
        assertThat(command.deviceId()).isEqualTo(deviceId);
        assertThat(command.deviceSequence()).isEqualTo(7);
        assertThat(command.aggregateId()).isEqualTo(aggregateId);
        assertThat(command.payload()).isEqualTo(payload);
        assertThat(command.actorUserId()).isEqualTo(actorId);
    }
}
