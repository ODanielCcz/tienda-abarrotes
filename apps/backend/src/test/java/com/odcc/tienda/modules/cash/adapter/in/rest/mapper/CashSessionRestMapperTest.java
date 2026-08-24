package com.odcc.tienda.modules.cash.adapter.in.rest.mapper;

import com.odcc.tienda.modules.cash.adapter.in.rest.request.CloseCashSessionRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CashSessionRestMapperTest {

    private final CashSessionRestMapper mapper = Mappers.getMapper(CashSessionRestMapper.class);

    @Test
    void mapsSessionAndActorFromExplicitContext() {
        UUID cashSessionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var request = new CloseCashSessionRequest(new BigDecimal("1250.00"), "Cierre correcto");

        var command = mapper.toCloseCommand(cashSessionId, request, actorId);

        assertThat(command.cashSessionId()).isEqualTo(cashSessionId);
        assertThat(command.closedBy()).isEqualTo(actorId);
        assertThat(command.countedCashAmount()).isEqualByComparingTo("1250.00");
    }
}
