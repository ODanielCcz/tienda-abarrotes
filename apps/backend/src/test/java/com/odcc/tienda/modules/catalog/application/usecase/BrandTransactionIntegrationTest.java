package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.TestcontainersConfiguration;
import com.odcc.tienda.modules.catalog.application.command.CreateBrandCommand;
import com.odcc.tienda.modules.catalog.application.port.in.CreateBrandUseCase;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class BrandTransactionIntegrationTest {

    @Autowired
    private CreateBrandUseCase createBrandUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private BusinessAuditPort auditPort;

    @Test
    void shouldRollbackBrandWhenAuditFails() {
        willThrow(new IllegalStateException("Audit unavailable"))
            .given(auditPort)
            .record(any());

        assertThrows(
            IllegalStateException.class,
            () -> createBrandUseCase.execute(
                new CreateBrandCommand(
                    "ROLLBACK-AUDIT",
                    "Debe revertirse"
                )
            )
        );

        Integer persisted = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM catalog.brands WHERE code = ?",
            Integer.class,
            "ROLLBACK-AUDIT"
        );

        assertEquals(0, persisted);
    }
}
