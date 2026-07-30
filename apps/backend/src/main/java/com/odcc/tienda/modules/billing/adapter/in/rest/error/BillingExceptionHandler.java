package com.odcc.tienda.modules.billing.adapter.in.rest.error;

import com.odcc.tienda.modules.billing.adapter.in.rest.BillingController;
import com.odcc.tienda.modules.billing.adapter.in.rest.CatalogFiscalClassificationController;
import com.odcc.tienda.modules.billing.application.exception.BillingConflictException;
import com.odcc.tienda.modules.billing.application.exception.BillingException;
import com.odcc.tienda.modules.billing.application.exception.BillingNotFoundException;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {BillingController.class, CatalogFiscalClassificationController.class})
public class BillingExceptionHandler {

    @ExceptionHandler(BillingNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> notFound(BillingNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "BILLING_RESOURCE_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(BillingConflictException.class)
    public ResponseEntity<ApiResponseDto<Void>> conflict(BillingConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "BILLING_CONFLICT", exception.getMessage(), request);
    }

    @ExceptionHandler(BillingException.class)
    public ResponseEntity<ApiResponseDto<Void>> invalid(BillingException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_BILLING_OPERATION", exception.getMessage(), request);
    }

    private ResponseEntity<ApiResponseDto<Void>> error(
        HttpStatus status, String code, String message, HttpServletRequest request
    ) {
        return ResponseEntity.status(status)
            .body(ApiResponseDto.error(status, code, message, null, request.getRequestURI()));
    }
}
