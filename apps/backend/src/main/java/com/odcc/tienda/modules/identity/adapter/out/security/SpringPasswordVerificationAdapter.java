package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.application.port.out.PasswordVerificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringPasswordVerificationAdapter
    implements PasswordVerificationPort {

    private static final String DUMMY_BCRYPT_HASH =
        "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return rawPassword != null
            && passwordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean matchesDummy(String rawPassword) {
        return passwordEncoder.matches(
            rawPassword == null ? "" : rawPassword,
            DUMMY_BCRYPT_HASH
        );
    }
}
