package com.odcc.tienda.modules.sync.adapter.config;

import com.odcc.tienda.modules.inventory.application.port.in.AdvancedInventoryUseCases;
import com.odcc.tienda.modules.sales.application.port.in.SalesCartUseCases;
import com.odcc.tienda.modules.sync.application.port.in.SyncUseCases;
import com.odcc.tienda.modules.sync.application.port.out.RequestFingerprintPort;
import com.odcc.tienda.modules.sync.application.port.out.SyncRepositoryPort;
import com.odcc.tienda.modules.sync.application.port.out.SyncRateLimitPort;
import com.odcc.tienda.modules.sync.application.usecase.SyncService;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SyncConfiguration {

    @Bean
    SyncUseCases syncUseCases(
        SyncRepositoryPort repository,
        RequestFingerprintPort fingerprintPort,
        AdvancedInventoryUseCases inventoryUseCases,
        SalesCartUseCases salesCartUseCases,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort,
        SyncRateLimitPort rateLimitPort
    ) {
        return new SyncService(repository, fingerprintPort, inventoryUseCases, salesCartUseCases, transactionRunner, auditPort, rateLimitPort);
    }
}
