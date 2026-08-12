package com.odcc.tienda.modules.organization.application.usecase;

import com.odcc.tienda.modules.organization.application.command.BranchCommands.ChangeBranchStatusCommand;
import com.odcc.tienda.modules.organization.application.model.BranchView;
import com.odcc.tienda.modules.organization.application.port.out.OrganizationRepositoryPort;
import com.odcc.tienda.modules.organization.domain.model.BranchStatus;
import com.odcc.tienda.shared.application.authorization.BranchAccessDeniedException;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.authorization.BranchScope;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizationServiceSecurityTest {

    private static final UUID BRANCH_ID = UUID.fromString("1427adbb-e0a3-49b8-befd-2f698173563e");
    private static final UUID ACTOR_ID = UUID.fromString("4d5d3ed8-28ac-4545-9cf0-2db313fc97dc");

    @Test
    void scopedActorCannotReactivateBranchChangedToInactiveBeforeTransaction() {
        AtomicBoolean transactionStarted = new AtomicBoolean();
        BranchView active = branch(BranchStatus.ACTIVE);
        BranchView inactive = branch(BranchStatus.INACTIVE);
        OrganizationRepositoryPort repository = mock(
            OrganizationRepositoryPort.class,
            invocation -> switch (invocation.getMethod().getName()) {
                case "findBranch", "findBranchForUpdate" -> Optional.of(
                    transactionStarted.get() ? inactive : active
                );
                case "changeBranchStatus" -> active;
                default -> org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            }
        );
        BranchAccessPort branchAccess = mock(BranchAccessPort.class);
        when(branchAccess.resolveScope(ACTOR_ID)).thenReturn(
            BranchScope.restricted(Set.of(BRANCH_ID))
        );
        TransactionRunner transactionRunner = new TransactionRunner() {
            @Override
            public <T> T required(Supplier<T> operation) {
                transactionStarted.set(true);
                return operation.get();
            }
        };
        OrganizationService service = new OrganizationService(
            repository,
            transactionRunner,
            event -> { },
            branchAccess
        );

        assertThrows(
            BranchAccessDeniedException.class,
            () -> service.changeBranchStatus(
                new ChangeBranchStatusCommand(BRANCH_ID, BranchStatus.ACTIVE),
                ACTOR_ID
            )
        );
    }

    private static BranchView branch(BranchStatus status) {
        Instant timestamp = Instant.parse("2026-08-12T00:00:00Z");
        return new BranchView(
            BRANCH_ID,
            "MAIN",
            "Principal",
            null,
            "America/Mexico_City",
            "MXN",
            status,
            timestamp,
            timestamp
        );
    }
}
