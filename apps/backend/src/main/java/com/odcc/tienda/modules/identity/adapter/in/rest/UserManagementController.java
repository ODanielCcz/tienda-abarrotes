package com.odcc.tienda.modules.identity.adapter.in.rest;

import com.odcc.tienda.modules.identity.adapter.in.rest.mapper.UserManagementRestMapper;
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
import com.odcc.tienda.modules.identity.application.model.ManagedUser;
import com.odcc.tienda.modules.identity.application.model.PermissionSummary;
import com.odcc.tienda.modules.identity.application.model.RoleDetail;
import com.odcc.tienda.modules.identity.application.model.RoleSummary;
import com.odcc.tienda.modules.identity.application.port.in.UserManagementUseCases;
import com.odcc.tienda.modules.identity.application.query.ListUsersQuery;
import com.odcc.tienda.modules.identity.domain.model.UserAccountStatus;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/identity")
@RequiredArgsConstructor
@Tag(name = "Identidad", description = "Administracion de usuarios, roles y permisos")
public class UserManagementController {

    private final UserManagementUseCases useCases;
    private final UserManagementRestMapper mapper;

    @GetMapping("/users")
    @Operation(summary = "Listar usuarios")
    @PreAuthorize("hasAuthority('IDENTITY_USER_READ')")
    public ResponseEntity<ApiResponseDto<List<ManagedUser>>> listUsers(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) UserAccountStatus status,
        @RequestParam(required = false) String roleCode,
        HttpServletRequest servletRequest
    ) {
        List<ManagedUser> users = useCases.list(new ListUsersQuery(search, status, roleCode));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "IDENTITY_USERS_FOUND", "Usuarios consultados correctamente", users, servletRequest.getRequestURI()));
    }

    @PostMapping("/users")
    @Operation(summary = "Crear usuario")
    @PreAuthorize("hasAuthority('IDENTITY_USER_CREATE')")
    public ResponseEntity<ApiResponseDto<ManagedUser>> createUser(@Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        ManagedUser user = useCases.create(mapper.toCreateUserCommand(currentUserId(jwt), request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(HttpStatus.CREATED, "IDENTITY_USER_CREATED", "Usuario creado correctamente", user, servletRequest.getRequestURI()));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Consultar usuario por id")
    @PreAuthorize("hasAuthority('IDENTITY_USER_READ')")
    public ResponseEntity<ApiResponseDto<ManagedUser>> getUserById(@PathVariable UUID userId, HttpServletRequest servletRequest) {
        ManagedUser user = useCases.getById(userId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "IDENTITY_USER_FOUND", "Usuario consultado correctamente", user, servletRequest.getRequestURI()));
    }

    @PutMapping("/users/{userId}")
    @Operation(summary = "Actualizar usuario")
    @PreAuthorize("hasAuthority('IDENTITY_USER_UPDATE')")
    public ResponseEntity<ApiResponseDto<ManagedUser>> updateUser(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        ManagedUser user = useCases.update(mapper.toUpdateUserCommand(currentUserId(jwt), userId, request));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "IDENTITY_USER_UPDATED", "Usuario actualizado correctamente", user, servletRequest.getRequestURI()));
    }

    @PatchMapping("/users/{userId}/status")
    @Operation(summary = "Cambiar estado de usuario")
    @PreAuthorize("hasAuthority('IDENTITY_USER_STATUS')")
    public ResponseEntity<ApiResponseDto<ManagedUser>> changeUserStatus(
        @PathVariable UUID userId,
        @Valid @RequestBody ChangeUserStatusRequest request,
        @AuthenticationPrincipal Jwt jwt,
        HttpServletRequest servletRequest
    ) {
        ManagedUser user = useCases.changeStatus(mapper.toChangeUserStatusCommand(currentUserId(jwt), userId, request));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "IDENTITY_USER_STATUS_UPDATED", "Estado de usuario actualizado correctamente", user, servletRequest.getRequestURI()));
    }

    @PostMapping("/users/{userId}/password")
    @Operation(summary = "Cambiar contraseña de usuario")
    @PreAuthorize("hasAuthority('IDENTITY_USER_PASSWORD_CHANGE')")
    public ResponseEntity<ApiResponseDto<ManagedUser>> changeUserPassword(@PathVariable UUID userId, @Valid @RequestBody ChangeUserPasswordRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        ManagedUser user = useCases.changePassword(mapper.toChangeUserPasswordCommand(currentUserId(jwt), userId, request));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "IDENTITY_USER_PASSWORD_CHANGED", "Contraseña actualizada correctamente", user, servletRequest.getRequestURI()));
    }

    @PutMapping("/users/{userId}/roles")
    @Operation(summary = "Asignar roles a usuario")
    @PreAuthorize("hasAuthority('IDENTITY_USER_ROLE_ASSIGN')")
    public ResponseEntity<ApiResponseDto<ManagedUser>> assignUserRoles(@PathVariable UUID userId, @Valid @RequestBody AssignUserRolesRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        ManagedUser user = useCases.assignRoles(mapper.toAssignUserRolesCommand(currentUserId(jwt), userId, request));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "IDENTITY_USER_ROLES_UPDATED", "Roles de usuario actualizados correctamente", user, servletRequest.getRequestURI()));
    }

    @PutMapping("/users/{userId}/branches")
    @Operation(summary = "Asignar sucursales a usuario")
    @PreAuthorize("hasAuthority('IDENTITY_USER_BRANCH_ASSIGN')")
    public ResponseEntity<ApiResponseDto<ManagedUser>> assignUserBranches(@PathVariable UUID userId, @Valid @RequestBody AssignUserBranchesRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        ManagedUser user = useCases.assignBranches(mapper.toAssignUserBranchesCommand(currentUserId(jwt), userId, request));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "IDENTITY_USER_BRANCHES_UPDATED", "Sucursales de usuario actualizadas correctamente", user, servletRequest.getRequestURI()));
    }

    @GetMapping("/roles")
    @Operation(summary = "Listar roles")
    @PreAuthorize("hasAuthority('IDENTITY_ROLE_READ')")
    public ResponseEntity<ApiResponseDto<List<RoleSummary>>> listRoles(HttpServletRequest servletRequest) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "IDENTITY_ROLES_FOUND", "Roles consultados correctamente", useCases.listRoles(), servletRequest.getRequestURI()));
    }

    @PostMapping("/roles")
    @Operation(summary = "Crear rol")
    @PreAuthorize("hasAuthority('IDENTITY_ROLE_CREATE')")
    public ResponseEntity<ApiResponseDto<RoleDetail>> createRole(@Valid @RequestBody CreateRoleRequest request, HttpServletRequest servletRequest) {
        RoleDetail role = useCases.createRole(mapper.toCreateRoleCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(HttpStatus.CREATED, "IDENTITY_ROLE_CREATED", "Rol creado correctamente", role, servletRequest.getRequestURI()));
    }

    @PutMapping("/roles/{roleId}")
    @Operation(summary = "Actualizar rol")
    @PreAuthorize("hasAuthority('IDENTITY_ROLE_UPDATE')")
    public ResponseEntity<ApiResponseDto<RoleDetail>> updateRole(@PathVariable UUID roleId, @Valid @RequestBody UpdateRoleRequest request, HttpServletRequest servletRequest) {
        RoleDetail role = useCases.updateRole(mapper.toUpdateRoleCommand(roleId, request));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "IDENTITY_ROLE_UPDATED", "Rol actualizado correctamente", role, servletRequest.getRequestURI()));
    }

    @PatchMapping("/roles/{roleId}/status")
    @Operation(summary = "Cambiar estado de rol")
    @PreAuthorize("hasAuthority('IDENTITY_ROLE_STATUS')")
    public ResponseEntity<ApiResponseDto<RoleDetail>> changeRoleStatus(@PathVariable UUID roleId, @Valid @RequestBody ChangeRoleStatusRequest request, HttpServletRequest servletRequest) {
        RoleDetail role = useCases.changeRoleStatus(mapper.toChangeRoleStatusCommand(roleId, request));
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "IDENTITY_ROLE_STATUS_UPDATED", "Estado de rol actualizado correctamente", role, servletRequest.getRequestURI()));
    }

    @PutMapping("/roles/{roleId}/permissions")
    @Operation(summary = "Asignar permisos a rol")
    @PreAuthorize("hasAuthority('IDENTITY_ROLE_PERMISSION_ASSIGN')")
    public ResponseEntity<ApiResponseDto<RoleDetail>> assignRolePermissions(@PathVariable UUID roleId, @Valid @RequestBody AssignRolePermissionsRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
        RoleDetail role = useCases.assignRolePermissions(
            mapper.toAssignRolePermissionsCommand(currentUserId(jwt), roleId, request)
        );
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "IDENTITY_ROLE_PERMISSIONS_UPDATED", "Permisos de rol actualizados correctamente", role, servletRequest.getRequestURI()));
    }

    @GetMapping("/permissions")
    @Operation(summary = "Listar permisos")
    @PreAuthorize("hasAuthority('IDENTITY_PERMISSION_READ')")
    public ResponseEntity<ApiResponseDto<List<PermissionSummary>>> listPermissions(HttpServletRequest servletRequest) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, "IDENTITY_PERMISSIONS_FOUND", "Permisos consultados correctamente", useCases.listPermissions(), servletRequest.getRequestURI()));
    }

    private static UUID currentUserId(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getSubject());
    }
}
