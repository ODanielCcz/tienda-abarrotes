package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.application.exception.WeakPasswordException;
import com.odcc.tienda.modules.identity.application.port.out.PasswordPolicyPort;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public final class DefaultPasswordPolicyAdapter implements PasswordPolicyPort {

    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 128;
    private static final Set<String> COMMON_PASSWORDS = Set.of(
        "123456789012",
        "admin123456!",
        "changeme123!",
        "password123!",
        "qwerty123456",
        "temporary123!",
        "tienda123456!"
    );

    @Override
    public void validate(String username, String rawPassword) {
        if (rawPassword == null) {
            throw weak("La contraseña es obligatoria");
        }
        if (rawPassword.length() < MIN_LENGTH) {
            throw weak("La contraseña debe contener al menos 12 caracteres");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            throw weak("La contraseña no puede exceder 128 caracteres");
        }

        String normalizedPassword = rawPassword.toLowerCase(Locale.ROOT);
        if (COMMON_PASSWORDS.contains(normalizedPassword)) {
            throw weak("La contraseña es demasiado común");
        }

        String normalizedUsername = username == null
            ? ""
            : username.trim().toLowerCase(Locale.ROOT);
        if (normalizedUsername.length() >= 3
            && normalizedPassword.contains(normalizedUsername)) {
            throw weak("La contraseña no debe contener el nombre de usuario");
        }
    }

    private static WeakPasswordException weak(String message) {
        return new WeakPasswordException(message);
    }
}
