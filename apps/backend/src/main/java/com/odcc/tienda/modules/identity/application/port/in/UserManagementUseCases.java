package com.odcc.tienda.modules.identity.application.port.in;

import com.odcc.tienda.modules.identity.application.command.AssignUserRolesCommand;
import com.odcc.tienda.modules.identity.application.command.ChangeUserPasswordCommand;
import com.odcc.tienda.modules.identity.application.command.ChangeUserStatusCommand;
import com.odcc.tienda.modules.identity.application.command.CreateUserCommand;
import com.odcc.tienda.modules.identity.application.command.UpdateUserCommand;
import com.odcc.tienda.modules.identity.application.model.ManagedUser;
import com.odcc.tienda.modules.identity.application.model.PermissionSummary;
import com.odcc.tienda.modules.identity.application.model.RoleSummary;
import com.odcc.tienda.modules.identity.application.query.ListUsersQuery;

import java.util.List;
import java.util.UUID;

public interface UserManagementUseCases {

    ManagedUser create(CreateUserCommand command);

    ManagedUser getById(UUID userId);

    List<ManagedUser> list(ListUsersQuery query);

    ManagedUser update(UpdateUserCommand command);

    ManagedUser changeStatus(ChangeUserStatusCommand command);

    ManagedUser changePassword(ChangeUserPasswordCommand command);

    ManagedUser assignRoles(AssignUserRolesCommand command);

    List<RoleSummary> listRoles();

    List<PermissionSummary> listPermissions();
}
