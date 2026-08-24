package com.odcc.tienda.modules.identity.adapter.out.security;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitKeyEncoderTest {

    private static final String NAMESPACE = "tienda:auth:rate-limit:v1";

    @Test
    void shouldGenerateDeterministicKeysFromIndependentKnownVectors() {
        RateLimitKeyEncoder encoder = new RateLimitKeyEncoder(NAMESPACE, secret());

        RateLimitKeyEncoder.Keys keys = encoder.encode(
            "192.0.2.10",
            "admin@example.com"
        );

        assertThat(keys.ip()).isEqualTo(
            NAMESPACE + ":ip:c248dbf3c4820a2cdb02e26fff8811a2"
                + "b87eea91cc318173b1c3446116e5e9d8"
        );
        assertThat(keys.pair()).isEqualTo(
            NAMESPACE + ":pair:b3873bcb57f285d3b79bd60b1a47a04"
                + "f30714ea3592c97cbbd4dbe2a6494533e"
        );
        assertThat(keys.account()).isEqualTo(
            NAMESPACE + ":account:d7fb798751bbe491dd640ce8045ceeff"
                + "c8b291d544601b87fa0594a4696830dc"
        );
    }

    @Test
    void shouldNormalizeUsernameAndAddress() {
        RateLimitKeyEncoder encoder = new RateLimitKeyEncoder(NAMESPACE, secret());

        RateLimitKeyEncoder.Keys normalized = encoder.encode(
            "192.0.2.10",
            "admin@example.com"
        );
        RateLimitKeyEncoder.Keys mixedCase = encoder.encode(
            " 192.0.2.10 ",
            " Admin@Example.COM "
        );

        assertThat(mixedCase).isEqualTo(normalized);
    }

    @Test
    void shouldNotExposeAddressOrUsername() {
        RateLimitKeyEncoder encoder = new RateLimitKeyEncoder(NAMESPACE, secret());

        RateLimitKeyEncoder.Keys keys = encoder.encode(
            "192.0.2.10",
            "admin@example.com"
        );

        assertThat(keys.asList())
            .allMatch(key -> key.matches(
                "tienda:auth:rate-limit:v1:(ip|pair|account):[0-9a-f]{64}"
            ))
            .noneMatch(key -> key.contains("192.0.2.10"))
            .noneMatch(key -> key.contains("admin@example.com"));
    }

    @Test
    void shouldSeparateIpPairAndAccountDimensions() {
        RateLimitKeyEncoder encoder = new RateLimitKeyEncoder(NAMESPACE, secret());

        RateLimitKeyEncoder.Keys keys = encoder.encode("192.0.2.10", "alice");

        assertThat(keys.asList()).doesNotHaveDuplicates();
        assertThat(keys.asList()).containsExactly(keys.ip(), keys.pair(), keys.account());
    }

    @Test
    void shouldDefensivelyCopyTheSecret() {
        byte[] secret = secret();
        RateLimitKeyEncoder encoder = new RateLimitKeyEncoder(NAMESPACE, secret);
        RateLimitKeyEncoder.Keys beforeMutation = encoder.encode("192.0.2.10", "alice");
        secret[0] = 99;

        assertThat(encoder.encode("192.0.2.10", "alice"))
            .isEqualTo(beforeMutation);
    }

    @Test
    void shouldRejectSecretsShorterThanThirtyTwoBytes() {
        assertThatThrownBy(() -> new RateLimitKeyEncoder(NAMESPACE, new byte[31]))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("32 bytes");
    }

    private static byte[] secret() {
        return IntStream.range(0, 32)
            .collect(
                () -> new byte[32],
                (bytes, value) -> bytes[value] = (byte) value,
                (left, right) -> { }
            );
    }
}
