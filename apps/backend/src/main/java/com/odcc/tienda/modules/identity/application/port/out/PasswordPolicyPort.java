package com.odcc.tienda.modules.identity.application.port.out;

public interface PasswordPolicyPort {

    void validate(String username, String rawPassword);
}
