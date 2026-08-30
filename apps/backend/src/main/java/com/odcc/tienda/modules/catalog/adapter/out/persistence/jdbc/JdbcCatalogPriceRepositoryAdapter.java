package com.odcc.tienda.modules.catalog.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.catalog.application.model.CatalogPrice;
import com.odcc.tienda.modules.catalog.application.port.out.CatalogPriceRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcCatalogPriceRepositoryAdapter implements CatalogPriceRepositoryPort {
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<CatalogPrice> findEffectiveGeneralPrices(UUID branchId) {
        return jdbc.query(
            """
                SELECT DISTINCT ON (price.product_presentation_id)
                    price.price_id,
                    price.product_presentation_id,
                    price.branch_id,
                    price.amount,
                    price_list.currency_code
                FROM catalog.prices price
                JOIN catalog.price_lists price_list ON price_list.price_list_id = price.price_list_id
                JOIN catalog.product_presentations presentation
                  ON presentation.product_presentation_id = price.product_presentation_id
                JOIN catalog.products product ON product.product_id = presentation.product_id
                WHERE price_list.code = 'GENERAL'
                  AND price_list.status = 'ACTIVE'
                  AND presentation.status = 'ACTIVE'
                  AND product.status = 'ACTIVE'
                  AND (price.branch_id = :branchId OR price.branch_id IS NULL)
                  AND price.valid_from <= clock_timestamp()
                  AND (price.valid_until IS NULL OR price.valid_until > clock_timestamp())
                ORDER BY price.product_presentation_id,
                    CASE WHEN price.branch_id = :branchId THEN 0 ELSE 1 END,
                    price.valid_from DESC,
                    price.created_at DESC
                """,
            new MapSqlParameterSource("branchId", branchId),
            (resultSet, rowNumber) -> new CatalogPrice(
                resultSet.getObject("price_id", UUID.class),
                resultSet.getObject("product_presentation_id", UUID.class),
                resultSet.getObject("branch_id", UUID.class),
                resultSet.getBigDecimal("amount"),
                resultSet.getString("currency_code").trim()
            )
        );
    }
}
