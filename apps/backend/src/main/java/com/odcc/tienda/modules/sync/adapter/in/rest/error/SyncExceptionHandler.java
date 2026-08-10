package com.odcc.tienda.modules.sync.adapter.in.rest.error;

import com.odcc.tienda.modules.sync.adapter.in.rest.SyncController;
import com.odcc.tienda.modules.sync.application.exception.SyncConflictException;
import com.odcc.tienda.modules.sync.application.exception.SyncException;
import com.odcc.tienda.modules.sync.application.exception.SyncNotFoundException;
import com.odcc.tienda.modules.sync.application.exception.SyncPayloadInvalidException;
import com.odcc.tienda.modules.sync.application.exception.SyncRateLimitedException;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SyncController.class)
public class SyncExceptionHandler {

    @ExceptionHandler(SyncNotFoundException.class)
    public ResponseEntity<ApiResponseDto<Void>> notFound(SyncNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "SYNC_RESOURCE_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(SyncConflictException.class)
    public ResponseEntity<ApiResponseDto<Void>> conflict(SyncConflictException exception, HttpServletRequest request) {
        String code = exception.getMessage().contains("idempotencia")
            ? "SYNC_IDEMPOTENCY_CONFLICT"
            : "SYNC_CONFLICT";
        return error(HttpStatus.CONFLICT, code, exception.getMessage(), request);
    }

    @ExceptionHandler(SyncException.class)
    public ResponseEntity<ApiResponseDto<Void>> invalid(SyncException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_SYNC_OPERATION", exception.getMessage(), request);
    }

    @ExceptionHandler(SyncPayloadInvalidException.class)
    public ResponseEntity<ApiResponseDto<Void>> invalidPayload(SyncPayloadInvalidException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "SYNC_PAYLOAD_INVALID", exception.getMessage(), request);
    }

    @ExceptionHandler(SyncRateLimitedException.class)
    public ResponseEntity<ApiResponseDto<Void>> rateLimited(SyncRateLimitedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
            .body(ApiResponseDto.error(
                HttpStatus.TOO_MANY_REQUESTS,
                "SYNC_RATE_LIMITED",
                exception.getMessage(),
                null,
                request.getRequestURI()
            ));
    }

    private ResponseEntity<ApiResponseDto<Void>> error(
        HttpStatus status, String code, String message, HttpServletRequest request
    ) {
        return ResponseEntity.status(status)
            .body(ApiResponseDto.error(status, code, message, null, request.getRequestURI()));
    }
}
