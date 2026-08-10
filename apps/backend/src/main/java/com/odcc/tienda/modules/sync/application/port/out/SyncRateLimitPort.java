package com.odcc.tienda.modules.sync.application.port.out;

import java.util.UUID;

public interface SyncRateLimitPort {

    void check(UUID deviceId);
}
