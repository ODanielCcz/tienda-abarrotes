package com.odcc.tienda.modules.sync.adapter.in.rest.filter;

import java.io.IOException;

public final class SyncPayloadTooLargeIOException extends IOException {

    public SyncPayloadTooLargeIOException() {
        super("El payload Sync supera 256 KiB");
    }
}
