package com.odcc.tienda.modules.identity.adapter.out.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public final class RateLimitKeyEncoder {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String namespace;
    private final byte[] secret;

    public RateLimitKeyEncoder(String namespace, byte[] secret) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("El namespace es obligatorio");
        }
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException(
                "El secreto HMAC debe contener al menos 32 bytes"
            );
        }
        this.namespace = namespace.trim();
        this.secret = Arrays.copyOf(secret, secret.length);
    }

    public Keys encode(String clientAddress, String username) {
        String normalizedAddress = normalize(clientAddress);
        String normalizedUsername = normalize(username);

        return new Keys(
            key("ip", "ip\0" + normalizedAddress),
            key(
                "pair",
                "pair\0" + normalizedAddress + '\0' + normalizedUsername
            ),
            key("account", "account\0" + normalizedUsername)
        );
    }

    private String key(String dimension, String message) {
        return namespace + ':' + dimension + ':' + hmacHex(message);
    }

    private String hmacHex(String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(
                mac.doFinal(message.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                "No fue posible derivar la clave de rate limiting",
                exception
            );
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank()
            ? "unknown"
            : value.trim().toLowerCase(Locale.ROOT);
    }

    public record Keys(String ip, String pair, String account) {
        public List<String> asList() {
            return List.of(ip, pair, account);
        }
    }
}
