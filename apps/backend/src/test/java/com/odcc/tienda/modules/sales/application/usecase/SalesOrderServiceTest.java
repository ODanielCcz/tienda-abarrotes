package com.odcc.tienda.modules.sales.application.usecase;

import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderItemCommand;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.exception.SalesPriceChangedException;
import com.odcc.tienda.modules.sales.application.exception.PriceNotConfiguredException;
import com.odcc.tienda.modules.sales.application.exception.SalesOrderIdempotencyConflictException;
import com.odcc.tienda.modules.sales.application.model.SalesOrder;
import com.odcc.tienda.modules.sales.support.InMemorySalesOrderRepository;
import com.odcc.tienda.shared.support.ImmediateTransactionRunner;
import com.odcc.tienda.shared.support.InMemoryBusinessAuditPort;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.authorization.BranchScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SalesOrderServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private InMemorySalesOrderRepository repository;
    private InMemoryBusinessAuditPort auditPort;
    private SalesOrderService service;

    @BeforeEach
    void setUp() {
        repository = new InMemorySalesOrderRepository();
        auditPort = new InMemoryBusinessAuditPort();
        service = new SalesOrderService(repository, new ImmediateTransactionRunner(), auditPort, new BranchAccessPort() {
            @Override public BranchScope resolveScope(UUID userId) { return BranchScope.global(); }
            @Override public void requireAccess(UUID userId, UUID branchId) { }
        });
    }

    @Test
    void shouldCreateConfirmedSalesOrderAndAuditIt() {
        SalesOrder order = service.create(validCommand(UUID.randomUUID()), ACTOR_ID);

        assertEquals("CONFIRMED", order.status());
        assertEquals("SALES_ORDER_CREATED", auditPort.events().getFirst().eventType());
    }

    @Test
    void shouldReturnExistingSalesOrderWhenIdempotencyKeyAndPayloadMatch() {
        UUID idempotencyKey = UUID.randomUUID();
        CreateSalesOrderCommand command = validCommand(idempotencyKey);

        SalesOrder first = service.create(command, ACTOR_ID);
        SalesOrder second = service.create(command, ACTOR_ID);

        assertSame(first, second);
        assertEquals(1, auditPort.events().size());
    }

    @Test
    void shouldRejectSameIdempotencyKeyWithDifferentPayload() {
        UUID idempotencyKey = UUID.randomUUID();
        service.create(validCommand(idempotencyKey), ACTOR_ID);

        CreateSalesOrderCommand differentPayload = new CreateSalesOrderCommand(
            UUID.randomUUID(),
            null,
            null,
            "POS",
            "MXN",
            idempotencyKey,
            List.of(new CreateSalesOrderItemCommand(UUID.randomUUID(), BigDecimal.ONE, new BigDecimal("30.00"), BigDecimal.ZERO))
        );

        assertThrows(SalesOrderIdempotencyConflictException.class, () -> service.create(differentPayload, ACTOR_ID));
    }

    @Test
    void shouldCreateSalesOrderWithActiveCustomer() {
        UUID customerId = UUID.randomUUID();
        repository.addActiveCustomer(customerId);

        SalesOrder order = service.create(validCommand(UUID.randomUUID(), customerId), ACTOR_ID);

        assertEquals(customerId, order.customerId());
    }

    @Test
    void shouldRejectSalesOrderWithInactiveCustomer() {
        UUID customerId = UUID.randomUUID();

        assertThrows(SalesException.class, () -> service.create(validCommand(UUID.randomUUID(), customerId), ACTOR_ID));
    }

    @Test
    void shouldRejectSalesOrderWithoutIdempotencyKey() {
        CreateSalesOrderCommand command = new CreateSalesOrderCommand(
            UUID.randomUUID(),
            null,
            null,
            "POS",
            "MXN",
            null,
            List.of(new CreateSalesOrderItemCommand(UUID.randomUUID(), BigDecimal.ONE, new BigDecimal("25.00"), BigDecimal.ZERO))
        );

        assertThrows(SalesException.class, () -> service.create(command, ACTOR_ID));
    }

    @Test
    void shouldPersistPriceResolvedByServer() {
        UUID presentationId = UUID.randomUUID();
        repository.setCurrentPrice(presentationId, new BigDecimal("25.00"));
        CreateSalesOrderCommand command = commandWithItem(
            presentationId,
            new BigDecimal("25.0000"),
            BigDecimal.ZERO
        );

        SalesOrder order = service.create(command, ACTOR_ID);

        assertEquals(new BigDecimal("25.00"), order.items().getFirst().unitPrice());
    }

    @Test
    void shouldRejectPriceManipulatedByClient() {
        UUID presentationId = UUID.randomUUID();
        repository.setCurrentPrice(presentationId, new BigDecimal("25.00"));
        CreateSalesOrderCommand command = commandWithItem(
            presentationId,
            new BigDecimal("1.00"),
            BigDecimal.ZERO
        );

        assertThrows(SalesPriceChangedException.class, () -> service.create(command, ACTOR_ID));
    }

    @Test
    void shouldRejectPresentationWithoutCurrentPrice() {
        UUID presentationId = UUID.randomUUID();
        repository.removeCurrentPrice(presentationId);

        assertThrows(
            PriceNotConfiguredException.class,
            () -> service.create(commandWithItem(presentationId, new BigDecimal("25.00"), BigDecimal.ZERO), ACTOR_ID)
        );
    }

    @Test
    void shouldRejectManualDiscountUntilPromotionPolicyExists() {
        UUID presentationId = UUID.randomUUID();

        SalesException exception = assertThrows(
            SalesException.class,
            () -> service.create(commandWithItem(presentationId, new BigDecimal("25.00"), BigDecimal.ONE), ACTOR_ID)
        );

        assertEquals("Los descuentos manuales no estan soportados", exception.getMessage());
    }

    @Test
    void shouldCancelConfirmedSalesOrderAndAuditIt() {
        SalesOrder order = service.create(validCommand(UUID.randomUUID()), ACTOR_ID);

        SalesOrder cancelled = service.cancel(order.salesOrderId(), ACTOR_ID);

        assertEquals("CANCELLED", cancelled.status());
        assertEquals("SALES_ORDER_CANCELLED", auditPort.events().get(1).eventType());
    }

    private static CreateSalesOrderCommand validCommand(UUID idempotencyKey) {
        return validCommand(idempotencyKey, null);
    }

    private static CreateSalesOrderCommand validCommand(UUID idempotencyKey, UUID customerId) {
        return new CreateSalesOrderCommand(
            UUID.randomUUID(),
            customerId,
            null,
            "POS",
            "MXN",
            idempotencyKey,
            List.of(new CreateSalesOrderItemCommand(UUID.randomUUID(), BigDecimal.ONE, new BigDecimal("25.00"), BigDecimal.ZERO))
        );
    }

    private static CreateSalesOrderCommand commandWithItem(
        UUID presentationId,
        BigDecimal unitPrice,
        BigDecimal discount
    ) {
        return new CreateSalesOrderCommand(
            UUID.randomUUID(),
            null,
            null,
            "POS",
            "MXN",
            UUID.randomUUID(),
            List.of(new CreateSalesOrderItemCommand(presentationId, BigDecimal.ONE, unitPrice, discount))
        );
    }
}

