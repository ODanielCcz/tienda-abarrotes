package com.odcc.tienda.modules.cash.adapter.in.rest.error;

import com.odcc.tienda.modules.cash.adapter.in.rest.CashSessionController;
import com.odcc.tienda.modules.cash.application.exception.CashException;
import com.odcc.tienda.modules.cash.application.exception.CashSessionAlreadyOpenException;
import com.odcc.tienda.modules.cash.application.exception.CashSessionNotFoundException;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CashSessionController.class)
public class CashExceptionHandler {

    @ExceptionHandler(CashSessionNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> notFound(CashSessionNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "CASH_SESSION_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(CashSessionAlreadyOpenException.class)
    public ResponseEntity<ApiResponseDto<Void>> alreadyOpen(CashSessionAlreadyOpenException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CASH_SESSION_ALREADY_OPEN", exception.getMessage(), request);
    }

    @ExceptionHandler(CashException.class)
    public ResponseEntity<ApiResponseDto<Void>> invalid(CashException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_CASH_OPERATION", exception.getMessage(), request);
    }

    private ResponseEntity<ApiResponseDto<Void>> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiResponseDto.error(status, code, message, null, request.getRequestURI()));
    }
}
