package com.odcc.tienda.modules.sales.application.usecase;

import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderItemCommand;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.exception.SalesOrderIdempotencyConflictException;
import com.odcc.tienda.modules.sales.application.model.SalesOrder;
import com.odcc.tienda.modules.sales.support.InMemorySalesOrderRepository;
import com.odcc.tienda.shared.support.ImmediateTransactionRunner;
import com.odcc.tienda.shared.support.InMemoryBusinessAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SalesOrderServiceTest {

    private InMemorySalesOrderRepository repository;
    private InMemoryBusinessAuditPort auditPort;
    private SalesOrderService service;

    @BeforeEach
    void setUp() {
        repository = new InMemorySalesOrderRepository();
        auditPort = new InMemoryBusinessAuditPort();
        service = new SalesOrderService(repository, new ImmediateTransactionRunner(), auditPort);
    }

    @Test
    void shouldCreateConfirmedSalesOrderAndAuditIt() {
        SalesOrder order = service.create(validCommand(UUID.randomUUID()));

        assertEquals("CONFIRMED", order.status());
        assertEquals("SALES_ORDER_CREATED", auditPort.events().getFirst().eventType());
    }

    @Test
    void shouldReturnExistingSalesOrderWhenIdempotencyKeyAndPayloadMatch() {
        UUID idempotencyKey = UUID.randomUUID();
        CreateSalesOrderCommand command = validCommand(idempotencyKey);

        SalesOrder first = service.create(command);
        SalesOrder second = service.create(command);

        assertSame(first, second);
        assertEquals(1, auditPort.events().size());
    }

    @Test
    void shouldRejectSameIdempotencyKeyWithDifferentPayload() {
        UUID idempotencyKey = UUID.randomUUID();
        service.create(validCommand(idempotencyKey));

        CreateSalesOrderCommand differentPayload = new CreateSalesOrderCommand(
            UUID.randomUUID(),
            null,
            null,
            "POS",
            "MXN",
            idempotencyKey,
            List.of(new CreateSalesOrderItemCommand(UUID.randomUUID(), BigDecimal.ONE, new BigDecimal("30.00"), BigDecimal.ZERO))
        );

        assertThrows(SalesOrderIdempotencyConflictException.class, () -> service.create(differentPayload));
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

        assertThrows(SalesException.class, () -> service.create(command));
    }

    @Test
    void shouldCancelConfirmedSalesOrderAndAuditIt() {
        SalesOrder order = service.create(validCommand(UUID.randomUUID()));

        SalesOrder cancelled = service.cancel(order.salesOrderId());

        assertEquals("CANCELLED", cancelled.status());
        assertEquals("SALES_ORDER_CANCELLED", auditPort.events().get(1).eventType());
    }

    private static CreateSalesOrderCommand validCommand(UUID idempotencyKey) {
        return new CreateSalesOrderCommand(
            UUID.randomUUID(),
            null,
            null,
            "POS",
            "MXN",
            idempotencyKey,
            List.of(new CreateSalesOrderItemCommand(UUID.randomUUID(), BigDecimal.ONE, new BigDecimal("25.00"), BigDecimal.ZERO))
        );
    }
}

