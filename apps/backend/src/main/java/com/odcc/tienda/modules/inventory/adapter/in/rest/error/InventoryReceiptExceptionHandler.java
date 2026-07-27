package com.odcc.tienda.modules.inventory.adapter.in.rest.error;

import com.odcc.tienda.modules.inventory.adapter.in.rest.AdvancedInventoryController;
import com.odcc.tienda.modules.inventory.adapter.in.rest.InventoryQueryController;
import com.odcc.tienda.modules.inventory.adapter.in.rest.InventoryReceiptController;
import com.odcc.tienda.modules.inventory.application.exception.InventoryReceiptAlreadyExistsException;
import com.odcc.tienda.modules.inventory.application.exception.InventoryReceiptException;
import com.odcc.tienda.modules.inventory.application.exception.InventoryReceiptNotFoundException;
import com.odcc.tienda.modules.inventory.application.exception.InventoryResourceNotFoundException;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {InventoryReceiptController.class, InventoryQueryController.class, AdvancedInventoryController.class})
public class InventoryReceiptExceptionHandler {

    @ExceptionHandler(InventoryReceiptNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleNotFound(InventoryReceiptNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "INVENTORY_RECEIPT_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(InventoryResourceNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleResourceNotFound(InventoryResourceNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "INVENTORY_RESOURCE_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(InventoryReceiptAlreadyExistsException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleIdempotencyConflict(InventoryReceiptAlreadyExistsException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "INVENTORY_RECEIPT_IDEMPOTENCY_CONFLICT", exception.getMessage(), request);
    }

    @ExceptionHandler(InventoryReceiptException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleInvalidReceipt(InventoryReceiptException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INVENTORY_RECEIPT", exception.getMessage(), request);
    }

    private ResponseEntity<ApiResponseDto<Void>> error(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(ApiResponseDto.error(
            status,
            code,
            message,
            null,
            request.getRequestURI()
        ));
    }
}
