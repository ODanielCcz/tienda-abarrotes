package com.odcc.tienda.modules.catalog.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.catalog.application.exception.ProductPresentationSkuAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.port.out.ProductPresentationRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.ProductPresentation;
import com.odcc.tienda.modules.catalog.domain.model.ProductPresentationStatus;
import com.odcc.tienda.shared.infrastructure.persistence.DataIntegrityViolationClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcProductPresentationRepositoryAdapter implements ProductPresentationRepositoryPort {

    private static final String SKU_CONSTRAINT = "product_presentations_sku_key";
    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<ProductPresentation> ROW_MAPPER = (rs, rowNum) -> ProductPresentation.restore(
        rs.getObject("product_presentation_id", UUID.class),
        rs.getObject("product_id", UUID.class),
        rs.getObject("unit_id", UUID.class),
        rs.getObject("tax_id", UUID.class),
        rs.getString("sku"),
        rs.getString("name"),
        rs.getBigDecimal("conversion_factor"),
        rs.getBigDecimal("net_content"),
        rs.getBigDecimal("minimum_stock"),
        ProductPresentationStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public boolean existsBySku(String sku) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM catalog.product_presentations WHERE sku = :sku", new MapSqlParameterSource("sku", sku), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean existsBySkuAndIdNot(String sku, UUID excludedPresentationId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM catalog.product_presentations WHERE sku = :sku AND product_presentation_id <> :id", new MapSqlParameterSource().addValue("sku", sku).addValue("id", excludedPresentationId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean existsUnitById(UUID unitId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM catalog.units_of_measure WHERE unit_id = :id", new MapSqlParameterSource("id", unitId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean existsTaxById(UUID taxId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM catalog.taxes WHERE tax_id = :id", new MapSqlParameterSource("id", taxId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public Optional<ProductPresentation> findById(UUID presentationId) {
        return jdbc.query("SELECT * FROM catalog.product_presentations WHERE product_presentation_id = :id", new MapSqlParameterSource("id", presentationId), ROW_MAPPER).stream().findFirst();
    }

    @Override
    public List<ProductPresentation> findByProductId(UUID productId) {
        return jdbc.query("SELECT * FROM catalog.product_presentations WHERE product_id = :productId ORDER BY name", new MapSqlParameterSource("productId", productId), ROW_MAPPER);
    }

    @Override
    public ProductPresentation save(ProductPresentation presentation) {
        try {
            boolean exists = findById(presentation.getId()).isPresent();
            if (exists) {
                jdbc.update("""
                    UPDATE catalog.product_presentations
                    SET unit_id = :unitId,
                        tax_id = :taxId,
                        sku = :sku,
                        name = :name,
                        conversion_factor = :conversionFactor,
                        net_content = :netContent,
                        minimum_stock = :minimumStock,
                        status = :status,
                        updated_at = :updatedAt
                    WHERE product_presentation_id = :id
                    """, params(presentation));
            } else {
                jdbc.update("""
                    INSERT INTO catalog.product_presentations (
                        product_presentation_id, product_id, unit_id, tax_id, sku, name,
                        conversion_factor, net_content, minimum_stock, status, created_at, updated_at
                    ) VALUES (
                        :id, :productId, :unitId, :taxId, :sku, :name,
                        :conversionFactor, :netContent, :minimumStock, :status, :createdAt, :updatedAt
                    )
                    """, params(presentation));
            }
            return findById(presentation.getId()).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            if (DataIntegrityViolationClassifier.matchesConstraint(exception, SKU_CONSTRAINT)) {
                throw new ProductPresentationSkuAlreadyExistsException(presentation.getSku());
            }
            throw exception;
        }
    }

    private static MapSqlParameterSource params(ProductPresentation presentation) {
        return new MapSqlParameterSource()
            .addValue("id", presentation.getId())
            .addValue("productId", presentation.getProductId())
            .addValue("unitId", presentation.getUnitId())
            .addValue("taxId", presentation.getTaxId())
            .addValue("sku", presentation.getSku())
            .addValue("name", presentation.getName())
            .addValue("conversionFactor", presentation.getConversionFactor())
            .addValue("netContent", presentation.getNetContent())
            .addValue("minimumStock", presentation.getMinimumStock())
            .addValue("status", presentation.getStatus().name())
            .addValue("createdAt", presentation.getCreatedAt())
            .addValue("updatedAt", presentation.getUpdatedAt());
    }
}
