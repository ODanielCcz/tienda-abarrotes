package com.odcc.tienda.modules.identity.adapter.config;

import com.odcc.tienda.modules.identity.adapter.out.bootstrap.BootstrapAdminProperties;
import com.odcc.tienda.modules.identity.application.port.in.LoginUseCase;
import com.odcc.tienda.modules.identity.application.port.in.UserManagementUseCases;
import com.odcc.tienda.modules.identity.application.port.out.AccessTokenPort;
import com.odcc.tienda.modules.identity.application.port.out.AuthenticationAuditPort;
import com.odcc.tienda.modules.identity.application.port.out.PasswordHashingPort;
import com.odcc.tienda.modules.identity.application.port.out.PasswordVerificationPort;
import com.odcc.tienda.modules.identity.application.port.out.UserAccountPort;
import com.odcc.tienda.modules.identity.application.port.out.LoginRateLimitPort;
import com.odcc.tienda.modules.identity.application.port.out.UserManagementRepositoryPort;
import com.odcc.tienda.modules.identity.application.usecase.LoginService;
import com.odcc.tienda.modules.identity.application.usecase.UserManagementService;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class IdentityConfiguration {

    @Bean
    LoginUseCase loginUseCase(
        UserAccountPort userAccountPort,
        PasswordVerificationPort passwordVerificationPort,
        AccessTokenPort accessTokenPort,
        AuthenticationAuditPort authenticationAuditPort,
        Clock clock,
        LoginRateLimitPort loginRateLimitPort
    ) {
        return new LoginService(
            userAccountPort,
            passwordVerificationPort,
            accessTokenPort,
            authenticationAuditPort,
            clock,
            loginRateLimitPort
        );
    }

    @Bean
    UserManagementUseCases userManagementUseCases(
        UserManagementRepositoryPort repository,
        PasswordHashingPort passwordHashingPort,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort,
        BranchAccessPort branchAccess
    ) {
        return new UserManagementService(repository, passwordHashingPort, transactionRunner, auditPort, branchAccess);
    }
}
