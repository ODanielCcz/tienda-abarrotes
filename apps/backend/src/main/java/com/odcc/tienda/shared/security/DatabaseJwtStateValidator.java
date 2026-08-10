package com.odcc.tienda.shared.security;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public final class DatabaseJwtStateValidator implements OAuth2TokenValidator<Jwt> {

    static final String TOKEN_REVOKED = "TOKEN_REVOKED";

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        UUID userId;
        Long tokenVersion;
        try {
            userId = UUID.fromString(token.getSubject());
            Object versionClaim = token.getClaim("auth_version");
            tokenVersion = versionClaim instanceof Number number ? number.longValue() : null;
        } catch (RuntimeException exception) {
            return revoked();
        }
        if (tokenVersion == null) return revoked();

        Integer valid = jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM iam.users
                WHERE user_id = :userId
                  AND status = 'ACTIVE'
                  AND auth_version = :authVersion
                """,
            new MapSqlParameterSource("userId", userId)
                .addValue("authVersion", tokenVersion),
            Integer.class
        );
        return valid != null && valid == 1
            ? OAuth2TokenValidatorResult.success()
            : revoked();
    }

    private static OAuth2TokenValidatorResult revoked() {
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error(TOKEN_REVOKED, "El token fue revocado", null)
        );
    }
}
