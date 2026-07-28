package com.odcc.tienda.modules.organization.adapter.in.rest.error;

import com.odcc.tienda.modules.organization.adapter.in.rest.OrganizationController;
import com.odcc.tienda.modules.organization.application.exception.OrganizationCodeAlreadyExistsException;
import com.odcc.tienda.modules.organization.application.exception.OrganizationException;
import com.odcc.tienda.modules.organization.application.exception.OrganizationResourceNotFoundException;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OrganizationController.class)
public class OrganizationExceptionHandler {

    @ExceptionHandler(OrganizationResourceNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> notFound(OrganizationResourceNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ORGANIZATION_RESOURCE_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(OrganizationCodeAlreadyExistsException.class)
    public ResponseEntity<ApiResponseDto<Void>> duplicatedCode(OrganizationCodeAlreadyExistsException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ORGANIZATION_CODE_ALREADY_EXISTS", exception.getMessage(), request);
    }

    @ExceptionHandler(OrganizationException.class)
    public ResponseEntity<ApiResponseDto<Void>> invalidOperation(OrganizationException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_ORGANIZATION_OPERATION", exception.getMessage(), request);
    }

    private ResponseEntity<ApiResponseDto<Void>> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiResponseDto.error(status, code, message, null, request.getRequestURI()));
    }
}