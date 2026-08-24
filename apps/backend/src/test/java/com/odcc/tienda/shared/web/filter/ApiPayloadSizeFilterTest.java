package com.odcc.tienda.shared.web.filter;

import com.odcc.tienda.shared.security.SecurityErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ApiPayloadSizeFilterTest {

    @Test
    void shouldLimitStreamingJsonPayloadWithoutContentLength() {
        ApiPayloadSizeFilter filter = filter();
        MockHttpServletRequest request = request("/api/v1/sales/orders");
        request.setContent("a".repeat(1024 * 1024 + 1).getBytes(StandardCharsets.UTF_8));

        assertThrows(ApiPayloadTooLargeIOException.class, () -> filter.doFilter(
            request,
            new MockHttpServletResponse(),
            (limitedRequest, response) -> limitedRequest.getInputStream().readAllBytes()
        ));
    }

    @Test
    void shouldLeaveSyncPayloadsToTheirStricterFilter() {
        ApiPayloadSizeFilter filter = filter();
        MockHttpServletRequest request = request("/api/v1/sync/inbox");
        request.setContent("a".repeat(1024 * 1024 + 1).getBytes(StandardCharsets.UTF_8));
        AtomicBoolean invoked = new AtomicBoolean();

        assertDoesNotThrow(() -> filter.doFilter(
            request,
            new MockHttpServletResponse(),
            (unwrappedRequest, response) -> invoked.set(true)
        ));
        assertTrue(invoked.get());
    }

    @SuppressWarnings("unchecked")
    private static ApiPayloadSizeFilter filter() {
        ObjectProvider<SecurityErrorWriter> provider = mock(ObjectProvider.class);
        return new ApiPayloadSizeFilter(provider);
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override public int getContentLength() { return -1; }
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setMethod("POST");
        request.setRequestURI(uri);
        request.setContentType("application/json");
        return request;
    }
}
