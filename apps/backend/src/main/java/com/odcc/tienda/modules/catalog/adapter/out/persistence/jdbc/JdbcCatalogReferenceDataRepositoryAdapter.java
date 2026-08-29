package com.odcc.tienda.modules.catalog.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.catalog.application.port.out.CatalogReferenceDataRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.CatalogTax;
import com.odcc.tienda.modules.catalog.domain.model.CatalogUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class JdbcCatalogReferenceDataRepositoryAdapter implements CatalogReferenceDataRepositoryPort {
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<CatalogUnit> findAllUnits() {
        return jdbc.query(
            """
                SELECT unit_id, code, name, symbol, quantity_scale
                FROM catalog.units_of_measure
                ORDER BY name, code
                """,
            (resultSet, rowNumber) -> new CatalogUnit(
                resultSet.getObject("unit_id", java.util.UUID.class),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getString("symbol"),
                resultSet.getInt("quantity_scale")
            )
        );
    }

    @Override
    public List<CatalogTax> findAllActiveTaxes() {
        return jdbc.query(
            """
                SELECT tax_id, code, name, rate
                FROM catalog.taxes
                WHERE status = 'ACTIVE'
                ORDER BY name, code
                """,
            (resultSet, rowNumber) -> new CatalogTax(
                resultSet.getObject("tax_id", java.util.UUID.class),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getBigDecimal("rate")
            )
        );
    }
}
