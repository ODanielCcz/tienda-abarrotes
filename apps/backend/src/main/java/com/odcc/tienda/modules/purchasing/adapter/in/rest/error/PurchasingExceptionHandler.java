package com.odcc.tienda.modules.purchasing.adapter.in.rest.error;

import com.odcc.tienda.modules.purchasing.adapter.in.rest.PurchaseController;
import com.odcc.tienda.modules.purchasing.adapter.in.rest.SupplierController;
import com.odcc.tienda.modules.purchasing.application.exception.PurchaseItemNotFoundException;
import com.odcc.tienda.modules.purchasing.application.exception.PurchaseNotFoundException;
import com.odcc.tienda.modules.purchasing.application.exception.PurchasingException;
import com.odcc.tienda.modules.purchasing.application.exception.SupplierCodeAlreadyExistsException;
import com.odcc.tienda.modules.purchasing.application.exception.SupplierNotFoundException;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {SupplierController.class, PurchaseController.class})
public class PurchasingExceptionHandler {

    @ExceptionHandler(SupplierNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> supplierNotFound(SupplierNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(PurchaseNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> purchaseNotFound(PurchaseNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "PURCHASE_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(PurchaseItemNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> purchaseItemNotFound(PurchaseItemNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "PURCHASE_ITEM_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(SupplierCodeAlreadyExistsException.class)
    public ResponseEntity<ApiResponseDto<Void>> supplierDuplicated(SupplierCodeAlreadyExistsException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "SUPPLIER_CODE_ALREADY_EXISTS", exception.getMessage(), request);
    }

    @ExceptionHandler(PurchasingException.class)
    public ResponseEntity<ApiResponseDto<Void>> invalidPurchasing(PurchasingException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PURCHASING_OPERATION", exception.getMessage(), request);
    }

    private ResponseEntity<ApiResponseDto<Void>> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiResponseDto.error(status, code, message, null, request.getRequestURI()));
    }
}
