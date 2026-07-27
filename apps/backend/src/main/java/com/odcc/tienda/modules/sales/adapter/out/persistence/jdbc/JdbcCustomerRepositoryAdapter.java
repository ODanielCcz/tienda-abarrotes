package com.odcc.tienda.modules.sales.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.sales.application.model.Customer;
import com.odcc.tienda.modules.sales.application.port.out.CustomerRepositoryPort;
import com.odcc.tienda.modules.sales.application.query.ListCustomersQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcCustomerRepositoryAdapter implements CustomerRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public boolean existsByCode(String customerCode) {
        if (customerCode == null) return false;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sales.customers WHERE customer_code = :code", new MapSqlParameterSource("code", customerCode), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByCodeAndIdNot(String customerCode, UUID customerId) {
        if (customerCode == null) return false;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sales.customers WHERE customer_code = :code AND customer_id <> :id", new MapSqlParameterSource().addValue("code", customerCode).addValue("id", customerId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public Customer save(Customer customer) {
        boolean exists = findById(customer.customerId()).isPresent();
        if (exists) {
            jdbc.update("""
                UPDATE sales.customers
                SET customer_code = :code,
                    customer_type = :type,
                    display_name = :displayName,
                    email = :email,
                    phone = :phone,
                    status = :status,
                    updated_at = :updatedAt
                WHERE customer_id = :id
                """, params(customer));
        } else {
            jdbc.update("""
                INSERT INTO sales.customers (
                    customer_id, customer_code, customer_type, display_name, email, phone,
                    status, created_at, updated_at
                ) VALUES (
                    :id, :code, :type, :displayName, :email, :phone,
                    :status, :createdAt, :updatedAt
                )
                """, params(customer));
        }
        return findById(customer.customerId()).orElseThrow();
    }

    @Override
    public Optional<Customer> findById(UUID customerId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM sales.customers WHERE customer_id = :id", new MapSqlParameterSource("id", customerId), this::mapCustomer));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<Customer> findAll(ListCustomersQuery query) {
        return jdbc.query("""
            SELECT *
            FROM sales.customers
            WHERE (:status IS NULL OR status = :status)
              AND (:customerType IS NULL OR customer_type = :customerType)
              AND (:search IS NULL OR customer_code ILIKE :searchLike OR display_name ILIKE :searchLike OR email ILIKE :searchLike OR phone ILIKE :searchLike)
            ORDER BY display_name
            LIMIT 200
            """, new MapSqlParameterSource()
            .addValue("status", normalize(query == null ? null : query.status()))
            .addValue("customerType", normalize(query == null ? null : query.customerType()))
            .addValue("search", normalizeSearch(query == null ? null : query.search()))
            .addValue("searchLike", searchLike(query == null ? null : query.search())), this::mapCustomer);
    }

    private MapSqlParameterSource params(Customer customer) {
        return new MapSqlParameterSource()
            .addValue("id", customer.customerId())
            .addValue("code", customer.customerCode())
            .addValue("type", customer.customerType())
            .addValue("displayName", customer.displayName())
            .addValue("email", customer.email())
            .addValue("phone", customer.phone())
            .addValue("status", customer.status())
            .addValue("createdAt", Timestamp.from(customer.createdAt()))
            .addValue("updatedAt", Timestamp.from(customer.updatedAt()));
    }

    private Customer mapCustomer(ResultSet rs, int rowNum) throws SQLException {
        return new Customer(
            rs.getObject("customer_id", UUID.class),
            rs.getString("customer_code"),
            rs.getString("customer_type"),
            rs.getString("display_name"),
            rs.getString("email"),
            rs.getString("phone"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private static String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String searchLike(String value) {
        String normalized = normalizeSearch(value);
        return normalized == null ? null : "%" + normalized + "%";
    }
}
