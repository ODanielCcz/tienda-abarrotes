package com.odcc.tienda.modules.identity.application.usecase;

import com.odcc.tienda.modules.identity.application.command.LoginCommand;
import com.odcc.tienda.modules.identity.application.exception.InvalidCredentialsException;
import com.odcc.tienda.modules.identity.application.exception.UserNotActiveException;
import com.odcc.tienda.modules.identity.application.model.AuthenticatedUser;
import com.odcc.tienda.modules.identity.application.model.IssuedAccessToken;
import com.odcc.tienda.modules.identity.application.model.LoginResult;
import com.odcc.tienda.modules.identity.application.port.in.LoginUseCase;
import com.odcc.tienda.modules.identity.application.port.out.AccessTokenPort;
import com.odcc.tienda.modules.identity.application.port.out.AuthenticationAuditPort;
import com.odcc.tienda.modules.identity.application.port.out.PasswordVerificationPort;
import com.odcc.tienda.modules.identity.application.port.out.UserAccountPort;
import com.odcc.tienda.modules.identity.application.port.out.LoginRateLimitPort;
import com.odcc.tienda.modules.identity.domain.model.UserAccount;
import com.odcc.tienda.modules.identity.domain.model.UserAccountStatus;
import lombok.RequiredArgsConstructor;

import java.util.Locale;
import java.util.Objects;
import java.time.Clock;
import java.time.Instant;

@RequiredArgsConstructor
public final class LoginService implements LoginUseCase {

    private final UserAccountPort userAccountPort;
    private final PasswordVerificationPort passwordVerificationPort;
    private final AccessTokenPort accessTokenPort;
    private final AuthenticationAuditPort authenticationAuditPort;
    private final Clock clock;
    private final LoginRateLimitPort loginRateLimitPort;

    @Override
    public LoginResult execute(LoginCommand command) {
        Objects.requireNonNull(command, "El comando de autenticación es obligatorio");
        String username = normalizeUsername(command.username());
        String password = command.password();
        loginRateLimitPort.check(command.clientAddress(), username);

        UserAccount user = userAccountPort.findByUsername(username).orElse(null);
        if (user == null) {
            passwordVerificationPort.matchesDummy(password);
            loginRateLimitPort.onFailure(command.clientAddress(), username);
            throw invalidCredentials(username);
        }

        Instant now = clock.instant();
        if (!passwordVerificationPort.matches(password, user.passwordHash())) {
            userAccountPort.recordFailedLogin(user.id(), now);
            loginRateLimitPort.onFailure(command.clientAddress(), username);
            authenticationAuditPort.loginFailed(
                user.id(),
                username,
                "INVALID_PASSWORD"
            );
            throw new InvalidCredentialsException();
        }

        if (user.status() != UserAccountStatus.ACTIVE) {
            loginRateLimitPort.onFailure(command.clientAddress(), username);
            authenticationAuditPort.loginFailed(
                user.id(),
                username,
                "USER_" + user.status().name()
            );
            throw new UserNotActiveException();
        }

        loginRateLimitPort.onSuccess(command.clientAddress(), username);
        IssuedAccessToken token = accessTokenPort.issue(user);
        userAccountPort.clearLoginFailures(user.id(), now);
        authenticationAuditPort.loginSucceeded(user.id(), username);

        return new LoginResult(
            token.value(),
            "Bearer",
            token.expiresAt(),
            new AuthenticatedUser(
                user.id(),
                user.username(),
                user.displayName(),
                user.roles(),
                user.permissions()
            )
        );
    }

    private InvalidCredentialsException invalidCredentials(String username) {
        authenticationAuditPort.loginFailed(
            null,
            username,
            "USER_NOT_FOUND"
        );
        return new InvalidCredentialsException();
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new InvalidCredentialsException();
        }

        return username.trim().toLowerCase(Locale.ROOT);
    }
}
