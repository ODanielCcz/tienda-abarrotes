package com.odcc.tienda.shared.web.filter;

import java.io.IOException;

public final class ApiPayloadTooLargeIOException extends IOException {

    public ApiPayloadTooLargeIOException() {
        super("El payload supera el limite de 1 MiB");
    }
}
