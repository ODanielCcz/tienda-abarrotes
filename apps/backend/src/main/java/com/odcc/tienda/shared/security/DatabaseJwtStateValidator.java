package com.odcc.tienda.shared.security;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

        Set<String> tokenAuthorities;
        try {
            List<String> authorityClaims = token.getClaimAsStringList("authorities");
            if (authorityClaims == null) return revoked();
            tokenAuthorities = Set.copyOf(authorityClaims);
        } catch (RuntimeException exception) {
            return revoked();
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource("userId", userId)
            .addValue("authVersion", tokenVersion);
        Integer valid = jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM iam.users
                WHERE user_id = :userId
                  AND status = 'ACTIVE'
                  AND auth_version = :authVersion
                """,
            parameters,
            Integer.class
        );
        if (valid == null || valid != 1) return revoked();

        Set<String> currentAuthorities = new HashSet<>(jdbc.queryForList(
            """
                SELECT effective.authority
                FROM (
                    SELECT 'ROLE_' || role.code AS authority
                    FROM iam.user_roles user_role
                    JOIN iam.roles role ON role.role_id = user_role.role_id
                    WHERE user_role.user_id = :userId
                      AND role.status = 'ACTIVE'
                      AND (
                          user_role.valid_until IS NULL
                          OR user_role.valid_until > clock_timestamp()
                      )

                    UNION

                    SELECT permission.code AS authority
                    FROM iam.user_roles user_role
                    JOIN iam.roles role ON role.role_id = user_role.role_id
                    JOIN iam.role_permissions role_permission
                      ON role_permission.role_id = role.role_id
                    JOIN iam.permissions permission
                      ON permission.permission_id = role_permission.permission_id
                    WHERE user_role.user_id = :userId
                      AND role.status = 'ACTIVE'
                      AND (
                          user_role.valid_until IS NULL
                          OR user_role.valid_until > clock_timestamp()
                      )
                ) effective
                ORDER BY effective.authority
                """,
            parameters,
            String.class
        ));

        return currentAuthorities.equals(tokenAuthorities)
            ? OAuth2TokenValidatorResult.success()
            : revoked();
    }

    private static OAuth2TokenValidatorResult revoked() {
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error(TOKEN_REVOKED, "El token fue revocado", null)
        );
    }
}
