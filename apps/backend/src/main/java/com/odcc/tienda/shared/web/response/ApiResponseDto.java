package com.odcc.tienda.shared.web.response;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Objects;

public record ApiResponseDto<T>(
    Instant timestamp,
    int status,
    String code,
    String message,
    T data,
    Object errors,
    String path
) {

    public ApiResponseDto {
        Objects.requireNonNull(
            timestamp,
            "La fecha de respuesta es obligatoria"
        );
        Objects.requireNonNull(
            code,
            "El código de respuesta es obligatorio"
        );
        Objects.requireNonNull(
            message,
            "El mensaje de respuesta es obligatorio"
        );
        Objects.requireNonNull(
            path,
            "La ruta de la petición es obligatoria"
        );

        if (status < 100 || status > 599) {
            throw new IllegalArgumentException(
                "El estado HTTP debe estar entre 100 y 599"
            );
        }

        if (status >= 200 && status < 300 && errors != null) {
            throw new IllegalArgumentException(
                "Una respuesta exitosa no puede contener errores"
            );
        }

        if (status >= 400 && data != null) {
            throw new IllegalArgumentException(
                "Una respuesta de error no puede contener datos"
            );
        }
    }

    public static <T> ApiResponseDto<T> success(
        HttpStatus status,
        String code,
        String message,
        T data,
        String path
    ) {
        Objects.requireNonNull(status, "El estado HTTP es obligatorio");

        if (!status.is2xxSuccessful()) {
            throw new IllegalArgumentException(
                "Una respuesta exitosa requiere un estado HTTP 2xx"
            );
        }

        return new ApiResponseDto<>(
            Instant.now(),
            status.value(),
            code,
            message,
            data,
            null,
            path
        );
    }

    public static ApiResponseDto<Void> error(
        HttpStatus status,
        String code,
        String message,
        Object errors,
        String path
    ) {
        Objects.requireNonNull(status, "El estado HTTP es obligatorio");

        if (!status.isError()) {
            throw new IllegalArgumentException(
                "Una respuesta de error requiere un estado HTTP 4xx o 5xx"
            );
        }

        return new ApiResponseDto<>(
            Instant.now(),
            status.value(),
            code,
            message,
            null,
            errors,
            path
        );
    }
}
