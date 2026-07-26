package com.odcc.tienda.modules.sales.adapter.config;

import com.odcc.tienda.modules.sales.application.port.in.SalesOrderUseCases;
import com.odcc.tienda.modules.sales.application.port.out.SalesOrderRepositoryPort;
import com.odcc.tienda.modules.sales.application.usecase.SalesOrderService;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SalesConfiguration {

    @Bean
    SalesOrderUseCases salesOrderUseCases(
        SalesOrderRepositoryPort repository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new SalesOrderService(repository, transactionRunner, auditPort);
    }
}
