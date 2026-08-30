package com.odcc.tienda.modules.sales.application.usecase;

import com.odcc.tienda.modules.sales.application.command.ConfirmSalesReturnCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderItemCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesReturnCommand;
import com.odcc.tienda.modules.sales.application.model.SalesReturn;
import com.odcc.tienda.modules.sales.application.port.out.SalesReturnRepositoryPort;
import com.odcc.tienda.modules.sales.support.InMemorySalesOrderRepository;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.authorization.BranchScope;
import com.odcc.tienda.shared.support.ImmediateTransactionRunner;
import com.odcc.tienda.shared.support.InMemoryBusinessAuditPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SalesReturnListServiceTest {

    @Test
    void shouldListReturnsAfterAuthorizingTheSalesOrderBranch() {
        UUID actorId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID presentationId = UUID.randomUUID();
        InMemorySalesOrderRepository orderRepository = new InMemorySalesOrderRepository();
        var order = orderRepository.createConfirmed(
            new CreateSalesOrderCommand(
                warehouseId,
                null,
                null,
                "POS",
                "MXN",
                UUID.randomUUID(),
                List.of(new CreateSalesOrderItemCommand(
                    presentationId,
                    BigDecimal.ONE,
                    new BigDecimal("25.00"),
                    BigDecimal.ZERO
                ))
            ),
            "fingerprint"
        );
        SalesReturn salesReturn = new SalesReturn(
            UUID.randomUUID(),
            order.salesOrderId(),
            "DRAFT",
            "Producto dañado",
            new BigDecimal("25.00"),
            actorId,
            Instant.now(),
            null,
            List.of()
        );
        ReturnRepository repository = new ReturnRepository(List.of(salesReturn));
        RecordingBranchAccess branchAccess = new RecordingBranchAccess();
        SalesReturnService service = new SalesReturnService(
            repository,
            new ImmediateTransactionRunner(),
            new InMemoryBusinessAuditPort(),
            orderRepository,
            branchAccess
        );

        List<SalesReturn> result = service.listBySalesOrder(order.salesOrderId(), actorId);

        assertEquals(List.of(salesReturn), result);
        assertEquals(order.branchId(), branchAccess.requiredBranchId);
    }

    private static final class ReturnRepository implements SalesReturnRepositoryPort {
        private final List<SalesReturn> returns;

        private ReturnRepository(List<SalesReturn> returns) {
            this.returns = returns;
        }

        @Override
        public List<SalesReturn> findAllBySalesOrderId(UUID salesOrderId) {
            return returns.stream()
                .filter(item -> item.salesOrderId().equals(salesOrderId))
                .toList();
        }

        @Override public SalesReturn createDraft(CreateSalesReturnCommand command) { throw new UnsupportedOperationException(); }
        @Override public Optional<SalesReturn> findById(UUID returnId) { return Optional.empty(); }
        @Override public SalesReturn confirm(ConfirmSalesReturnCommand command) { throw new UnsupportedOperationException(); }
        @Override public SalesReturn cancel(UUID returnId) { throw new UnsupportedOperationException(); }
    }

    private static final class RecordingBranchAccess implements BranchAccessPort {
        private UUID requiredBranchId;

        @Override public BranchScope resolveScope(UUID userId) { return BranchScope.global(); }

        @Override
        public void requireAccess(UUID userId, UUID branchId) {
            requiredBranchId = branchId;
        }
    }
}
