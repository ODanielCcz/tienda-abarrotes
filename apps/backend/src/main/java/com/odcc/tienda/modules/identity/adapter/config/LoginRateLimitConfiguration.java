package com.odcc.tienda.modules.identity.adapter.config;

import com.odcc.tienda.modules.identity.adapter.out.security.InMemoryLoginRateLimiter;
import com.odcc.tienda.modules.identity.adapter.out.security.LoginRateLimitMetrics;
import com.odcc.tienda.modules.identity.adapter.out.security.RateLimitKeyEncoder;
import com.odcc.tienda.modules.identity.adapter.out.security.RedisLoginRateLimiter;
import com.odcc.tienda.modules.identity.application.port.out.LoginRateLimitPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LoginRateLimitProperties.class)
public class LoginRateLimitConfiguration {

    @Bean
    LoginRateLimitMetrics loginRateLimitMetrics(MeterRegistry meterRegistry) {
        return new LoginRateLimitMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(
        name = "app.security.login-rate-limit.provider",
        havingValue = "memory",
        matchIfMissing = true
    )
    LoginRateLimitPort inMemoryLoginRateLimiter(
        Clock clock,
        LoginRateLimitProperties properties,
        LoginRateLimitMetrics metrics
    ) {
        return new InMemoryLoginRateLimiter(
            clock,
            properties,
            metrics
        );
    }

    @Bean
    @ConditionalOnProperty(
        name = "app.security.login-rate-limit.provider",
        havingValue = "redis"
    )
    RateLimitKeyEncoder rateLimitKeyEncoder(
        LoginRateLimitProperties properties
    ) {
        return new RateLimitKeyEncoder(
            properties.namespace(),
            Base64.getDecoder().decode(properties.keySecretBase64())
        );
    }

    @Bean("checkLoginRateLimitScript")
    @ConditionalOnProperty(
        name = "app.security.login-rate-limit.provider",
        havingValue = "redis"
    )
    RedisScript<String> checkLoginRateLimitScript() {
        return RedisScript.of(
            new ClassPathResource("redis/check-rate-limit.lua"),
            String.class
        );
    }

    @Bean("recordLoginFailureScript")
    @ConditionalOnProperty(
        name = "app.security.login-rate-limit.provider",
        havingValue = "redis"
    )
    RedisScript<Long> recordLoginFailureScript() {
        return RedisScript.of(
            new ClassPathResource("redis/record-login-failure.lua"),
            Long.class
        );
    }

    @Bean
    @ConditionalOnProperty(
        name = "app.security.login-rate-limit.provider",
        havingValue = "redis"
    )
    LoginRateLimitPort redisLoginRateLimiter(
        StringRedisTemplate redis,
        @Qualifier("checkLoginRateLimitScript")
        RedisScript<String> checkScript,
        @Qualifier("recordLoginFailureScript")
        RedisScript<Long> failureScript,
        RateLimitKeyEncoder keyEncoder,
        LoginRateLimitProperties properties,
        LoginRateLimitMetrics metrics
    ) {
        return new RedisLoginRateLimiter(
            redis,
            checkScript,
            failureScript,
            keyEncoder,
            properties,
            metrics
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
