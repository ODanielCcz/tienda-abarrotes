package com.odcc.tienda.modules.billing.adapter.in.rest.mapper;

import com.odcc.tienda.modules.billing.adapter.in.rest.request.BillingRequests.IssuerProfileRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BillingRestMapperTest {

    private final BillingRestMapper mapper = Mappers.getMapper(BillingRestMapper.class);

    @Test
    void mapsIssuerProfileIdFromRouteAndFiscalDataFromRequest() {
        UUID issuerProfileId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        var request = new IssuerProfileRequest(
            branchId,
            "AAA010101AAA",
            "Emisor de Prueba SA de CV",
            "06000",
            "601",
            "A"
        );

        var command = mapper.toUpdateIssuerCommand(issuerProfileId, request);

        assertThat(command.issuerProfileId()).isEqualTo(issuerProfileId);
        assertThat(command.branchId()).isEqualTo(branchId);
        assertThat(command.rfc()).isEqualTo("AAA010101AAA");
        assertThat(command.postalCode()).isEqualTo("06000");
    }
}
