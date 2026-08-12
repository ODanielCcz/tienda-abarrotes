package com.odcc.tienda.modules.sales.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.sales.application.command.UpsertSalesCartCommand;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcSalesCartRepositoryAdapterTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @InjectMocks
    private JdbcSalesCartRepositoryAdapter repository;

    @Test
    void shouldStopBeforeReplacingItemsWhenCartRowCannotBeClaimed() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        UpsertSalesCartCommand command = new UpsertSalesCartCommand(
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "MXN",
            Instant.parse("2030-01-01T00:00:00Z"),
            List.of(new UpsertSalesCartCommand.Item(
                UUID.randomUUID(), BigDecimal.ONE, BigDecimal.TEN
            ))
        );

        SalesException exception = assertThrows(SalesException.class, () -> repository.upsert(command));

        assertEquals("El carrito no pertenece al dispositivo, sucursal o estado editable", exception.getMessage());
        verify(jdbc, times(1)).update(anyString(), any(MapSqlParameterSource.class));
    }
}
