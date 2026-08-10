package com.odcc.tienda.modules.purchasing.adapter.out.persistence.jdbc;

import com.odcc.tienda.TestcontainersConfiguration;
import com.odcc.tienda.modules.purchasing.application.port.out.SupplierRepositoryPort;
import com.odcc.tienda.modules.purchasing.application.query.ListSuppliersQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class JdbcSupplierRepositoryAdapterIntegrationTest {

    @Autowired
    private SupplierRepositoryPort repository;

    @Test
    void shouldListSuppliersWithoutOptionalFilters() {
        assertNotNull(repository.findAll(new ListSuppliersQuery(null, null)));
    }
}
