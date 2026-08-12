package com.odcc.tienda.modules.identity.application.usecase;

import com.odcc.tienda.modules.identity.application.command.AssignUserRolesCommand;
import com.odcc.tienda.modules.identity.application.command.AssignUserBranchesCommand;
import com.odcc.tienda.modules.identity.application.command.AssignRolePermissionsCommand;
import com.odcc.tienda.modules.identity.application.command.CreateUserCommand;
import com.odcc.tienda.modules.identity.application.command.ChangeUserPasswordCommand;
import com.odcc.tienda.modules.identity.application.model.ManagedUser;
import com.odcc.tienda.modules.identity.application.port.out.UserManagementRepositoryPort;
import com.odcc.tienda.modules.identity.domain.model.UserAccountStatus;
import com.odcc.tienda.shared.application.authorization.BranchAccessDeniedException;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.authorization.BranchScope;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("4d5d3ed8-28ac-4545-9cf0-2db313fc97dc");
    private static final UUID TARGET_ID = UUID.fromString("b12a88d6-0264-439a-8d68-9889a917330b");
    private static final UUID ROLE_ID = UUID.fromString("0ffec0f9-dc2d-4ce8-bf86-27d4fc3fe8e8");
    private static final UUID BRANCH_ID = UUID.fromString("1427adbb-e0a3-49b8-befd-2f698173563e");
    private static final UUID SECOND_BRANCH_ID = UUID.fromString("1735128b-56fc-47a7-842d-67787fa9e356");

    @Mock
    private UserManagementRepositoryPort repository;

    @Mock
    private BranchAccessPort branchAccess;

    private UserManagementService service;

    @BeforeEach
    void setUp() {
        TransactionRunner transactionRunner = new TransactionRunner() {
            @Override
            public <T> T required(Supplier<T> operation) {
                return operation.get();
            }
        };
        service = new UserManagementService(
            repository,
            rawPassword -> "hashed-password",
            transactionRunner,
            event -> { },
            branchAccess
        );
    }

    @Test
    void delegatedUserCannotGrantSystemAdminRole() {
        when(repository.findById(TARGET_ID)).thenReturn(Optional.of(managedUser(TARGET_ID, Set.of("CATALOG_MANAGER"), Set.of())));
        when(repository.findActiveRoleCodes(Set.of("SYSTEM_ADMIN"))).thenReturn(Set.of("SYSTEM_ADMIN"));
        when(branchAccess.resolveScope(ACTOR_ID)).thenReturn(BranchScope.restricted(Set.of()));

        assertThrows(
            BranchAccessDeniedException.class,
            () -> service.assignRoles(
                new AssignUserRolesCommand(ACTOR_ID, TARGET_ID, Set.of("SYSTEM_ADMIN"))
            )
        );
    }

    @Test
    void delegatedUserCannotCreateSystemAdminAccount() {
        when(repository.findActiveRoleCodes(Set.of("SYSTEM_ADMIN"))).thenReturn(Set.of("SYSTEM_ADMIN"));
        when(branchAccess.resolveScope(ACTOR_ID)).thenReturn(BranchScope.restricted(Set.of()));

        assertThrows(
            BranchAccessDeniedException.class,
            () -> service.create(
                new CreateUserCommand(
                    ACTOR_ID,
                    "new-admin",
                    "New Admin",
                    "Temporary123!",
                    Set.of("SYSTEM_ADMIN")
                )
            )
        );
    }

    @Test
    void userCannotAddRolesToItself() {
        when(repository.findById(ACTOR_ID)).thenReturn(Optional.of(managedUser(ACTOR_ID, Set.of("DELEGATED"), Set.of())));
        when(repository.findActiveRoleCodes(Set.of("DELEGATED", "CATALOG_MANAGER")))
            .thenReturn(Set.of("DELEGATED", "CATALOG_MANAGER"));

        assertThrows(
            BranchAccessDeniedException.class,
            () -> service.assignRoles(
                new AssignUserRolesCommand(
                    ACTOR_ID,
                    ACTOR_ID,
                    Set.of("DELEGATED", "CATALOG_MANAGER")
                )
            )
        );
    }

    @Test
    void delegatedUserCannotExpandBranchAssignments() {
        when(repository.findById(TARGET_ID)).thenReturn(Optional.of(managedUser(TARGET_ID, Set.of("CASHIER"), Set.of(BRANCH_ID))));
        when(repository.findActiveBranchIds(Set.of(BRANCH_ID, SECOND_BRANCH_ID)))
            .thenReturn(Set.of(BRANCH_ID, SECOND_BRANCH_ID));
        when(branchAccess.resolveScope(ACTOR_ID)).thenReturn(BranchScope.restricted(Set.of(BRANCH_ID, SECOND_BRANCH_ID)));

        assertThrows(
            BranchAccessDeniedException.class,
            () -> service.assignBranches(
                new AssignUserBranchesCommand(
                    ACTOR_ID,
                    TARGET_ID,
                    Set.of(BRANCH_ID, SECOND_BRANCH_ID)
                )
            )
        );
    }

    @Test
    void delegatedUserCannotModifyRolePermissions() {
        when(branchAccess.resolveScope(ACTOR_ID)).thenReturn(BranchScope.restricted(Set.of()));

        assertThrows(
            BranchAccessDeniedException.class,
            () -> service.assignRolePermissions(
                new AssignRolePermissionsCommand(
                    ACTOR_ID,
                    ROLE_ID,
                    Set.of("IDENTITY_USER_ROLE_ASSIGN")
                )
            )
        );
    }

    @Test
    void delegatedUserCannotTakeOverBranchlessAccount() {
        when(repository.findById(TARGET_ID)).thenReturn(Optional.of(
            managedUser(TARGET_ID, Set.of("CASHIER"), Set.of())
        ));
        when(branchAccess.resolveScope(ACTOR_ID)).thenReturn(
            BranchScope.restricted(Set.of(BRANCH_ID))
        );

        assertThrows(
            BranchAccessDeniedException.class,
            () -> service.changePassword(
                new ChangeUserPasswordCommand(ACTOR_ID, TARGET_ID, "Replacement123!")
            )
        );
    }

    private static ManagedUser managedUser(UUID userId, Set<String> roles, Set<UUID> branchIds) {
        return new ManagedUser(
            userId,
            "managed-user",
            "Managed User",
            UserAccountStatus.ACTIVE,
            roles,
            Set.of(),
            branchIds,
            Instant.parse("2026-08-11T12:00:00Z"),
            Instant.parse("2026-08-11T12:00:00Z")
        );
    }
}
