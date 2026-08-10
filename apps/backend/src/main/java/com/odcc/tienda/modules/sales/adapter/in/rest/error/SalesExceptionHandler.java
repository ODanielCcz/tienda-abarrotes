package com.odcc.tienda.modules.sales.adapter.in.rest.error;

import com.odcc.tienda.modules.sales.adapter.in.rest.CustomerController;
import com.odcc.tienda.modules.sales.adapter.in.rest.SalesOrderController;
import com.odcc.tienda.modules.sales.adapter.in.rest.SalesPaymentController;
import com.odcc.tienda.modules.sales.adapter.in.rest.SalesPaymentManagementController;
import com.odcc.tienda.modules.sales.adapter.in.rest.SalesReturnController;
import com.odcc.tienda.modules.sales.application.exception.CustomerCodeAlreadyExistsException;
import com.odcc.tienda.modules.sales.application.exception.CustomerNotFoundException;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.exception.SalesOrderIdempotencyConflictException;
import com.odcc.tienda.modules.sales.application.exception.SalesOrderNotFoundException;
import com.odcc.tienda.modules.sales.application.exception.SalesPaymentIdempotencyConflictException;
import com.odcc.tienda.modules.sales.application.exception.SalesPaymentOverpaidException;
import com.odcc.tienda.modules.sales.application.exception.SalesPaymentNotFoundException;
import com.odcc.tienda.modules.sales.application.exception.SalesReturnNotFoundException;
import com.odcc.tienda.modules.sales.application.exception.SalesReturnAlreadyProcessedException;
import com.odcc.tienda.modules.sales.application.exception.StockInsufficientException;
import com.odcc.tienda.modules.sales.application.exception.PriceNotConfiguredException;
import com.odcc.tienda.modules.sales.application.exception.SalesPriceChangedException;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {SalesOrderController.class, SalesPaymentController.class, SalesPaymentManagementController.class, SalesReturnController.class, CustomerController.class})
public class SalesExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> customerNotFound(CustomerNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(CustomerCodeAlreadyExistsException.class)
    public ResponseEntity<ApiResponseDto<Void>> customerCodeAlreadyExists(CustomerCodeAlreadyExistsException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CUSTOMER_CODE_ALREADY_EXISTS", exception.getMessage(), request);
    }

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

    @ExceptionHandler(PriceNotConfiguredException.class)
    public ResponseEntity<ApiResponseDto<Void>> priceNotConfigured(PriceNotConfiguredException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "PRICE_NOT_CONFIGURED", exception.getMessage(), request);
    }

    @ExceptionHandler(SalesPriceChangedException.class)
    public ResponseEntity<ApiResponseDto<Void>> salesPriceChanged(SalesPriceChangedException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "SALES_PRICE_CHANGED", exception.getMessage(), request);
    }

    @ExceptionHandler(SalesReturnNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> salesReturnNotFound(SalesReturnNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "SALES_RETURN_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(SalesReturnAlreadyProcessedException.class)
    public ResponseEntity<ApiResponseDto<Void>> salesReturnAlreadyProcessed(SalesReturnAlreadyProcessedException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "SALES_RETURN_ALREADY_PROCESSED", exception.getMessage(), request);
    }

    @ExceptionHandler(SalesPaymentNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> paymentNotFound(SalesPaymentNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "SALES_PAYMENT_NOT_FOUND", exception.getMessage(), request);
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
