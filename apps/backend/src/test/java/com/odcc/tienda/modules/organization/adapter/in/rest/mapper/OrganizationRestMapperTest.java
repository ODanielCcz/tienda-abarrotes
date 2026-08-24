package com.odcc.tienda.modules.organization.adapter.in.rest.mapper;

import com.odcc.tienda.modules.organization.adapter.in.rest.request.BranchRequests.UpdateBranchRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationRestMapperTest {

    private final OrganizationRestMapper mapper = Mappers.getMapper(OrganizationRestMapper.class);

    @Test
    void mapsBranchIdFromRouteAndBranchDataFromRequest() {
        UUID branchId = UUID.randomUUID();
        var request = new UpdateBranchRequest(
            "SUC-CENTRO",
            "Sucursal Centro",
            "Tienda Centro SA de CV",
            "America/Mexico_City",
            "MXN"
        );

        var command = mapper.toUpdateBranchCommand(branchId, request);

        assertThat(command.branchId()).isEqualTo(branchId);
        assertThat(command.code()).isEqualTo("SUC-CENTRO");
        assertThat(command.timezone()).isEqualTo("America/Mexico_City");
        assertThat(command.currencyCode()).isEqualTo("MXN");
    }
}
