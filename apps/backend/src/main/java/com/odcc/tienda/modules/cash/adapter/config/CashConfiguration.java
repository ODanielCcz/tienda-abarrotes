package com.odcc.tienda.modules.cash.adapter.config;

import com.odcc.tienda.modules.cash.application.port.in.CashSessionUseCases;
import com.odcc.tienda.modules.cash.application.port.out.CashSessionRepositoryPort;
import com.odcc.tienda.modules.cash.application.usecase.CashSessionService;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CashConfiguration {

    @Bean
    CashSessionUseCases cashSessionUseCases(
        CashSessionRepositoryPort repository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort,
        BranchAccessPort branchAccessPort
    ) {
        return new CashSessionService(repository, transactionRunner, auditPort, branchAccessPort);
    }
}
