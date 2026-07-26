package com.odcc.tienda.modules.identity.application.port.out;

import com.odcc.tienda.modules.identity.domain.model.UserAccount;

import java.util.Optional;

public interface UserAccountPort {

    Optional<UserAccount> findByUsername(String username);
}
