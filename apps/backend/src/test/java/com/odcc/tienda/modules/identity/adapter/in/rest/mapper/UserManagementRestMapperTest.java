package com.odcc.tienda.modules.identity.adapter.in.rest.mapper;

import com.odcc.tienda.modules.identity.adapter.in.rest.request.AssignRolePermissionsRequest;
import com.odcc.tienda.modules.identity.adapter.in.rest.request.CreateUserRequest;
import com.odcc.tienda.modules.identity.adapter.in.rest.request.UpdateUserRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserManagementRestMapperTest {

    private final UserManagementRestMapper mapper = Mappers.getMapper(UserManagementRestMapper.class);

    @Test
    void mapsAuthenticatedActorWhenCreatingUser() {
        UUID actorUserId = UUID.randomUUID();
        var request = new CreateUserRequest(
            "cajero1",
            "Cajero Uno",
            "Temporal123!",
            Set.of("CASHIER")
        );

        var command = mapper.toCreateUserCommand(actorUserId, request);

        assertThat(command.actorUserId()).isEqualTo(actorUserId);
        assertThat(command.username()).isEqualTo("cajero1");
        assertThat(command.roleCodes()).containsExactly("CASHIER");
    }

    @Test
    void mapsRouteAndAuthenticatedActorWhenUpdatingUser() {
        UUID actorUserId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        var command = mapper.toUpdateUserCommand(
            actorUserId,
            userId,
            new UpdateUserRequest("cajero2", "Cajero Dos")
        );

        assertThat(command.actorUserId()).isEqualTo(actorUserId);
        assertThat(command.userId()).isEqualTo(userId);
        assertThat(command.displayName()).isEqualTo("Cajero Dos");
    }

    @Test
    void mapsRouteActorAndPermissionsWhenAssigningRolePermissions() {
        UUID actorUserId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        var command = mapper.toAssignRolePermissionsCommand(
            actorUserId,
            roleId,
            new AssignRolePermissionsRequest(Set.of("SALES_ORDER_READ"))
        );

        assertThat(command.actorUserId()).isEqualTo(actorUserId);
        assertThat(command.roleId()).isEqualTo(roleId);
        assertThat(command.permissionCodes()).containsExactly("SALES_ORDER_READ");
    }
}
