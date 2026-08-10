package com.odcc.tienda.modules.identity.application.port.out;

public interface LoginRateLimitPort {

    void check(String clientAddress);
}
