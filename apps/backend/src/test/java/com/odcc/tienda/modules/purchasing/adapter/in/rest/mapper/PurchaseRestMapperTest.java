package com.odcc.tienda.modules.purchasing.adapter.in.rest.mapper;

import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.ReceivePurchaseItemRequest;
import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.ReceivePurchasePalletRequest;
import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.ReceivePurchaseRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseRestMapperTest {

    private final PurchaseRestMapper mapper = Mappers.getMapper(PurchaseRestMapper.class);

    @Test
    void mapsRoutePurchaseIdAndNestedReceiptItems() {
        UUID purchaseId = UUID.randomUUID();
        UUID purchaseItemId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        var item = new ReceivePurchaseItemRequest(
            purchaseItemId,
            "LOT-COMPRA-01",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2027, 8, 1),
            new BigDecimal("25.000")
        );
        var request = new ReceivePurchaseRequest(
            idempotencyKey,
            null,
            List.of(new ReceivePurchasePalletRequest("SSCC-COMPRA", List.of(item)))
        );

        var command = mapper.toReceiveCommand(request, purchaseId);

        assertThat(command.purchaseId()).isEqualTo(purchaseId);
        assertThat(command.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(command.items()).isEmpty();
        assertThat(command.pallets()).singleElement().satisfies(pallet ->
            assertThat(pallet.items()).singleElement().satisfies(mapped -> {
                assertThat(mapped.purchaseItemId()).isEqualTo(purchaseItemId);
                assertThat(mapped.lotNumber()).isEqualTo("LOT-COMPRA-01");
                assertThat(mapped.quantity()).isEqualByComparingTo("25.000");
            })
        );
    }
}
