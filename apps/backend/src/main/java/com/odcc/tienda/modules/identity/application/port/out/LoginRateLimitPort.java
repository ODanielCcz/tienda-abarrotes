package com.odcc.tienda.modules.identity.application.port.out;

public interface LoginRateLimitPort {

    void check(String clientAddress, String username);

    default void check(String clientAddress) {
        check(clientAddress, "unknown");
    }

    default void onSuccess(String clientAddress, String username) {
    }
}
