package com.odcc.tienda.modules.catalog.adapter.in.rest.error;

import com.odcc.tienda.modules.catalog.adapter.in.rest.CategoryController;
import com.odcc.tienda.modules.catalog.application.exception.CategoryCodeAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.exception.CategoryNotFoundException;
import com.odcc.tienda.modules.catalog.application.exception.CategoryParentNotFoundException;
import com.odcc.tienda.modules.catalog.domain.exception.InvalidCategoryException;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CategoryController.class)
public class CategoryExceptionHandler {

    @ExceptionHandler(CategoryCodeAlreadyExistsException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleDuplicatedCode(CategoryCodeAlreadyExistsException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(ApiResponseDto.error(
            status,
            "CATEGORY_CODE_ALREADY_EXISTS",
            exception.getMessage(),
            null,
            request.getRequestURI()
        ));
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleCategoryNotFound(CategoryNotFoundException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(ApiResponseDto.error(
            status,
            "CATEGORY_NOT_FOUND",
            exception.getMessage(),
            null,
            request.getRequestURI()
        ));
    }

    @ExceptionHandler(CategoryParentNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleParentCategoryNotFound(CategoryParentNotFoundException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(ApiResponseDto.error(
            status,
            "CATEGORY_PARENT_NOT_FOUND",
            exception.getMessage(),
            null,
            request.getRequestURI()
        ));
    }

    @ExceptionHandler(InvalidCategoryException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleInvalidCategory(InvalidCategoryException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponseDto.error(
            status,
            "INVALID_CATEGORY",
            exception.getMessage(),
            null,
            request.getRequestURI()
        ));
    }
}