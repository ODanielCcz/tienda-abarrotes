package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.application.model.IssuedAccessToken;
import com.odcc.tienda.modules.identity.application.port.out.AccessTokenPort;
import com.odcc.tienda.modules.identity.domain.model.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAccessTokenAdapter implements AccessTokenPort {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    @Override
    public IssuedAccessToken issue(UserAccount userAccount) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject(userAccount.id().toString())
            .claim("username", userAccount.username())
            .claim("display_name", userAccount.displayName())
            .claim("roles", List.copyOf(userAccount.roles()))
            .claim("permissions", List.copyOf(userAccount.permissions()))
            .claim("authorities", List.copyOf(userAccount.authorities()))
            .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder
            .encode(JwtEncoderParameters.from(header, claims))
            .getTokenValue();

        return new IssuedAccessToken(token, expiresAt);
    }
}
