package com.odcc.tienda.modules.identity.application.usecase;

import com.odcc.tienda.modules.identity.application.command.LoginCommand;
import com.odcc.tienda.modules.identity.application.exception.InvalidCredentialsException;
import com.odcc.tienda.modules.identity.application.exception.UserNotActiveException;
import com.odcc.tienda.modules.identity.application.exception.UserTemporarilyLockedException;
import com.odcc.tienda.modules.identity.application.model.IssuedAccessToken;
import com.odcc.tienda.modules.identity.application.model.LoginResult;
import com.odcc.tienda.modules.identity.application.port.out.AccessTokenPort;
import com.odcc.tienda.modules.identity.application.port.out.AuthenticationAuditPort;
import com.odcc.tienda.modules.identity.application.port.out.PasswordVerificationPort;
import com.odcc.tienda.modules.identity.application.port.out.UserAccountPort;
import com.odcc.tienda.modules.identity.domain.model.UserAccount;
import com.odcc.tienda.modules.identity.domain.model.UserAccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginServiceTest {

    private static final UUID USER_ID = UUID.fromString(
        "b36f4e12-1404-4a39-9e14-cb15dd64027e"
    );

    private FakeUserAccountPort userAccountPort;
    private FakeAuthenticationAuditPort auditPort;
    private LoginService service;
    private Clock clock;
    private TrackingPasswordVerificationPort passwordPort;

    @BeforeEach
    void setUp() {
        userAccountPort = new FakeUserAccountPort(activeUser());
        clock = Clock.fixed(Instant.parse("2026-08-09T18:00:00Z"), ZoneOffset.UTC);
        auditPort = new FakeAuthenticationAuditPort();
        passwordPort = new TrackingPasswordVerificationPort();
        AccessTokenPort tokenPort = user -> new IssuedAccessToken(
            "signed.jwt.token",
            Instant.parse("2026-07-24T08:30:00Z")
        );

        service = new LoginService(
            userAccountPort,
            passwordPort,
            tokenPort,
            auditPort,
            clock,
            clientAddress -> { }
        );
    }

    @Test
    void shouldAuthenticateActiveUserAndReturnAuthorities() {
        LoginResult result = service.execute(
            new LoginCommand(" ADMIN ", "correct-password")
        );

        assertEquals("signed.jwt.token", result.accessToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(USER_ID, result.user().id());
        assertEquals(Set.of("SYSTEM_ADMIN"), result.user().roles());
        assertEquals(
            Set.of("CATALOG_BRAND_READ"),
            result.user().permissions()
        );
        assertEquals(List.of("SUCCESS:admin"), auditPort.events);
    }

    @Test
    void shouldRejectInvalidPasswordWithoutIssuingToken() {
        assertThrows(
            InvalidCredentialsException.class,
            () -> service.execute(
                new LoginCommand("admin", "wrong-password")
            )
        );

        assertEquals(List.of("FAILURE:admin:INVALID_PASSWORD"), auditPort.events);
        assertEquals(1, passwordPort.realMatches);
        assertEquals(0, passwordPort.dummyMatches);
    }

    @Test
    void shouldRejectUnknownUserWithoutRevealingItsExistence() {
        userAccountPort.user = null;

        InvalidCredentialsException exception = assertThrows(
            InvalidCredentialsException.class,
            () -> service.execute(
                new LoginCommand("unknown", "any-password")
            )
        );

        assertEquals(
            "Las credenciales proporcionadas no son válidas",
            exception.getMessage()
        );
        assertEquals(0, passwordPort.realMatches);
        assertEquals(1, passwordPort.dummyMatches);
        assertEquals(List.of("FAILURE:unknown:USER_NOT_FOUND"), auditPort.events);
    }

    @Test
    void shouldRejectUserThatIsNotActiveAfterPasswordValidation() {
        userAccountPort.user = new UserAccount(
            USER_ID,
            "admin",
            "stored-hash",
            "Administrador",
            UserAccountStatus.LOCKED,
            Set.of("SYSTEM_ADMIN"),
            Set.of("CATALOG_BRAND_READ")
        );

        assertThrows(
            UserNotActiveException.class,
            () -> service.execute(
                new LoginCommand("admin", "correct-password")
            )
        );

        assertEquals(List.of("FAILURE:admin:USER_LOCKED"), auditPort.events);
    }

    @Test
    void shouldNotPersistentlyLockAccountAfterFiveInvalidPasswords() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(
                InvalidCredentialsException.class,
                () -> service.execute(new LoginCommand("admin", "wrong-password"))
            );
        }

        assertEquals(5, userAccountPort.user.failedLoginAttempts());
        assertEquals(null, userAccountPort.user.lockedUntil());

        service.execute(new LoginCommand("admin", "correct-password"));
        assertEquals(0, userAccountPort.user.failedLoginAttempts());
    }

    @Test
    void shouldClearFailuresAfterSuccessfulLogin() {
        userAccountPort.user = activeUser().withAuthenticationState(
            2,
            Instant.parse("2026-08-09T17:59:00Z")
        );

        service.execute(new LoginCommand("admin", "correct-password"));

        assertEquals(0, userAccountPort.user.failedLoginAttempts());
        assertEquals(null, userAccountPort.user.lockedUntil());
    }

    private static UserAccount activeUser() {
        return new UserAccount(
            USER_ID,
            "admin",
            "stored-hash",
            "Administrador",
            UserAccountStatus.ACTIVE,
            Set.of("SYSTEM_ADMIN"),
            Set.of("CATALOG_BRAND_READ")
        );
    }
    private static final class TrackingPasswordVerificationPort
        implements PasswordVerificationPort {

        private int realMatches;
        private int dummyMatches;

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            realMatches++;
            return "correct-password".equals(rawPassword)
                && "stored-hash".equals(encodedPassword);
        }

        @Override
        public boolean matchesDummy(String rawPassword) {
            dummyMatches++;
            return false;
        }
    }


    private static final class FakeUserAccountPort implements UserAccountPort {

        private UserAccount user;

        private FakeUserAccountPort(UserAccount user) {
            this.user = user;
        }

        @Override
        public Optional<UserAccount> findByUsername(String username) {
            return Optional.ofNullable(user)
                .filter(account -> account.username().equals(username));
        }

        @Override
        public void recordFailedLogin(UUID userId, Instant failedAt) {
            int attempts = user.failedLoginAttempts() + 1;
            user = user.withAuthenticationState(attempts, null);
        }

        @Override
        public void clearLoginFailures(UUID userId, Instant loginAt) {
            user = user.withAuthenticationState(0, null);
        }
    }

    private static final class FakeAuthenticationAuditPort
        implements AuthenticationAuditPort {

        private final List<String> events = new ArrayList<>();

        @Override
        public void loginSucceeded(UUID userId, String username) {
            events.add("SUCCESS:" + username);
        }

        @Override
        public void loginFailed(UUID userId, String username, String reason) {
            events.add("FAILURE:" + username + ":" + reason);
        }
    }
}
