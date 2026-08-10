package com.odcc.tienda.modules.billing.adapter.config;

import com.odcc.tienda.modules.billing.application.port.in.BillingUseCases;
import com.odcc.tienda.modules.billing.application.port.out.BillingRepositoryPort;
import com.odcc.tienda.modules.billing.application.usecase.BillingService;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BillingConfiguration {

    @Bean
    BillingUseCases billingUseCases(
        BillingRepositoryPort repository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort,
        BranchAccessPort branchAccess
    ) {
        return new BillingService(repository, transactionRunner, auditPort, branchAccess);
    }
}
