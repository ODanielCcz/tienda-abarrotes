package com.odcc.tienda.modules.sync.adapter.in.rest.filter;

import com.odcc.tienda.shared.security.SecurityErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SyncPayloadSizeFilterTest {

    @Test
    void shouldLimitConflictResolutionWhileStreamingWithoutContentLength() {
        assertStreamingPayloadIsLimited(
            "",
            "/api/v1/sync/conflicts/8dd631ef-a29e-43fe-8e75-b72133e73ca0/resolve"
        );
    }

    @Test
    void shouldLimitInboxWithContextPathAndMatrixParameterWithoutContentLength() {
        assertStreamingPayloadIsLimited(
            "/store",
            "/store/api/v1/sync/inbox;client=mobile"
        );
    }

    @Test
    void shouldLimitConflictResolutionWithMatrixParameterWithoutContentLength() {
        assertStreamingPayloadIsLimited(
            "",
            "/api/v1/sync/conflicts/8dd631ef-a29e-43fe-8e75-b72133e73ca0/resolve;client=mobile"
        );
    }

    private static void assertStreamingPayloadIsLimited(String contextPath, String requestUri) {
        @SuppressWarnings("unchecked")
        ObjectProvider<SecurityErrorWriter> errorWriterProvider = mock(ObjectProvider.class);
        SyncPayloadSizeFilter filter = new SyncPayloadSizeFilter(errorWriterProvider);
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override public int getContentLength() { return -1; }
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setMethod("POST");
        request.setContextPath(contextPath);
        request.setRequestURI(requestUri);
        request.setContent("a".repeat(256 * 1024 + 1).getBytes(StandardCharsets.UTF_8));

        assertThrows(SyncPayloadTooLargeIOException.class, () -> filter.doFilter(
            request,
            new MockHttpServletResponse(),
            (limitedRequest, response) -> limitedRequest.getInputStream().readAllBytes()
        ));
    }
}
