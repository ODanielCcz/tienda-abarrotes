package com.odcc.tienda.modules.identity.adapter.in.rest.error;

import com.odcc.tienda.modules.identity.adapter.in.rest.UserManagementController;
import com.odcc.tienda.modules.identity.application.exception.BranchNotFoundException;
import com.odcc.tienda.modules.identity.application.exception.IdentityException;
import com.odcc.tienda.modules.identity.application.exception.LastSystemAdminException;
import com.odcc.tienda.modules.identity.application.exception.PermissionNotFoundException;
import com.odcc.tienda.modules.identity.application.exception.RoleCodeAlreadyExistsException;
import com.odcc.tienda.modules.identity.application.exception.RoleNotFoundException;
import com.odcc.tienda.modules.identity.application.exception.SelfDisableNotAllowedException;
import com.odcc.tienda.modules.identity.application.exception.SystemRoleProtectedException;
import com.odcc.tienda.modules.identity.application.exception.UserAlreadyExistsException;
import com.odcc.tienda.modules.identity.application.exception.UserManagementNotFoundException;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = UserManagementController.class)
public class UserManagementExceptionHandler {

    @ExceptionHandler(UserManagementNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleNotFound(UserManagementNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "IDENTITY_USER_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleDuplicate(UserAlreadyExistsException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "IDENTITY_USER_ALREADY_EXISTS", exception.getMessage(), request);
    }

    @ExceptionHandler(RoleCodeAlreadyExistsException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleRoleDuplicate(RoleCodeAlreadyExistsException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "IDENTITY_ROLE_ALREADY_EXISTS", exception.getMessage(), request);
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleRoleNotFound(RoleNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "IDENTITY_ROLE_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(PermissionNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handlePermissionNotFound(PermissionNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "IDENTITY_PERMISSION_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(BranchNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleBranchNotFound(BranchNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "IDENTITY_BRANCH_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(LastSystemAdminException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleLastAdmin(LastSystemAdminException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "IDENTITY_LAST_SYSTEM_ADMIN", exception.getMessage(), request);
    }

    @ExceptionHandler(SelfDisableNotAllowedException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleSelfDisable(SelfDisableNotAllowedException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "IDENTITY_SELF_DISABLE_NOT_ALLOWED", exception.getMessage(), request);
    }

    @ExceptionHandler(SystemRoleProtectedException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleSystemRole(SystemRoleProtectedException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "IDENTITY_SYSTEM_ROLE_PROTECTED", exception.getMessage(), request);
    }

    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleIdentity(IdentityException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "IDENTITY_INVALID_REQUEST", exception.getMessage(), request);
    }

    private static ResponseEntity<ApiResponseDto<Void>> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiResponseDto.error(status, code, message, null, request.getRequestURI()));
    }
}