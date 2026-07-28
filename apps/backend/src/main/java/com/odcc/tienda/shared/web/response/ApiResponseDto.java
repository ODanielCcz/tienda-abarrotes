package com.odcc.tienda.shared.web.response;

import com.odcc.tienda.shared.web.correlation.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Objects;

public record ApiResponseDto<T>(
    Instant timestamp,
    int status,
    String code,
    String reason,
    String message,
    T data,
    Object errors,
    String path,
    String correlationId
) {

    public ApiResponseDto(
        Instant timestamp,
        int status,
        String code,
        String message,
        T data,
        Object errors,
        String path
    ) {
        this(
            timestamp,
            status,
            code,
            reasonPhrase(status),
            message,
            data,
            errors,
            path,
            currentCorrelationId()
        );
    }

    public ApiResponseDto(
        Instant timestamp,
        int status,
        String code,
        String reason,
        String message,
        T data,
        Object errors,
        String path
    ) {
        this(
            timestamp,
            status,
            code,
            reason,
            message,
            data,
            errors,
            path,
            currentCorrelationId()
        );
    }

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
            reason,
            "La razón HTTP de respuesta es obligatoria"
        );
        Objects.requireNonNull(
            message,
            "El mensaje de respuesta es obligatorio"
        );
        Objects.requireNonNull(
            path,
            "La ruta de la petición es obligatoria"
        );
        Objects.requireNonNull(
            correlationId,
            "El identificador de correlación es obligatorio"
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
        int status,
        String message,
        T data,
        String path
    ) {
        return success(
            status,
            message,
            data,
            path,
            currentCorrelationId()
        );
    }

    public static <T> ApiResponseDto<T> success(
        int status,
        String message,
        T data,
        String path,
        String correlationId
    ) {
        if (status < 200 || status > 299) {
            throw new IllegalArgumentException(
                "Una respuesta exitosa requiere un estado HTTP 2xx"
            );
        }

        return new ApiResponseDto<>(
            Instant.now(),
            status,
            reasonPhrase(status),
            reasonPhrase(status),
            message,
            data,
            null,
            path,
            correlationId
        );
    }

    public static <T> ApiResponseDto<T> success(
        HttpStatus status,
        String code,
        String message,
        T data,
        String path
    ) {
        return success(
            status,
            code,
            message,
            data,
            path,
            currentCorrelationId()
        );
    }

    public static <T> ApiResponseDto<T> success(
        HttpStatus status,
        String code,
        String message,
        T data,
        String path,
        String correlationId
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
            status.getReasonPhrase(),
            message,
            data,
            null,
            path,
            correlationId
        );
    }

    public static ApiResponseDto<Void> error(
        int status,
        String message,
        Object errors,
        String path
    ) {
        return error(
            status,
            message,
            errors,
            path,
            currentCorrelationId()
        );
    }

    public static ApiResponseDto<Void> error(
        int status,
        String message,
        Object errors,
        String path,
        String correlationId
    ) {
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException(
                "Una respuesta de error requiere un estado HTTP 4xx o 5xx"
            );
        }

        return new ApiResponseDto<>(
            Instant.now(),
            status,
            reasonPhrase(status),
            reasonPhrase(status),
            message,
            null,
            errors,
            path,
            correlationId
        );
    }

    public static ApiResponseDto<Void> error(
        HttpStatus status,
        String code,
        String message,
        Object errors,
        String path
    ) {
        return error(
            status,
            code,
            message,
            errors,
            path,
            currentCorrelationId()
        );
    }

    public static ApiResponseDto<Void> error(
        HttpStatus status,
        String code,
        String message,
        Object errors,
        String path,
        String correlationId
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
            status.getReasonPhrase(),
            message,
            null,
            errors,
            path,
            correlationId
        );
    }

    private static String reasonPhrase(int status) {
        HttpStatus httpStatus = HttpStatus.resolve(status);

        return httpStatus != null
            ? httpStatus.getReasonPhrase()
            : "Unknown Status Code";
    }

    private static String currentCorrelationId() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

        return correlationId != null && !correlationId.isBlank()
            ? correlationId
            : "unavailable";
    }
}
