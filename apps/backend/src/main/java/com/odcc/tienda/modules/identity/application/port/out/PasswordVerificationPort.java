package com.odcc.tienda.modules.identity.application.port.out;

public interface PasswordVerificationPort {

    boolean matches(String rawPassword, String encodedPassword);

    boolean matchesDummy(String rawPassword);
}
