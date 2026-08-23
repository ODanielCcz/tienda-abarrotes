package com.odcc.tienda.modules.purchasing.application.usecase;

import com.odcc.tienda.modules.inventory.application.command.CreateInventoryReceiptCommand;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceiptExecution;
import com.odcc.tienda.modules.inventory.application.port.in.CreateInventoryReceiptUseCase;
import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseItemCommand;
import com.odcc.tienda.modules.purchasing.application.command.ReceivePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.command.ReceivePurchaseItemCommand;
import com.odcc.tienda.modules.purchasing.application.exception.PurchasingException;
import com.odcc.tienda.modules.purchasing.application.exception.PurchaseIdempotencyConflictException;
import com.odcc.tienda.modules.purchasing.application.exception.PurchaseItemMismatchException;
import com.odcc.tienda.modules.purchasing.application.model.Purchase;
import com.odcc.tienda.modules.purchasing.support.InMemoryPurchaseRepository;
import com.odcc.tienda.shared.support.ImmediateTransactionRunner;
import com.odcc.tienda.shared.support.InMemoryBusinessAuditPort;
import com.odcc.tienda.shared.support.AllowAllBranchAccessPort;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.authorization.BranchScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PurchaseServiceTest {

    private InMemoryPurchaseRepository repository;
    private PurchaseService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPurchaseRepository();
        service = new PurchaseService(repository, new FakeInventoryReceiptUseCase(), new ImmediateTransactionRunner(), new InMemoryBusinessAuditPort(), new AllowAllBranchAccessPort());
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
    void shouldNotApplyReceivedQuantityWhenInventoryReceiptIsReplayed() {
        Purchase draft = service.create(new CreatePurchaseCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "FAC-REPLAY",
            "MXN",
            UUID.randomUUID(),
            List.of(new CreatePurchaseItemCommand(
                UUID.randomUUID(),
                new BigDecimal("2.000"),
                new BigDecimal("10.0000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
            ))
        ));
        Purchase purchase = service.confirm(draft.purchaseId());
        UUID idempotencyKey = UUID.randomUUID();
        ReceivePurchaseCommand command = new ReceivePurchaseCommand(
            purchase.purchaseId(),
            idempotencyKey,
            List.of(new ReceivePurchaseItemCommand(
                purchase.items().getFirst().purchaseItemId(),
                null,
                null,
                null,
                BigDecimal.ONE
            )),
            List.of()
        );

        service.receive(command);
        service.receive(command);

        Purchase updated = service.getById(purchase.purchaseId());
        assertEquals("PARTIALLY_RECEIVED", updated.status());
        assertEquals(BigDecimal.ONE, updated.items().getFirst().receivedQuantity());
    }

    @Test
    void shouldRejectReceivingMoreThanPurchased() {
        Purchase purchase = service.confirm(createPurchase().purchaseId());

        assertThrows(PurchasingException.class, () -> service.receive(new ReceivePurchaseCommand(purchase.purchaseId(), UUID.randomUUID(), List.of(new ReceivePurchaseItemCommand(purchase.items().getFirst().purchaseItemId(), null, null, null, new BigDecimal("2"))), List.of())));
    }

    @Test
    void shouldRejectItemThatBelongsToAnotherPurchase() {
        Purchase target = service.confirm(createPurchase().purchaseId());
        Purchase another = service.confirm(createPurchase().purchaseId());

        ReceivePurchaseCommand command = new ReceivePurchaseCommand(
            target.purchaseId(),
            UUID.randomUUID(),
            List.of(new ReceivePurchaseItemCommand(
                another.items().getFirst().purchaseItemId(),
                null,
                null,
                null,
                BigDecimal.ONE
            )),
            List.of()
        );

        assertThrows(PurchaseItemMismatchException.class, () -> service.receive(command));
    }

    @Test
    void shouldRejectIdempotencyReplayWhenPayloadChanges() {
        UUID idempotencyKey = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID presentationId = UUID.randomUUID();
        CreatePurchaseCommand original = purchaseCommand(idempotencyKey, warehouseId, supplierId, presentationId, new BigDecimal("10.00"));
        service.create(original);

        CreatePurchaseCommand changed = purchaseCommand(idempotencyKey, warehouseId, supplierId, presentationId, new BigDecimal("11.00"));

        PurchaseIdempotencyConflictException exception = assertThrows(PurchaseIdempotencyConflictException.class, () -> service.create(changed));
        assertEquals("La llave de idempotencia ya fue utilizada con otra solicitud", exception.getMessage());
    }

    @Test
    void shouldRejectIdempotencyReplayWhenWarehouseChanges() {
        UUID idempotencyKey = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID presentationId = UUID.randomUUID();
        CreatePurchaseCommand original = purchaseCommand(idempotencyKey, UUID.randomUUID(), supplierId, presentationId, new BigDecimal("10.00"));
        service.create(original);

        CreatePurchaseCommand changedBranch = purchaseCommand(idempotencyKey, UUID.randomUUID(), supplierId, presentationId, new BigDecimal("10.00"));

        PurchaseIdempotencyConflictException exception = assertThrows(PurchaseIdempotencyConflictException.class, () -> service.create(changedBranch));
        assertEquals("La llave de idempotencia ya fue utilizada con otra solicitud", exception.getMessage());
    }

    @Test
    void shouldReturnExistingPurchaseForCanonicalIdempotencyReplay() {
        UUID idempotencyKey = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID presentationId = UUID.randomUUID();
        Purchase created = service.create(new CreatePurchaseCommand(
            warehouseId,
            supplierId,
            "FAC-1",
            "MXN",
            idempotencyKey,
            List.of(new CreatePurchaseItemCommand(presentationId, new BigDecimal("1.0"), new BigDecimal("10.00"), null, null))
        ));

        Purchase replay = service.create(new CreatePurchaseCommand(
            warehouseId,
            supplierId,
            " FAC-1 ",
            " mxn ",
            idempotencyKey,
            List.of(new CreatePurchaseItemCommand(presentationId, new BigDecimal("1.000"), new BigDecimal("10.0000"), BigDecimal.ZERO, BigDecimal.ZERO))
        ));

        assertEquals(created.purchaseId(), replay.purchaseId());
    }

    @Test
    void shouldAuthorizePersistedBranchBeforeReturningIdempotentReplay() {
        UUID idempotencyKey = UUID.randomUUID();
        CreatePurchaseCommand command = purchaseCommand(
            idempotencyKey,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("10.00")
        );
        Purchase created = service.create(command);
        RecordingBranchAccessPort branchAccess = new RecordingBranchAccessPort();
        service = new PurchaseService(repository, new FakeInventoryReceiptUseCase(), new ImmediateTransactionRunner(), new InMemoryBusinessAuditPort(), branchAccess);

        Purchase replay = service.create(command, UUID.randomUUID());

        assertEquals(created.purchaseId(), replay.purchaseId());
        assertEquals(List.of(created.branchId()), branchAccess.requiredBranchIds);
    }

    private Purchase createPurchase() {
        return service.create(new CreatePurchaseCommand(UUID.randomUUID(), UUID.randomUUID(), "FAC-1", "MXN", UUID.randomUUID(), List.of(new CreatePurchaseItemCommand(UUID.randomUUID(), BigDecimal.ONE, new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO))));
    }

    private static CreatePurchaseCommand purchaseCommand(
        UUID idempotencyKey,
        UUID warehouseId,
        UUID supplierId,
        UUID presentationId,
        BigDecimal unitCost
    ) {
        return new CreatePurchaseCommand(
            warehouseId,
            supplierId,
            "FAC-1",
            "MXN",
            idempotencyKey,
            List.of(new CreatePurchaseItemCommand(presentationId, BigDecimal.ONE, unitCost, BigDecimal.ZERO, BigDecimal.ZERO))
        );
    }

    private static final class RecordingBranchAccessPort implements BranchAccessPort {
        private final List<UUID> requiredBranchIds = new java.util.ArrayList<>();

        @Override
        public BranchScope resolveScope(UUID userId) {
            return BranchScope.restricted(Set.copyOf(requiredBranchIds));
        }

        @Override
        public void requireAccess(UUID userId, UUID branchId) {
            requiredBranchIds.add(branchId);
        }
    }

    private static class FakeInventoryReceiptUseCase implements CreateInventoryReceiptUseCase {
        private final Map<UUID, InventoryReceipt> receipts = new HashMap<>();

        @Override
        public InventoryReceipt execute(CreateInventoryReceiptCommand command) {
            return executeWithOutcome(command).receipt();
        }

        @Override
        public InventoryReceiptExecution executeWithOutcome(CreateInventoryReceiptCommand command) {
            InventoryReceipt existing = receipts.get(command.idempotencyKey());
            if (existing != null) return InventoryReceiptExecution.replayed(existing);
            InventoryReceipt created = new InventoryReceipt(UUID.randomUUID(), command.warehouseId(), command.supplierId(), "CONFIRMED", Instant.now(), List.of(), List.of());
            receipts.put(command.idempotencyKey(), created);
            return InventoryReceiptExecution.created(created);
        }
    }
}
