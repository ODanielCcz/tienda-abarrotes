package com.odcc.tienda.modules.sales.adapter.config;

import com.odcc.tienda.modules.sales.application.port.in.CustomerUseCases;
import com.odcc.tienda.modules.sales.application.port.in.SalesCartUseCases;
import com.odcc.tienda.modules.sales.application.port.in.SalesOrderUseCases;
import com.odcc.tienda.modules.sales.application.port.in.SalesPaymentUseCases;
import com.odcc.tienda.modules.sales.application.port.in.SalesReturnUseCases;
import com.odcc.tienda.modules.sales.application.port.out.CustomerRepositoryPort;
import com.odcc.tienda.modules.sales.application.port.out.SalesCartRepositoryPort;
import com.odcc.tienda.modules.sales.application.port.out.SalesOrderRepositoryPort;
import com.odcc.tienda.modules.sales.application.port.out.SalesPaymentRepositoryPort;
import com.odcc.tienda.modules.sales.application.port.out.SalesReturnRepositoryPort;
import com.odcc.tienda.modules.sales.application.usecase.CustomerService;
import com.odcc.tienda.modules.sales.application.usecase.SalesCartService;
import com.odcc.tienda.modules.sales.application.usecase.SalesOrderService;
import com.odcc.tienda.modules.sales.application.usecase.SalesPaymentService;
import com.odcc.tienda.modules.sales.application.usecase.SalesReturnService;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SalesConfiguration {

    @Bean
    SalesCartUseCases salesCartUseCases(
        SalesCartRepositoryPort repository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new SalesCartService(repository, transactionRunner, auditPort);
    }

    @Bean
    CustomerUseCases customerUseCases(
        CustomerRepositoryPort repository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new CustomerService(repository, transactionRunner, auditPort);
    }

    @Bean
    SalesOrderUseCases salesOrderUseCases(
        SalesOrderRepositoryPort repository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new SalesOrderService(repository, transactionRunner, auditPort);
    }

    @Bean
    SalesPaymentUseCases salesPaymentUseCases(
        SalesPaymentRepositoryPort repository,
        SalesOrderRepositoryPort salesOrderRepository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new SalesPaymentService(repository, salesOrderRepository, transactionRunner, auditPort);
    }

    @Bean
    SalesReturnUseCases salesReturnUseCases(
        SalesReturnRepositoryPort repository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new SalesReturnService(repository, transactionRunner, auditPort);
    }
}
