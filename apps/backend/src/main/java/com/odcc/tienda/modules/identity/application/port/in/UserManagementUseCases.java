package com.odcc.tienda.modules.identity.application.port.in;

import com.odcc.tienda.modules.identity.application.command.AssignRolePermissionsCommand;
import com.odcc.tienda.modules.identity.application.command.AssignUserBranchesCommand;
import com.odcc.tienda.modules.identity.application.command.AssignUserRolesCommand;
import com.odcc.tienda.modules.identity.application.command.ChangeRoleStatusCommand;
import com.odcc.tienda.modules.identity.application.command.ChangeUserPasswordCommand;
import com.odcc.tienda.modules.identity.application.command.ChangeUserStatusCommand;
import com.odcc.tienda.modules.identity.application.command.CreateRoleCommand;
import com.odcc.tienda.modules.identity.application.command.CreateUserCommand;
import com.odcc.tienda.modules.identity.application.command.UpdateRoleCommand;
import com.odcc.tienda.modules.identity.application.command.UpdateUserCommand;
import com.odcc.tienda.modules.identity.application.model.ManagedUser;
import com.odcc.tienda.modules.identity.application.model.PermissionSummary;
import com.odcc.tienda.modules.identity.application.model.RoleDetail;
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

    ManagedUser assignBranches(AssignUserBranchesCommand command);

    RoleDetail createRole(CreateRoleCommand command);

    RoleDetail updateRole(UpdateRoleCommand command);

    RoleDetail changeRoleStatus(ChangeRoleStatusCommand command);

    RoleDetail assignRolePermissions(AssignRolePermissionsCommand command);

    List<RoleSummary> listRoles();

    List<PermissionSummary> listPermissions();
}