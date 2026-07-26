package com.odcc.tienda.modules.catalog.adapter.in.rest.error;

import com.odcc.tienda.modules.catalog.adapter.in.rest.BrandController;
import com.odcc.tienda.modules.catalog.application.exception.BrandCodeAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.exception.BrandNotFoundException;
import com.odcc.tienda.modules.catalog.domain.exception.InvalidBrandException;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice(assignableTypes = BrandController.class)
public class BrandExceptionHandler {

    @ExceptionHandler(BrandCodeAlreadyExistsException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleDuplicatedCode(
        BrandCodeAlreadyExistsException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.CONFLICT;

        ApiResponseDto<Void> response = ApiResponseDto.error(
            status,
            "BRAND_CODE_ALREADY_EXISTS",
            exception.getMessage(),
            null,
            request.getRequestURI()
        );

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(BrandNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleBrandNotFound(
        BrandNotFoundException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.NOT_FOUND;

        ApiResponseDto<Void> response = ApiResponseDto.error(
            status,
            "BRAND_NOT_FOUND",
            exception.getMessage(),
            null,
            request.getRequestURI()
        );

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(InvalidBrandException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleInvalidBrand(
        InvalidBrandException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        ApiResponseDto<Void> response = ApiResponseDto.error(
            status,
            "INVALID_BRAND",
            exception.getMessage(),
            null,
            request.getRequestURI()
        );

        return ResponseEntity.status(status).body(response);
    }

}
