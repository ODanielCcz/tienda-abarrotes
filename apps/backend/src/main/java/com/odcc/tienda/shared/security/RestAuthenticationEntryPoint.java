package com.odcc.tienda.shared.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorWriter errorWriter;

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException, ServletException {
        boolean revoked = containsTokenRevoked(exception);
        errorWriter.write(
            request,
            response,
            HttpStatus.UNAUTHORIZED,
            revoked ? DatabaseJwtStateValidator.TOKEN_REVOKED : "UNAUTHORIZED",
            revoked ? "El token de acceso fue revocado" : "Se requiere un token de acceso válido"
        );
    }

    private static boolean containsTokenRevoked(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof JwtValidationException validationException
                && !validationException.getErrors().isEmpty()
                && validationException.getErrors().stream()
                    .allMatch(oauthError -> DatabaseJwtStateValidator.TOKEN_REVOKED.equals(
                        oauthError.getErrorCode()
                    ))) {
                return true;
            }
            if (current.getMessage() != null
                && current.getMessage().contains(DatabaseJwtStateValidator.TOKEN_REVOKED)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
