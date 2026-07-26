package com.odcc.tienda.shared.web.correlation;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void shouldPreserveSafeClientCorrelationId()
        throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "client-request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(
            "client-request-123",
            response.getHeader(CorrelationIdFilter.HEADER_NAME)
        );
        assertEquals(
            "client-request-123",
            request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME)
        );
    }

    @Test
    void shouldReplaceUnsafeCorrelationIdWithUuid()
        throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
            CorrelationIdFilter.HEADER_NAME,
            "unsafe value with spaces and\r\nheader"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String correlationId = response.getHeader(
            CorrelationIdFilter.HEADER_NAME
        );

        assertNotNull(correlationId);
        assertEquals(correlationId, UUID.fromString(correlationId).toString());
    }
}
