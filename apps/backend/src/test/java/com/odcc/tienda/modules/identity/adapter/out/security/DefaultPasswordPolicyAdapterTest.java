package com.odcc.tienda.modules.identity.adapter.out.security;

import com.odcc.tienda.modules.identity.application.exception.WeakPasswordException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultPasswordPolicyAdapterTest {

    private final DefaultPasswordPolicyAdapter policy = new DefaultPasswordPolicyAdapter();

    @Test
    void shouldRejectShortCommonAndUsernameBasedPasswords() {
        assertThrows(
            WeakPasswordException.class,
            () -> policy.validate("cashier", "short")
        );
        assertThrows(
            WeakPasswordException.class,
            () -> policy.validate("cashier", "password123!")
        );
        assertThrows(
            WeakPasswordException.class,
            () -> policy.validate("cashier", "cashier-secure-phrase-2026")
        );
    }

    @Test
    void shouldAcceptLongUnicodePassphrasesWithoutTrimmingThem() {
        assertDoesNotThrow(
            () -> policy.validate("cashier", "Árbol lunar cobre río 2026!")
        );
    }

    @Test
    void shouldRejectPasswordsLongerThanOneHundredTwentyEightCharacters() {
        assertThrows(
            WeakPasswordException.class,
            () -> policy.validate("cashier", "x".repeat(129))
        );
    }
}
