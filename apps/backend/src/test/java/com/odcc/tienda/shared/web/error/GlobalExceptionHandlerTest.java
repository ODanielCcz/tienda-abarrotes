package com.odcc.tienda.shared.web.error;

import com.odcc.tienda.shared.web.correlation.CorrelationIdFilter;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturnSafeInternalErrorWithCorrelationId() {
        MockHttpServletRequest request = new MockHttpServletRequest(
            "GET",
            "/api/v1/test"
        );
        request.setAttribute(
            CorrelationIdFilter.ATTRIBUTE_NAME,
            "error-correlation-123"
        );

        ResponseEntity<ApiResponseDto<Void>> response =
            handler.handleUnexpectedError(
                new IllegalStateException("sensitive database detail"),
                request
            );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
        assertFalse(response.getBody().message().contains("sensitive"));
        assertEquals(
            "error-correlation-123",
            ((Map<?, ?>) response.getBody().errors()).get("correlationId")
        );
    }
}
