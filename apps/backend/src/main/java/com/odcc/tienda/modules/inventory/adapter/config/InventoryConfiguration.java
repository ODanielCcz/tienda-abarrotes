package com.odcc.tienda.modules.inventory.adapter.config;

import com.odcc.tienda.modules.inventory.application.port.in.AdvancedInventoryUseCases;
import com.odcc.tienda.modules.inventory.application.port.in.CreateInventoryReceiptUseCase;
import com.odcc.tienda.modules.inventory.application.port.in.GetInventoryReceiptByIdUseCase;
import com.odcc.tienda.modules.inventory.application.port.in.InventoryQueriesUseCase;
import com.odcc.tienda.modules.inventory.application.port.out.AdvancedInventoryRepositoryPort;
import com.odcc.tienda.modules.inventory.application.port.out.InventoryQueryRepositoryPort;
import com.odcc.tienda.modules.inventory.application.port.out.InventoryReceiptFingerprintPort;
import com.odcc.tienda.modules.inventory.application.port.out.InventoryReceiptRepositoryPort;
import com.odcc.tienda.modules.inventory.application.usecase.AdvancedInventoryService;
import com.odcc.tienda.modules.inventory.application.usecase.CreateInventoryReceiptService;
import com.odcc.tienda.modules.inventory.application.usecase.GetInventoryReceiptByIdService;
import com.odcc.tienda.modules.inventory.application.usecase.InventoryQueriesService;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InventoryConfiguration {

    @Bean
    CreateInventoryReceiptUseCase createInventoryReceiptUseCase(
        InventoryReceiptRepositoryPort repository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort,
        BranchAccessPort branchAccessPort,
        InventoryReceiptFingerprintPort fingerprintPort
    ) {
        return new CreateInventoryReceiptService(repository, transactionRunner, auditPort, branchAccessPort, fingerprintPort);
    }

    @Bean
    GetInventoryReceiptByIdUseCase getInventoryReceiptByIdUseCase(InventoryReceiptRepositoryPort repository, BranchAccessPort branchAccessPort) {
        return new GetInventoryReceiptByIdService(repository, branchAccessPort);
    }

    @Bean
    InventoryQueriesUseCase inventoryQueriesUseCase(InventoryQueryRepositoryPort repository, BranchAccessPort branchAccessPort) {
        return new InventoryQueriesService(repository, branchAccessPort);
    }

    @Bean
    AdvancedInventoryUseCases advancedInventoryUseCases(
        AdvancedInventoryRepositoryPort repository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort,
        BranchAccessPort branchAccessPort
    ) {
        return new AdvancedInventoryService(repository, transactionRunner, auditPort, branchAccessPort);
    }}
