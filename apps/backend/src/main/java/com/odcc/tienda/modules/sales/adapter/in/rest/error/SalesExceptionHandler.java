package com.odcc.tienda.modules.sales.adapter.in.rest.error;

import com.odcc.tienda.modules.sales.adapter.in.rest.SalesOrderController;
import com.odcc.tienda.modules.sales.adapter.in.rest.SalesPaymentController;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.exception.SalesOrderIdempotencyConflictException;
import com.odcc.tienda.modules.sales.application.exception.SalesOrderNotFoundException;
import com.odcc.tienda.modules.sales.application.exception.SalesPaymentIdempotencyConflictException;
import com.odcc.tienda.modules.sales.application.exception.SalesPaymentOverpaidException;
import com.odcc.tienda.modules.sales.application.exception.StockInsufficientException;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {SalesOrderController.class, SalesPaymentController.class})
public class SalesExceptionHandler {

    @ExceptionHandler(SalesOrderNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> salesOrderNotFound(SalesOrderNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "SALES_ORDER_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(SalesOrderIdempotencyConflictException.class)
    public ResponseEntity<ApiResponseDto<Void>> idempotencyConflict(SalesOrderIdempotencyConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "SALES_ORDER_IDEMPOTENCY_CONFLICT", exception.getMessage(), request);
    }

    @ExceptionHandler(StockInsufficientException.class)
    public ResponseEntity<ApiResponseDto<Void>> stockInsufficient(StockInsufficientException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "STOCK_INSUFFICIENT", exception.getMessage(), request);
    }

    @ExceptionHandler(SalesPaymentIdempotencyConflictException.class)
    public ResponseEntity<ApiResponseDto<Void>> paymentIdempotencyConflict(SalesPaymentIdempotencyConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "SALES_PAYMENT_IDEMPOTENCY_CONFLICT", exception.getMessage(), request);
    }

    @ExceptionHandler(SalesPaymentOverpaidException.class)
    public ResponseEntity<ApiResponseDto<Void>> paymentOverpaid(SalesPaymentOverpaidException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "SALES_PAYMENT_OVERPAID", exception.getMessage(), request);
    }

    @ExceptionHandler(SalesException.class)
    public ResponseEntity<ApiResponseDto<Void>> invalidSalesOperation(SalesException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_SALES_OPERATION", exception.getMessage(), request);
    }

    private ResponseEntity<ApiResponseDto<Void>> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiResponseDto.error(status, code, message, null, request.getRequestURI()));
    }
}
