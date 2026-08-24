package com.odcc.tienda.modules.identity.adapter.in.rest.mapper;

import com.odcc.tienda.modules.identity.adapter.in.rest.request.AssignRolePermissionsRequest;
import com.odcc.tienda.modules.identity.adapter.in.rest.request.AssignUserBranchesRequest;
import com.odcc.tienda.modules.identity.adapter.in.rest.request.AssignUserRolesRequest;
import com.odcc.tienda.modules.identity.adapter.in.rest.request.ChangeRoleStatusRequest;
import com.odcc.tienda.modules.identity.adapter.in.rest.request.ChangeUserPasswordRequest;
import com.odcc.tienda.modules.identity.adapter.in.rest.request.ChangeUserStatusRequest;
import com.odcc.tienda.modules.identity.adapter.in.rest.request.CreateRoleRequest;
import com.odcc.tienda.modules.identity.adapter.in.rest.request.CreateUserRequest;
import com.odcc.tienda.modules.identity.adapter.in.rest.request.UpdateRoleRequest;
import com.odcc.tienda.modules.identity.adapter.in.rest.request.UpdateUserRequest;
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
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(config = CentralMapperConfig.class)
public interface UserManagementRestMapper {

    @Mapping(target = "actorUserId", source = "actorUserId")
    @Mapping(target = "username", source = "request.username")
    @Mapping(target = "displayName", source = "request.displayName")
    @Mapping(target = "password", source = "request.password")
    @Mapping(target = "roleCodes", source = "request.roleCodes")
    CreateUserCommand toCreateUserCommand(UUID actorUserId, CreateUserRequest request);

    @Mapping(target = "actorUserId", source = "actorUserId")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "username", source = "request.username")
    @Mapping(target = "displayName", source = "request.displayName")
    UpdateUserCommand toUpdateUserCommand(UUID actorUserId, UUID userId, UpdateUserRequest request);

    @Mapping(target = "currentUserId", source = "actorUserId")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "status", source = "request.status")
    ChangeUserStatusCommand toChangeUserStatusCommand(UUID actorUserId, UUID userId, ChangeUserStatusRequest request);

    @Mapping(target = "actorUserId", source = "actorUserId")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "password", source = "request.password")
    ChangeUserPasswordCommand toChangeUserPasswordCommand(UUID actorUserId, UUID userId, ChangeUserPasswordRequest request);

    @Mapping(target = "actorUserId", source = "actorUserId")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "roleCodes", source = "request.roleCodes")
    AssignUserRolesCommand toAssignUserRolesCommand(UUID actorUserId, UUID userId, AssignUserRolesRequest request);

    @Mapping(target = "actorUserId", source = "actorUserId")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "branchIds", source = "request.branchIds")
    AssignUserBranchesCommand toAssignUserBranchesCommand(UUID actorUserId, UUID userId, AssignUserBranchesRequest request);

    CreateRoleCommand toCreateRoleCommand(CreateRoleRequest request);

    @Mapping(target = "roleId", source = "roleId")
    @Mapping(target = "code", source = "request.code")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "description", source = "request.description")
    UpdateRoleCommand toUpdateRoleCommand(UUID roleId, UpdateRoleRequest request);

    @Mapping(target = "roleId", source = "roleId")
    @Mapping(target = "status", source = "request.status")
    ChangeRoleStatusCommand toChangeRoleStatusCommand(UUID roleId, ChangeRoleStatusRequest request);

    @Mapping(target = "actorUserId", source = "actorUserId")
    @Mapping(target = "roleId", source = "roleId")
    @Mapping(target = "permissionCodes", source = "request.permissionCodes")
    AssignRolePermissionsCommand toAssignRolePermissionsCommand(
        UUID actorUserId,
        UUID roleId,
        AssignRolePermissionsRequest request
    );
}
