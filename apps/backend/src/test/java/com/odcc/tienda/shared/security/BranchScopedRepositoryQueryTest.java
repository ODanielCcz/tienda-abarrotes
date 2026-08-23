package com.odcc.tienda.shared.security;

import com.odcc.tienda.modules.purchasing.adapter.out.persistence.jdbc.JdbcPurchaseRepositoryAdapter;
import com.odcc.tienda.modules.purchasing.application.query.ListPurchasesQuery;
import com.odcc.tienda.modules.sales.adapter.out.persistence.jdbc.JdbcSalesOrderRepositoryAdapter;
import com.odcc.tienda.modules.sales.application.query.ListSalesOrdersQuery;
import com.odcc.tienda.shared.application.authorization.BranchScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BranchScopedRepositoryQueryTest {

    private static final UUID BRANCH_ID = UUID.fromString(
        "a836f5f3-1af7-4f1f-91f5-14b52c83e0e1"
    );

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void salesQueryShouldApplyAuthorizedBranchesBeforeLimit() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());
        JdbcSalesOrderRepositoryAdapter adapter = new JdbcSalesOrderRepositoryAdapter(jdbc);

        adapter.findAll(
            new ListSalesOrdersQuery(null, null, null),
            BranchScope.restricted(Set.of(BRANCH_ID))
        );

        assertScopedBeforeLimit(jdbc);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void purchaseQueryShouldApplyAuthorizedBranchesBeforeLimit() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());
        JdbcPurchaseRepositoryAdapter adapter = new JdbcPurchaseRepositoryAdapter(jdbc);

        adapter.findAll(
            new ListPurchasesQuery(null, null, null),
            BranchScope.restricted(Set.of(BRANCH_ID))
        );

        assertScopedBeforeLimit(jdbc);
    }

    @Test
    void restrictedEmptyScopeShouldNotExecuteAGlobalQuery() {
        NamedParameterJdbcTemplate salesJdbc = mock(NamedParameterJdbcTemplate.class);
        NamedParameterJdbcTemplate purchaseJdbc = mock(NamedParameterJdbcTemplate.class);

        new JdbcSalesOrderRepositoryAdapter(salesJdbc).findAll(
            new ListSalesOrdersQuery(null, null, null),
            BranchScope.restricted(Set.of())
        );
        new JdbcPurchaseRepositoryAdapter(purchaseJdbc).findAll(
            new ListPurchasesQuery(null, null, null),
            BranchScope.restricted(Set.of())
        );

        verifyNoInteractions(salesJdbc, purchaseJdbc);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertScopedBeforeLimit(NamedParameterJdbcTemplate jdbc) {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));

        assertTrue(sql.getValue().indexOf("branch_id IN") < sql.getValue().indexOf("LIMIT 200"));
        assertEquals(List.of(BRANCH_ID), parameters.getValue().getValue("authorizedBranchIds"));
    }
}
