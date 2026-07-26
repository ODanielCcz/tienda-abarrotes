package com.odcc.tienda.shared.web.response;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiResponseDtoTest {

    @Test
    void shouldCreateSuccessfulResponseFromHttpStatus() {
        ApiResponseDto<String> response = ApiResponseDto.success(
            HttpStatus.CREATED,
            "RESOURCE_CREATED",
            "Recurso creado correctamente",
            "resource-data",
            "/api/v1/resources"
        );

        assertNotNull(response.timestamp());
        assertEquals(201, response.status());
        assertEquals("RESOURCE_CREATED", response.code());
        assertEquals(
            "Recurso creado correctamente",
            response.message()
        );
        assertEquals("resource-data", response.data());
        assertNull(response.errors());
        assertEquals("/api/v1/resources", response.path());
    }

    @Test
    void shouldCreateErrorResponseFromHttpStatus() {
        Map<String, String> errors = Map.of(
            "name",
            "El nombre es obligatorio"
        );

        ApiResponseDto<Void> response = ApiResponseDto.error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "La solicitud contiene campos inválidos",
            errors,
            "/api/v1/resources"
        );

        assertEquals(400, response.status());
        assertEquals("VALIDATION_ERROR", response.code());
        assertNull(response.data());
        assertEquals(errors, response.errors());
    }

    @Test
    void shouldRejectErrorStatusForSuccessfulResponse() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ApiResponseDto.success(
                HttpStatus.BAD_REQUEST,
                "INVALID_SUCCESS",
                "Respuesta inválida",
                null,
                "/api/v1/resources"
            )
        );
    }

    @Test
    void shouldRejectSuccessfulStatusForErrorResponse() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ApiResponseDto.error(
                HttpStatus.OK,
                "INVALID_ERROR",
                "Respuesta inválida",
                null,
                "/api/v1/resources"
            )
        );
    }
}
