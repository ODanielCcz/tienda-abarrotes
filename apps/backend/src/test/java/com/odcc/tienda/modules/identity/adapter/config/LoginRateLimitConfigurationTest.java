package com.odcc.tienda.modules.identity.adapter.config;

import com.odcc.tienda.modules.identity.adapter.out.security.InMemoryLoginRateLimiter;
import com.odcc.tienda.modules.identity.application.port.out.LoginRateLimitPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitConfigurationTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                LoginRateLimitConfiguration.class,
                ClockTestConfiguration.class
            );

    @Test
    void shouldUseMemoryProviderByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LoginRateLimitPort.class);
            assertThat(context.getBean(LoginRateLimitPort.class))
                .isInstanceOf(InMemoryLoginRateLimiter.class);
        });
    }

    @Test
    void shouldRejectRedisWithoutAValidBase64Secret() {
        contextRunner
            .withPropertyValues(
                "app.security.login-rate-limit.provider=redis",
                "app.security.login-rate-limit.key-secret-base64=not-base64"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining(
                        "Redis requiere un secreto Base64 de al menos 32 bytes"
                    );
            });
    }

    @Test
    void shouldRejectLimitsBelowOne() {
        contextRunner
            .withPropertyValues(
                "app.security.login-rate-limit.ip-max-failures=0"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("ipMaxFailures");
            });
    }

    @Test
    void shouldRejectMemoryProviderWhenProdProfileIsActive() {
        contextRunner
            .withInitializer(context ->
                context.getEnvironment().setActiveProfiles("prod")
            )
            .withPropertyValues(
                "app.security.login-rate-limit.provider=memory"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining(
                        "El perfil prod requiere el proveedor redis"
                    );
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class ClockTestConfiguration {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
