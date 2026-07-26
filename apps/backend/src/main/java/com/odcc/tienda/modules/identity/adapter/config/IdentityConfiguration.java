package com.odcc.tienda.modules.identity.adapter.config;

import com.odcc.tienda.modules.identity.application.port.in.LoginUseCase;
import com.odcc.tienda.modules.identity.application.port.out.AccessTokenPort;
import com.odcc.tienda.modules.identity.application.port.out.AuthenticationAuditPort;
import com.odcc.tienda.modules.identity.application.port.out.PasswordVerificationPort;
import com.odcc.tienda.modules.identity.application.port.out.UserAccountPort;
import com.odcc.tienda.modules.identity.application.usecase.LoginService;
import com.odcc.tienda.modules.identity.adapter.out.bootstrap.BootstrapAdminProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class IdentityConfiguration {

    @Bean
    LoginUseCase loginUseCase(
        UserAccountPort userAccountPort,
        PasswordVerificationPort passwordVerificationPort,
        AccessTokenPort accessTokenPort,
        AuthenticationAuditPort authenticationAuditPort
    ) {
        return new LoginService(
            userAccountPort,
            passwordVerificationPort,
            accessTokenPort,
            authenticationAuditPort
        );
    }
}
