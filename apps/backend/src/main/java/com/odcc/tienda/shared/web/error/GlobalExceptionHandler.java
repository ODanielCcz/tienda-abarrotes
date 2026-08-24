package com.odcc.tienda.shared.web.error;

import com.odcc.tienda.shared.application.authorization.BranchAccessDeniedException;
import com.odcc.tienda.modules.sync.adapter.in.rest.filter.SyncPayloadTooLargeIOException;
import com.odcc.tienda.shared.web.filter.ApiPayloadTooLargeIOException;
import com.odcc.tienda.shared.web.correlation.CorrelationIdFilter;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleValidation(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        exception
            .getBindingResult()
            .getFieldErrors()
            .forEach(error ->
                errors.putIfAbsent(
                    error.getField(),
                    error.getDefaultMessage()
                )
            );

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "La solicitud contiene campos inválidos",
            errors,
            request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleTypeMismatch(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        return error(
            HttpStatus.BAD_REQUEST,
            "INVALID_PARAMETER",
            "La solicitud contiene un parámetro inválido",
            Map.of(
                exception.getName(),
                "El parámetro tiene un formato inválido"
            ),
            request
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleMissingParameter(
        MissingServletRequestParameterException exception,
        HttpServletRequest request
    ) {
        return error(
            HttpStatus.BAD_REQUEST,
            "MISSING_PARAMETER",
            "La solicitud no contiene todos los parámetros requeridos",
            Map.of(exception.getParameterName(), "El parámetro es obligatorio"),
            request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleUnreadableBody(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        if (hasCause(exception, ApiPayloadTooLargeIOException.class)) {
            return error(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "REQUEST_PAYLOAD_TOO_LARGE",
                "El payload supera el limite configurado",
                null,
                request
            );
        }
        if (hasCause(exception, SyncPayloadTooLargeIOException.class)) {
            return error(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "SYNC_PAYLOAD_TOO_LARGE",
                "El payload Sync supera 256 KiB",
                null,
                request
            );
        }
        return error(
            HttpStatus.BAD_REQUEST,
            "MALFORMED_JSON",
            "El cuerpo de la solicitud no contiene un JSON válido",
            null,
            request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleIllegalArgument(
        IllegalArgumentException exception,
        HttpServletRequest request
    ) {
        return error(
            HttpStatus.BAD_REQUEST,
            "INVALID_PARAMETER",
            exception.getMessage(),
            null,
            request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleUnsupportedMethod(
        HttpRequestMethodNotSupportedException exception,
        HttpServletRequest request
    ) {
        return error(
            HttpStatus.METHOD_NOT_ALLOWED,
            "METHOD_NOT_ALLOWED",
            "El método HTTP no está permitido para esta ruta",
            null,
            request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleResourceNotFound(
        NoResourceFoundException exception,
        HttpServletRequest request
    ) {
        return error(
            HttpStatus.NOT_FOUND,
            "RESOURCE_NOT_FOUND",
            "La ruta solicitada no existe",
            null,
            request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleAccessDenied(
        AccessDeniedException exception,
        HttpServletRequest request
    ) {
        return error(
            HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            "No tienes permiso para realizar esta operación",
            null,
            request
        );
    }

    @ExceptionHandler(BranchAccessDeniedException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleBranchAccessDenied(
        BranchAccessDeniedException exception,
        HttpServletRequest request
    ) {
        return error(
            HttpStatus.FORBIDDEN,
            "BRANCH_ACCESS_DENIED",
            exception.getMessage(),
            null,
            request
        );
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<Void>> handleUnexpectedError(
        Exception exception,
        HttpServletRequest request
    ) {
        String correlationId = CorrelationIdFilter.from(request);

        log.error(
            "Error inesperado al procesar {} {}. correlationId={}",
            request.getMethod(),
            request.getRequestURI(),
            correlationId,
            exception
        );

        return error(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "Ocurrió un error interno. Reporta el identificador de correlación.",
            null,
            request
        );
    }

    private static ResponseEntity<ApiResponseDto<Void>> error(
        HttpStatus status,
        String code,
        String message,
        Object errors,
        HttpServletRequest request
    ) {
        ApiResponseDto<Void> response = ApiResponseDto.error(
            status,
            code,
            message,
            errors,
            request.getRequestURI(),
            CorrelationIdFilter.from(request)
        );

        return ResponseEntity.status(status).body(response);
    }
}

