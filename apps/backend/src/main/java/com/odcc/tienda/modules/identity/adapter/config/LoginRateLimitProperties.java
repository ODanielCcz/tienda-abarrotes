package com.odcc.tienda.modules.identity.adapter.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Base64;

@Validated
@ConfigurationProperties("app.security.login-rate-limit")
public record LoginRateLimitProperties(
    @DefaultValue("memory") @NotNull Provider provider,
    @DefaultValue("PT1M") @NotNull @DurationMin(seconds = 1) Duration window,
    @DefaultValue("20") @Min(1) int ipMaxFailures,
    @DefaultValue("5") @Min(1) int pairMaxFailures,
    @DefaultValue("10") @Min(1) int accountMaxFailures,
    @DefaultValue("tienda:auth:rate-limit:v1") @NotBlank String namespace,
    String keySecretBase64,
    @DefaultValue("PT5S") @NotNull @DurationMin(seconds = 1)
    Duration unavailableRetryAfter
) {

    public enum Provider {
        MEMORY,
        REDIS
    }

    @AssertTrue(message = "Redis requiere un secreto Base64 de al menos 32 bytes")
    public boolean isRedisSecretValid() {
        if (provider != Provider.REDIS) {
            return true;
        }

        try {
            return keySecretBase64 != null
                && Base64.getDecoder().decode(keySecretBase64).length >= 32;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
