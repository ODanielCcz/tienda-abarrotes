package com.odcc.tienda.modules.purchasing.adapter.in.rest.mapper;

import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.UpdateSupplierRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierRestMapperTest {

    private final SupplierRestMapper mapper = Mappers.getMapper(SupplierRestMapper.class);

    @Test
    void mapsSupplierIdFromRouteAndDataFromRequest() {
        UUID supplierId = UUID.randomUUID();
        var request = new UpdateSupplierRequest(
            "PROV-01",
            "Proveedor Uno SA de CV",
            "Proveedor Uno",
            "PUO260801ABC",
            "contacto@example.com",
            "5555555555",
            15
        );

        var command = mapper.toUpdateCommand(supplierId, request);

        assertThat(command.supplierId()).isEqualTo(supplierId);
        assertThat(command.supplierCode()).isEqualTo("PROV-01");
        assertThat(command.legalName()).isEqualTo("Proveedor Uno SA de CV");
        assertThat(command.creditDays()).isEqualTo(15);
    }
}
