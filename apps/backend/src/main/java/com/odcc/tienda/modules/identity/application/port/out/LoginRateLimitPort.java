package com.odcc.tienda.modules.identity.application.port.out;

public interface LoginRateLimitPort {

    void check(String clientAddress);

    default void check(String clientAddress, String username) {
        check(clientAddress);
    }

    default void onFailure(String clientAddress, String username) {
    }

    default void onSuccess(String clientAddress, String username) {
    }
}
