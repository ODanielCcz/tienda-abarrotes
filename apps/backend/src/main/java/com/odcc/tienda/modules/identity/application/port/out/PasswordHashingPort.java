package com.odcc.tienda.modules.identity.application.port.out;

public interface PasswordHashingPort {

    String hash(String rawPassword);
}
