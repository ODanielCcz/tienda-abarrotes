package com.odcc.tienda.shared.web.filter;

import com.odcc.tienda.shared.security.SecurityErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class ApiPayloadSizeFilter extends OncePerRequestFilter {

    private static final int DEFAULT_MAX_BYTES = 1024 * 1024;
    private static final String API_PREFIX = "/api/v1/";
    private static final String SYNC_PREFIX = "/api/v1/sync/";
    private static final Set<String> MUTATING_METHODS = Set.of(
        HttpMethod.POST.name(),
        HttpMethod.PUT.name(),
        HttpMethod.PATCH.name()
    );

    private final ObjectProvider<SecurityErrorWriter> errorWriterProvider;
    private final int maxBytes;

    @Autowired
    public ApiPayloadSizeFilter(
        ObjectProvider<SecurityErrorWriter> errorWriterProvider,
        @Value("${app.web.max-json-payload-bytes:1048576}") int maxBytes
    ) {
        this.errorWriterProvider = errorWriterProvider;
        this.maxBytes = Math.max(1, maxBytes);
    }

    ApiPayloadSizeFilter(ObjectProvider<SecurityErrorWriter> errorWriterProvider) {
        this(errorWriterProvider, DEFAULT_MAX_BYTES);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String path = pathWithinApplication(request);
        if (!mustLimit(request.getMethod(), path)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > maxBytes) {
            writeTooLarge(request, response);
            return;
        }
        filterChain.doFilter(new LimitedRequest(request, maxBytes), response);
    }

    private static boolean mustLimit(String method, String path) {
        return MUTATING_METHODS.contains(method)
            && path.startsWith(API_PREFIX)
            && !path.startsWith(SYNC_PREFIX);
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
            ? uri.substring(contextPath.length())
            : uri;
    }

    private void writeTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityErrorWriter writer = errorWriterProvider.getIfAvailable();
        if (writer == null) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "El payload supera el limite de 1 MiB");
            return;
        }
        writer.write(
            request,
            response,
            HttpStatus.PAYLOAD_TOO_LARGE,
            "REQUEST_PAYLOAD_TOO_LARGE",
            "El payload supera el limite configurado"
        );
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final int maxBytes;

        private LimitedRequest(HttpServletRequest request, int maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedServletInputStream(super.getInputStream(), maxBytes);
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final int maxBytes;
        private int consumed;

        private LimitedServletInputStream(ServletInputStream delegate, int maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) count(1);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) count(read);
            return read;
        }

        private void count(int bytes) throws ApiPayloadTooLargeIOException {
            consumed += bytes;
            if (consumed > maxBytes) throw new ApiPayloadTooLargeIOException();
        }

        @Override public boolean isFinished() { return delegate.isFinished(); }
        @Override public boolean isReady() { return delegate.isReady(); }
        @Override public void setReadListener(ReadListener readListener) { delegate.setReadListener(readListener); }
    }
}
