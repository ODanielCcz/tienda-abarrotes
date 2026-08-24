package com.odcc.tienda.modules.inventory.adapter.in.rest.mapper;

import com.odcc.tienda.modules.inventory.adapter.in.rest.request.CreateInventoryAdjustmentRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.InventoryAdjustmentItemRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdvancedInventoryRestMapperTest {

    private final AdvancedInventoryRestMapper mapper =
        Mappers.getMapper(AdvancedInventoryRestMapper.class);

    @Test
    void mapsAdjustmentRequestAndAuthenticatedActor() {
        UUID warehouseId = UUID.randomUUID();
        UUID presentationId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var request = new CreateInventoryAdjustmentRequest(
            warehouseId,
            "Conteo de control",
            List.of(new InventoryAdjustmentItemRequest(
                presentationId,
                lotId,
                "IN",
                new BigDecimal("4.500"),
                new BigDecimal("18.25")
            ))
        );

        var command = mapper.toAdjustmentCommand(request, actorId);

        assertThat(command.warehouseId()).isEqualTo(warehouseId);
        assertThat(command.reason()).isEqualTo("Conteo de control");
        assertThat(command.createdBy()).isEqualTo(actorId);
        assertThat(command.items()).singleElement().satisfies(item -> {
            assertThat(item.productPresentationId()).isEqualTo(presentationId);
            assertThat(item.lotId()).isEqualTo(lotId);
            assertThat(item.direction()).isEqualTo("IN");
            assertThat(item.quantity()).isEqualByComparingTo("4.500");
            assertThat(item.unitCost()).isEqualByComparingTo("18.25");
        });
    }
}
