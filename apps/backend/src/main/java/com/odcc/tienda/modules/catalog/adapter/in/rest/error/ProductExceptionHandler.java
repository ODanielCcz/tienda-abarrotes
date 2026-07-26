package com.odcc.tienda.modules.catalog.adapter.in.rest.error;

import com.odcc.tienda.modules.catalog.adapter.in.rest.ProductController;
import com.odcc.tienda.modules.catalog.adapter.in.rest.ProductPresentationController;
import com.odcc.tienda.modules.catalog.application.exception.ProductBrandNotFoundException;
import com.odcc.tienda.modules.catalog.application.exception.ProductCategoryNotFoundException;
import com.odcc.tienda.modules.catalog.application.exception.ProductNotFoundException;
import com.odcc.tienda.modules.catalog.application.exception.ProductPresentationNotFoundException;
import com.odcc.tienda.modules.catalog.application.exception.ProductPresentationSkuAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.exception.TaxNotFoundException;
import com.odcc.tienda.modules.catalog.application.exception.UnitOfMeasureNotFoundException;
import com.odcc.tienda.modules.catalog.domain.exception.InvalidProductException;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {ProductController.class, ProductPresentationController.class})
public class ProductExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleProductNotFound(ProductNotFoundException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return error(status, "PRODUCT_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(ProductCategoryNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleCategoryNotFound(ProductCategoryNotFoundException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return error(status, "PRODUCT_CATEGORY_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(ProductBrandNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleBrandNotFound(ProductBrandNotFoundException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return error(status, "PRODUCT_BRAND_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(ProductPresentationNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handlePresentationNotFound(ProductPresentationNotFoundException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return error(status, "PRODUCT_PRESENTATION_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(UnitOfMeasureNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleUnitNotFound(UnitOfMeasureNotFoundException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return error(status, "UNIT_OF_MEASURE_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(TaxNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleTaxNotFound(TaxNotFoundException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return error(status, "TAX_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(ProductPresentationSkuAlreadyExistsException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleDuplicatedSku(ProductPresentationSkuAlreadyExistsException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;
        return error(status, "PRODUCT_PRESENTATION_SKU_ALREADY_EXISTS", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidProductException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleInvalidProduct(InvalidProductException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return error(status, "INVALID_PRODUCT", exception.getMessage(), request);
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
