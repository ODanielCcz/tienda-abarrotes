package com.odcc.tienda.modules.purchasing.adapter.config;

import com.odcc.tienda.modules.inventory.application.port.in.CreateInventoryReceiptUseCase;
import com.odcc.tienda.modules.purchasing.application.port.in.PurchaseUseCases;
import com.odcc.tienda.modules.purchasing.application.port.in.SupplierUseCases;
import com.odcc.tienda.modules.purchasing.application.port.out.PurchaseRepositoryPort;
import com.odcc.tienda.modules.purchasing.application.port.out.SupplierRepositoryPort;
import com.odcc.tienda.modules.purchasing.application.usecase.PurchaseService;
import com.odcc.tienda.modules.purchasing.application.usecase.SupplierService;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PurchasingConfiguration {

    @Bean
    SupplierUseCases supplierUseCases(
        SupplierRepositoryPort repository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new SupplierService(repository, transactionRunner, auditPort);
    }

    @Bean
    PurchaseUseCases purchaseUseCases(
        PurchaseRepositoryPort repository,
        CreateInventoryReceiptUseCase inventoryReceiptUseCase,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new PurchaseService(repository, inventoryReceiptUseCase, transactionRunner, auditPort);
    }
}
