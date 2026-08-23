package com.odcc.tienda.modules.identity.adapter.config;

import com.odcc.tienda.modules.identity.adapter.out.security.InMemoryLoginRateLimiter;
import com.odcc.tienda.modules.identity.application.port.out.LoginRateLimitPort;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LoginRateLimitProperties.class)
public class LoginRateLimitConfiguration {

    @Bean
    @ConditionalOnProperty(
        name = "app.security.login-rate-limit.provider",
        havingValue = "memory",
        matchIfMissing = true
    )
    LoginRateLimitPort inMemoryLoginRateLimiter(
        Clock clock,
        LoginRateLimitProperties properties
    ) {
        return new InMemoryLoginRateLimiter(
            clock,
            properties.ipMaxFailures(),
            properties.pairMaxFailures(),
            properties.accountMaxFailures(),
            properties.window()
        );
    }

    @Bean
    @Profile("prod")
    SmartInitializingSingleton productionProviderGuard(
        LoginRateLimitProperties properties
    ) {
        return () -> {
            if (properties.provider() != LoginRateLimitProperties.Provider.REDIS) {
                throw new IllegalStateException(
                    "El perfil prod requiere el proveedor redis"
                );
            }
        };
    }
}
