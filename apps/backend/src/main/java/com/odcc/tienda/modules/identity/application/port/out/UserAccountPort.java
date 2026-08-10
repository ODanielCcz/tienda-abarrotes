package com.odcc.tienda.modules.identity.application.port.out;

import com.odcc.tienda.modules.identity.domain.model.UserAccount;

import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

public interface UserAccountPort {

    Optional<UserAccount> findByUsername(String username);

    void recordFailedLogin(UUID userId, Instant lockedUntilAtThreshold);

    void clearLoginFailures(UUID userId, Instant loginAt);
}
