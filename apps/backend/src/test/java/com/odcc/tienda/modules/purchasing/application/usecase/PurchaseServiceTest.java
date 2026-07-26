package com.odcc.tienda.modules.purchasing.application.usecase;

import com.odcc.tienda.modules.inventory.application.command.CreateInventoryReceiptCommand;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;
import com.odcc.tienda.modules.inventory.application.port.in.CreateInventoryReceiptUseCase;
import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseItemCommand;
import com.odcc.tienda.modules.purchasing.application.command.ReceivePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.command.ReceivePurchaseItemCommand;
import com.odcc.tienda.modules.purchasing.application.exception.PurchasingException;
import com.odcc.tienda.modules.purchasing.application.model.Purchase;
import com.odcc.tienda.modules.purchasing.support.InMemoryPurchaseRepository;
import com.odcc.tienda.shared.support.ImmediateTransactionRunner;
import com.odcc.tienda.shared.support.InMemoryBusinessAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PurchaseServiceTest {

    private InMemoryPurchaseRepository repository;
    private PurchaseService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPurchaseRepository();
        service = new PurchaseService(repository, new FakeInventoryReceiptUseCase(), new ImmediateTransactionRunner(), new InMemoryBusinessAuditPort());
    }

    @Test
    void shouldCreateAndConfirmPurchase() {
        Purchase purchase = createPurchase();

        Purchase confirmed = service.confirm(purchase.purchaseId());

        assertEquals("CONFIRMED", confirmed.status());
    }

    @Test
    void shouldReceiveConfirmedPurchaseAndMarkAsReceived() {
        Purchase purchase = service.confirm(createPurchase().purchaseId());

        service.receive(new ReceivePurchaseCommand(purchase.purchaseId(), UUID.randomUUID(), List.of(new ReceivePurchaseItemCommand(purchase.items().getFirst().purchaseItemId(), null, null, null, BigDecimal.ONE)), List.of()));

        Purchase updated = service.getById(purchase.purchaseId());
        assertEquals("RECEIVED", updated.status());
        assertEquals(BigDecimal.ONE, updated.items().getFirst().receivedQuantity());
    }

    @Test
    void shouldRejectReceivingMoreThanPurchased() {
        Purchase purchase = service.confirm(createPurchase().purchaseId());

        assertThrows(PurchasingException.class, () -> service.receive(new ReceivePurchaseCommand(purchase.purchaseId(), UUID.randomUUID(), List.of(new ReceivePurchaseItemCommand(purchase.items().getFirst().purchaseItemId(), null, null, null, new BigDecimal("2"))), List.of())));
    }

    private Purchase createPurchase() {
        return service.create(new CreatePurchaseCommand(UUID.randomUUID(), UUID.randomUUID(), "FAC-1", "MXN", UUID.randomUUID(), List.of(new CreatePurchaseItemCommand(UUID.randomUUID(), BigDecimal.ONE, new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO))));
    }

    private static class FakeInventoryReceiptUseCase implements CreateInventoryReceiptUseCase {
        @Override
        public InventoryReceipt execute(CreateInventoryReceiptCommand command) {
            return new InventoryReceipt(UUID.randomUUID(), command.warehouseId(), command.supplierId(), "CONFIRMED", Instant.now(), List.of(), List.of());
        }
    }
}
