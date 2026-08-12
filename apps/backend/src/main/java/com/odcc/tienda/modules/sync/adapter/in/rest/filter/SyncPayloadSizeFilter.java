package com.odcc.tienda.modules.sync.adapter.in.rest.filter;

import com.odcc.tienda.shared.security.SecurityErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public final class SyncPayloadSizeFilter extends OncePerRequestFilter {

    private static final int MAX_BYTES = 256 * 1024;
    private static final String INBOX_PATH = "/api/v1/sync/inbox";
    private static final String CONFLICTS_PATH = "/api/v1/sync/conflicts/";
    private static final String RESOLVE_SUFFIX = "/resolve";

    private final ObjectProvider<SecurityErrorWriter> errorWriterProvider;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String pathWithinApplication = pathWithinApplication(request);
        if (!HttpMethod.POST.matches(request.getMethod()) || !isLimitedPath(pathWithinApplication)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > MAX_BYTES) {
            writeTooLarge(request, response);
            return;
        }
        filterChain.doFilter(new LimitedRequest(request), response);
    }

    private static boolean isLimitedPath(String requestUri) {
        if (INBOX_PATH.equals(requestUri)) return true;
        if (!requestUri.startsWith(CONFLICTS_PATH) || !requestUri.endsWith(RESOLVE_SUFFIX)) return false;
        String conflictId = requestUri.substring(CONFLICTS_PATH.length(), requestUri.length() - RESOLVE_SUFFIX.length());
        return !conflictId.isBlank() && conflictId.indexOf('/') < 0;
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)
            ? requestUri.substring(contextPath.length())
            : requestUri;
        StringBuilder normalized = new StringBuilder(path.length());
        boolean matrixParameter = false;
        for (int index = 0; index < path.length(); index++) {
            char character = path.charAt(index);
            if (character == ';') {
                matrixParameter = true;
            } else if (character == '/') {
                matrixParameter = false;
                normalized.append(character);
            } else if (!matrixParameter) {
                normalized.append(character);
            }
        }
        return normalized.toString();
    }

    private void writeTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityErrorWriter errorWriter = errorWriterProvider.getIfAvailable();
        if (errorWriter == null) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "El payload Sync supera 256 KiB");
            return;
        }
        errorWriter.write(
            request,
            response,
            HttpStatus.PAYLOAD_TOO_LARGE,
            "SYNC_PAYLOAD_TOO_LARGE",
            "El payload Sync supera 256 KiB"
        );
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private LimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedServletInputStream(super.getInputStream());
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private int consumed;

        private LimitedServletInputStream(ServletInputStream delegate) {
            this.delegate = delegate;
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

        private void count(int bytes) throws SyncPayloadTooLargeIOException {
            consumed += bytes;
            if (consumed > MAX_BYTES) throw new SyncPayloadTooLargeIOException();
        }

        @Override public boolean isFinished() { return delegate.isFinished(); }
        @Override public boolean isReady() { return delegate.isReady(); }
        @Override public void setReadListener(ReadListener readListener) { delegate.setReadListener(readListener); }
    }
}
