package com.odcc.tienda.modules.identity.adapter.in.rest.error;

import com.odcc.tienda.modules.identity.adapter.in.rest.AuthenticationController;
import com.odcc.tienda.modules.identity.application.exception.InvalidCredentialsException;
import com.odcc.tienda.modules.identity.application.exception.UserNotActiveException;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthenticationController.class)
public class AuthenticationExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleInvalidCredentials(
        InvalidCredentialsException exception,
        HttpServletRequest request
    ) {
        return error(
            HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS",
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(UserNotActiveException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleInactiveUser(
        UserNotActiveException exception,
        HttpServletRequest request
    ) {
        return error(
            HttpStatus.FORBIDDEN,
            "USER_NOT_ACTIVE",
            exception.getMessage(),
            request
        );
    }

    private static ResponseEntity<ApiResponseDto<Void>> error(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(
            ApiResponseDto.error(
                status,
                code,
                message,
                null,
                request.getRequestURI()
            )
        );
    }
}
