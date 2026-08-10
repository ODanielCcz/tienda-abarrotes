package com.odcc.tienda.modules.purchasing.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.purchasing.application.model.Supplier;
import com.odcc.tienda.modules.purchasing.application.port.out.SupplierRepositoryPort;
import com.odcc.tienda.modules.purchasing.application.query.ListSuppliersQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcSupplierRepositoryAdapter implements SupplierRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public boolean existsByCode(String supplierCode) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM purchasing.suppliers WHERE supplier_code = :code", new MapSqlParameterSource("code", supplierCode), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByCodeAndIdNot(String supplierCode, UUID supplierId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM purchasing.suppliers WHERE supplier_code = :code AND supplier_id <> :id", new MapSqlParameterSource().addValue("code", supplierCode).addValue("id", supplierId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public Supplier save(Supplier supplier) {
        boolean exists = findById(supplier.supplierId()).isPresent();
        if (exists) {
            jdbc.update("""
                UPDATE purchasing.suppliers
                SET supplier_code = :code, legal_name = :legalName, trade_name = :tradeName,
                    tax_id = :taxId, email = :email, phone = :phone, credit_days = :creditDays,
                    status = :status, updated_at = :updatedAt
                WHERE supplier_id = :id
                """, params(supplier));
        } else {
            jdbc.update("""
                INSERT INTO purchasing.suppliers (
                    supplier_id, supplier_code, legal_name, trade_name, tax_id, email, phone,
                    credit_days, status, created_at, updated_at
                ) VALUES (
                    :id, :code, :legalName, :tradeName, :taxId, :email, :phone,
                    :creditDays, :status, :createdAt, :updatedAt
                )
                """, params(supplier));
        }
        return findById(supplier.supplierId()).orElseThrow();
    }

    @Override
    public Optional<Supplier> findById(UUID supplierId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM purchasing.suppliers WHERE supplier_id = :id", new MapSqlParameterSource("id", supplierId), this::mapSupplier));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<Supplier> findAll(ListSuppliersQuery query) {
        String status = normalize(query == null ? null : query.status());
        String search = normalize(query == null ? null : query.search());
        String searchLike = search == null ? null : "%" + search + "%";
        return jdbc.query("""
            SELECT *
            FROM purchasing.suppliers
            WHERE (:status IS NULL OR status = :status)
              AND (:search IS NULL OR supplier_code ILIKE :searchLike OR legal_name ILIKE :searchLike OR trade_name ILIKE :searchLike)
            ORDER BY legal_name
            LIMIT 200
            """, new MapSqlParameterSource()
            .addValue("status", status, Types.VARCHAR)
            .addValue("search", search, Types.VARCHAR)
            .addValue("searchLike", searchLike, Types.VARCHAR), this::mapSupplier);
    }

    private MapSqlParameterSource params(Supplier supplier) {
        return new MapSqlParameterSource()
            .addValue("id", supplier.supplierId())
            .addValue("code", supplier.supplierCode())
            .addValue("legalName", supplier.legalName())
            .addValue("tradeName", supplier.tradeName())
            .addValue("taxId", supplier.taxId())
            .addValue("email", supplier.email())
            .addValue("phone", supplier.phone())
            .addValue("creditDays", supplier.creditDays())
            .addValue("status", supplier.status())
            .addValue("createdAt", supplier.createdAt())
            .addValue("updatedAt", supplier.updatedAt());
    }

    private Supplier mapSupplier(ResultSet rs, int rowNum) throws SQLException {
        return new Supplier(
            rs.getObject("supplier_id", UUID.class),
            rs.getString("supplier_code"),
            rs.getString("legal_name"),
            rs.getString("trade_name"),
            rs.getString("tax_id"),
            rs.getString("email"),
            rs.getString("phone"),
            rs.getInt("credit_days"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}
