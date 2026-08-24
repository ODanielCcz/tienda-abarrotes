package com.odcc.tienda.modules.inventory.adapter.in.rest.mapper;

import com.odcc.tienda.modules.inventory.adapter.in.rest.request.CreateInventoryReceiptRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.InventoryReceiptItemRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.InventoryReceiptPalletRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReceiptRestMapperTest {

    private final InventoryReceiptRestMapper mapper =
        Mappers.getMapper(InventoryReceiptRestMapper.class);

    @Test
    void mapsReceiptItemsAndPalletsAndDefaultsNullCollections() {
        UUID warehouseId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID presentationId = UUID.randomUUID();
        var item = new InventoryReceiptItemRequest(
            presentationId,
            "LOT-2026-08",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2027, 8, 1),
            new BigDecimal("10.000"),
            new BigDecimal("12.50")
        );
        var request = new CreateInventoryReceiptRequest(
            warehouseId,
            null,
            idempotencyKey,
            "Recepcion de prueba",
            null,
            List.of(new InventoryReceiptPalletRequest("SSCC-001", List.of(item)))
        );

        var command = mapper.toCommand(request);

        assertThat(command.warehouseId()).isEqualTo(warehouseId);
        assertThat(command.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(command.items()).isEmpty();
        assertThat(command.pallets()).singleElement().satisfies(pallet -> {
            assertThat(pallet.externalPalletCode()).isEqualTo("SSCC-001");
            assertThat(pallet.items()).singleElement().satisfies(mapped -> {
                assertThat(mapped.productPresentationId()).isEqualTo(presentationId);
                assertThat(mapped.lotNumber()).isEqualTo("LOT-2026-08");
                assertThat(mapped.quantity()).isEqualByComparingTo("10.000");
            });
        });
    }
}
