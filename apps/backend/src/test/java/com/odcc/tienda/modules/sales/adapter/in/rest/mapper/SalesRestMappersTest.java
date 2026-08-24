package com.odcc.tienda.modules.sales.adapter.in.rest.mapper;

import com.odcc.tienda.modules.sales.adapter.in.rest.request.ConfirmSalesReturnRequest;
import com.odcc.tienda.modules.sales.adapter.in.rest.request.CreateSalesOrderItemRequest;
import com.odcc.tienda.modules.sales.adapter.in.rest.request.CreateSalesOrderRequest;
import com.odcc.tienda.modules.sales.adapter.in.rest.request.CreateSalesPaymentRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SalesRestMappersTest {

    @Test
    void mapsNestedOrderItemsWithoutChangingExpectedPrice() {
        var mapper = Mappers.getMapper(SalesOrderRestMapper.class);
        UUID presentationId = UUID.randomUUID();
        var request = new CreateSalesOrderRequest(
            UUID.randomUUID(),
            null,
            null,
            "POS",
            "MXN",
            UUID.randomUUID(),
            List.of(new CreateSalesOrderItemRequest(
                presentationId,
                new BigDecimal("2.000"),
                new BigDecimal("18.50"),
                BigDecimal.ZERO
            ))
        );

        var command = mapper.toCreateCommand(request);

        assertThat(command.items()).singleElement().satisfies(item -> {
            assertThat(item.productPresentationId()).isEqualTo(presentationId);
            assertThat(item.quantity()).isEqualByComparingTo("2.000");
            assertThat(item.unitPrice()).isEqualByComparingTo("18.50");
            assertThat(item.discountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Test
    void mapsPaymentRouteAndAuthenticatedActor() {
        var mapper = Mappers.getMapper(SalesPaymentRestMapper.class);
        UUID salesOrderId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var request = new CreateSalesPaymentRequest(
            UUID.randomUUID(),
            "CASH",
            new BigDecimal("42.92"),
            "MXN",
            null,
            UUID.randomUUID()
        );

        var command = mapper.toCreateCommand(salesOrderId, request, actorId);

        assertThat(command.salesOrderId()).isEqualTo(salesOrderId);
        assertThat(command.createdBy()).isEqualTo(actorId);
        assertThat(command.amount()).isEqualByComparingTo("42.92");
    }

    @Test
    void mapsOptionalReturnRequestAndAuthenticatedActor() {
        var mapper = Mappers.getMapper(SalesReturnRestMapper.class);
        UUID returnId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        var command = mapper.toConfirmCommand(
            returnId,
            new ConfirmSalesReturnRequest(null),
            actorId
        );

        assertThat(command.returnId()).isEqualTo(returnId);
        assertThat(command.cashSessionId()).isNull();
        assertThat(command.confirmedBy()).isEqualTo(actorId);
    }
}
