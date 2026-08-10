package com.odcc.tienda.modules.organization.adapter.config;

import com.odcc.tienda.modules.organization.application.port.in.OrganizationUseCases;
import com.odcc.tienda.modules.organization.application.port.out.OrganizationRepositoryPort;
import com.odcc.tienda.modules.organization.application.usecase.OrganizationService;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OrganizationConfiguration {

    @Bean
    OrganizationUseCases organizationUseCases(
        OrganizationRepositoryPort repository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort,
        BranchAccessPort branchAccess
    ) {
        return new OrganizationService(repository, transactionRunner, auditPort, branchAccess);
    }
}
