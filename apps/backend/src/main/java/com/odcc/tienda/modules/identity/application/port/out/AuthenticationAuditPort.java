package com.odcc.tienda.modules.identity.application.port.out;

import java.util.UUID;

public interface AuthenticationAuditPort {

    void loginSucceeded(UUID userId, String username);

    void loginFailed(UUID userId, String username, String reason);
}
