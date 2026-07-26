package com.odcc.tienda.modules.inventory.adapter.config;

import com.odcc.tienda.modules.inventory.application.port.in.CreateInventoryReceiptUseCase;
import com.odcc.tienda.modules.inventory.application.port.in.GetInventoryReceiptByIdUseCase;
import com.odcc.tienda.modules.inventory.application.port.in.InventoryQueriesUseCase;
import com.odcc.tienda.modules.inventory.application.port.out.InventoryQueryRepositoryPort;
import com.odcc.tienda.modules.inventory.application.port.out.InventoryReceiptRepositoryPort;
import com.odcc.tienda.modules.inventory.application.usecase.CreateInventoryReceiptService;
import com.odcc.tienda.modules.inventory.application.usecase.GetInventoryReceiptByIdService;
import com.odcc.tienda.modules.inventory.application.usecase.InventoryQueriesService;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InventoryConfiguration {

    @Bean
    CreateInventoryReceiptUseCase createInventoryReceiptUseCase(
        InventoryReceiptRepositoryPort repository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new CreateInventoryReceiptService(repository, transactionRunner, auditPort);
    }

    @Bean
    GetInventoryReceiptByIdUseCase getInventoryReceiptByIdUseCase(InventoryReceiptRepositoryPort repository) {
        return new GetInventoryReceiptByIdService(repository);
    }

    @Bean
    InventoryQueriesUseCase inventoryQueriesUseCase(InventoryQueryRepositoryPort repository) {
        return new InventoryQueriesService(repository);
    }
}
