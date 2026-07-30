package com.odcc.tienda.modules.billing.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.billing.application.command.BillingCommands.CreateFiscalDocumentCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.CreateFiscalProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.CreateIssuerProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateFiscalProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateIssuerProfileCommand;
import com.odcc.tienda.modules.billing.application.exception.BillingConflictException;
import com.odcc.tienda.modules.billing.application.exception.BillingNotFoundException;
import com.odcc.tienda.modules.billing.application.model.BillingModels.FiscalDocument;
import com.odcc.tienda.modules.billing.application.model.BillingModels.FiscalDocumentItem;
import com.odcc.tienda.modules.billing.application.model.BillingModels.FiscalDocumentSource;
import com.odcc.tienda.modules.billing.application.model.BillingModels.FiscalDocumentSourceItem;
import com.odcc.tienda.modules.billing.application.model.BillingModels.FiscalProfile;
import com.odcc.tienda.modules.billing.application.model.BillingModels.IssuerProfile;
import com.odcc.tienda.modules.billing.application.port.out.BillingRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcBillingRepositoryAdapter implements BillingRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public boolean branchIsActive(UUID branchId) {
        return exists("SELECT EXISTS(SELECT 1 FROM organization.branches WHERE branch_id = :id AND status = 'ACTIVE')", branchId);
    }

    @Override
    public boolean customerIsActive(UUID customerId) {
        return exists("SELECT EXISTS(SELECT 1 FROM sales.customers WHERE customer_id = :id AND status = 'ACTIVE')", customerId);
    }

    @Override
    public boolean activeIssuerExists(UUID branchId, UUID excludedIssuerProfileId) {
        Boolean value = jdbc.queryForObject("""
            SELECT EXISTS(
                SELECT 1 FROM billing.issuer_profiles
                WHERE branch_id = :branchId
                  AND status = 'ACTIVE'
                  AND (CAST(:excludedId AS uuid) IS NULL OR issuer_profile_id <> :excludedId)
            )
            """, new MapSqlParameterSource("branchId", branchId).addValue("excludedId", excludedIssuerProfileId), Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    @Override
    public IssuerProfile createIssuerProfile(CreateIssuerProfileCommand command) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                INSERT INTO billing.issuer_profiles (
                    issuer_profile_id, branch_id, rfc, legal_name, postal_code,
                    fiscal_regime_code, default_series, status
                ) VALUES (
                    :id, :branchId, :rfc, :legalName, :postalCode,
                    :fiscalRegimeCode, :defaultSeries, 'ACTIVE'
                )
                """, issuerParameters(id, command.branchId(), command.rfc(), command.legalName(),
                command.postalCode(), command.fiscalRegimeCode(), command.defaultSeries()));
            return findIssuerProfile(id).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new BillingConflictException("Ya existe un perfil emisor con esos datos");
        }
    }

    @Override
    public List<IssuerProfile> listIssuerProfiles(UUID branchId, String status) {
        return jdbc.query("""
            SELECT issuer_profile_id, branch_id, rfc, legal_name, postal_code,
                   fiscal_regime_code, default_series, status, created_at, updated_at
            FROM billing.issuer_profiles
            WHERE (CAST(:branchId AS uuid) IS NULL OR branch_id = :branchId)
              AND (CAST(:status AS varchar) IS NULL OR status = :status)
            ORDER BY created_at DESC
            """, new MapSqlParameterSource("branchId", branchId).addValue("status", status), this::mapIssuer);
    }

    @Override
    public Optional<IssuerProfile> findIssuerProfile(UUID issuerProfileId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT issuer_profile_id, branch_id, rfc, legal_name, postal_code,
                       fiscal_regime_code, default_series, status, created_at, updated_at
                FROM billing.issuer_profiles
                WHERE issuer_profile_id = :id
                """, new MapSqlParameterSource("id", issuerProfileId), this::mapIssuer));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public IssuerProfile updateIssuerProfile(UpdateIssuerProfileCommand command) {
        try {
            int updated = jdbc.update("""
                UPDATE billing.issuer_profiles
                SET branch_id = :branchId, rfc = :rfc, legal_name = :legalName,
                    postal_code = :postalCode, fiscal_regime_code = :fiscalRegimeCode,
                    default_series = :defaultSeries, updated_at = clock_timestamp()
                WHERE issuer_profile_id = :id
                """, issuerParameters(command.issuerProfileId(), command.branchId(), command.rfc(), command.legalName(),
                command.postalCode(), command.fiscalRegimeCode(), command.defaultSeries()));
            if (updated != 1) throw new BillingNotFoundException("el perfil emisor " + command.issuerProfileId());
            return findIssuerProfile(command.issuerProfileId()).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new BillingConflictException("No se pudo actualizar el perfil emisor por una restriccion de integridad");
        }
    }

    @Override
    public IssuerProfile changeIssuerProfileStatus(UUID issuerProfileId, String status) {
        try {
            int updated = jdbc.update("""
                UPDATE billing.issuer_profiles SET status = :status, updated_at = clock_timestamp()
                WHERE issuer_profile_id = :id
                """, new MapSqlParameterSource("id", issuerProfileId).addValue("status", status));
            if (updated != 1) throw new BillingNotFoundException("el perfil emisor " + issuerProfileId);
            return findIssuerProfile(issuerProfileId).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new BillingConflictException("La sucursal ya tiene otro perfil emisor activo");
        }
    }

    @Override
    public FiscalProfile createFiscalProfile(CreateFiscalProfileCommand command) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                INSERT INTO billing.fiscal_profiles (
                    fiscal_profile_id, customer_id, rfc, legal_name, postal_code,
                    fiscal_regime_code, cfdi_use_code, email, status
                ) VALUES (
                    :id, :customerId, :rfc, :legalName, :postalCode,
                    :fiscalRegimeCode, :cfdiUseCode, :email, 'ACTIVE'
                )
                """, fiscalProfileParameters(id, command.customerId(), command.rfc(), command.legalName(),
                command.postalCode(), command.fiscalRegimeCode(), command.cfdiUseCode(), command.email()));
            return findFiscalProfile(id).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new BillingConflictException("El cliente ya tiene un perfil fiscal con ese RFC");
        }
    }

    @Override
    public List<FiscalProfile> listFiscalProfiles(UUID customerId, String status) {
        return jdbc.query("""
            SELECT fiscal_profile_id, customer_id, rfc, legal_name, postal_code,
                   fiscal_regime_code, cfdi_use_code, email, status, created_at, updated_at
            FROM billing.fiscal_profiles
            WHERE (CAST(:customerId AS uuid) IS NULL OR customer_id = :customerId)
              AND (CAST(:status AS varchar) IS NULL OR status = :status)
            ORDER BY created_at DESC
            """, new MapSqlParameterSource("customerId", customerId).addValue("status", status), this::mapFiscalProfile);
    }

    @Override
    public Optional<FiscalProfile> findFiscalProfile(UUID fiscalProfileId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT fiscal_profile_id, customer_id, rfc, legal_name, postal_code,
                       fiscal_regime_code, cfdi_use_code, email, status, created_at, updated_at
                FROM billing.fiscal_profiles WHERE fiscal_profile_id = :id
                """, new MapSqlParameterSource("id", fiscalProfileId), this::mapFiscalProfile));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public FiscalProfile updateFiscalProfile(UpdateFiscalProfileCommand command) {
        try {
            int updated = jdbc.update("""
                UPDATE billing.fiscal_profiles
                SET customer_id = :customerId, rfc = :rfc, legal_name = :legalName,
                    postal_code = :postalCode, fiscal_regime_code = :fiscalRegimeCode,
                    cfdi_use_code = :cfdiUseCode, email = :email, updated_at = clock_timestamp()
                WHERE fiscal_profile_id = :id
                """, fiscalProfileParameters(command.fiscalProfileId(), command.customerId(), command.rfc(),
                command.legalName(), command.postalCode(), command.fiscalRegimeCode(), command.cfdiUseCode(), command.email()));
            if (updated != 1) throw new BillingNotFoundException("el perfil fiscal " + command.fiscalProfileId());
            return findFiscalProfile(command.fiscalProfileId()).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new BillingConflictException("El cliente ya tiene un perfil fiscal con ese RFC");
        }
    }

    @Override
    public FiscalProfile changeFiscalProfileStatus(UUID fiscalProfileId, String status) {
        int updated = jdbc.update("""
            UPDATE billing.fiscal_profiles SET status = :status, updated_at = clock_timestamp()
            WHERE fiscal_profile_id = :id
            """, new MapSqlParameterSource("id", fiscalProfileId).addValue("status", status));
        if (updated != 1) throw new BillingNotFoundException("el perfil fiscal " + fiscalProfileId);
        return findFiscalProfile(fiscalProfileId).orElseThrow();
    }

    @Override
    public boolean productExists(UUID productId) {
        return exists("SELECT EXISTS(SELECT 1 FROM catalog.products WHERE product_id = :id)", productId);
    }

    @Override
    public boolean unitExists(UUID unitId) {
        return exists("SELECT EXISTS(SELECT 1 FROM catalog.units_of_measure WHERE unit_id = :id)", unitId);
    }

    @Override
    public void updateProductFiscalClassification(UUID productId, String satProductServiceCode) {
        int updated = jdbc.update("""
            UPDATE catalog.products SET sat_product_service_code = :code, updated_at = clock_timestamp()
            WHERE product_id = :id
            """, new MapSqlParameterSource("id", productId).addValue("code", satProductServiceCode));
        if (updated != 1) throw new BillingNotFoundException("el producto " + productId);
    }

    @Override
    public void updateUnitFiscalClassification(UUID unitId, String satUnitCode) {
        int updated = jdbc.update("""
            UPDATE catalog.units_of_measure SET sat_unit_code = :code WHERE unit_id = :id
            """, new MapSqlParameterSource("id", unitId).addValue("code", satUnitCode));
        if (updated != 1) throw new BillingNotFoundException("la unidad de medida " + unitId);
    }

    @Override
    public Optional<FiscalDocumentSource> findFiscalDocumentSource(UUID salesOrderId) {
        try {
            FiscalDocumentSource source = jdbc.queryForObject("""
                SELECT sales_order_id, branch_id, customer_id, status, payment_status,
                       currency_code, subtotal, discount_total, tax_total, total
                FROM sales.sales_orders WHERE sales_order_id = :id
                """, new MapSqlParameterSource("id", salesOrderId), (rs, rowNum) -> new FiscalDocumentSource(
                uuid(rs, "sales_order_id"), uuid(rs, "branch_id"), uuid(rs, "customer_id"),
                rs.getString("status"), rs.getString("payment_status"), rs.getString("currency_code").trim(),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount_total"),
                rs.getBigDecimal("tax_total"), rs.getBigDecimal("total"), List.of()
            ));
            List<FiscalDocumentSourceItem> items = jdbc.query("""
                SELECT soi.sales_order_item_id,
                       soi.product_name_snapshot AS description,
                       product.sat_product_service_code,
                       unit.sat_unit_code,
                       soi.quantity, soi.unit_price, soi.discount_amount, soi.tax_amount, soi.line_total
                FROM sales.sales_order_items soi
                JOIN catalog.product_presentations presentation
                  ON presentation.product_presentation_id = soi.product_presentation_id
                JOIN catalog.products product ON product.product_id = presentation.product_id
                JOIN catalog.units_of_measure unit ON unit.unit_id = presentation.unit_id
                WHERE soi.sales_order_id = :id
                ORDER BY soi.sales_order_item_id
                """, new MapSqlParameterSource("id", salesOrderId), this::mapSourceItem);
            return Optional.of(new FiscalDocumentSource(
                source.salesOrderId(), source.branchId(), source.customerId(), source.orderStatus(),
                source.paymentStatus(), source.currencyCode(), source.subtotal(), source.discountTotal(),
                source.taxTotal(), source.total(), items
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean activeIncomeDocumentExists(UUID salesOrderId) {
        Boolean value = jdbc.queryForObject("""
            SELECT EXISTS(
                SELECT 1 FROM billing.fiscal_documents
                WHERE sales_order_id = :id AND document_type = 'INCOME'
                  AND status NOT IN ('CANCELLED','ERROR')
            )
            """, new MapSqlParameterSource("id", salesOrderId), Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    @Override
    public FiscalDocument createFiscalDocument(
        CreateFiscalDocumentCommand command,
        IssuerProfile issuer,
        FiscalProfile receiver,
        FiscalDocumentSource source
    ) {
        UUID documentId = UUID.randomUUID();
        String series = command.series() == null || command.series().isBlank()
            ? issuer.defaultSeries()
            : command.series().trim().toUpperCase();
        try {
            jdbc.update("""
                INSERT INTO billing.fiscal_documents (
                    fiscal_document_id, sales_order_id, issuer_profile_id, fiscal_profile_id,
                    document_type, cfdi_version, status, series, folio,
                    issuer_rfc, issuer_name, receiver_rfc, receiver_name,
                    payment_form_code, payment_method_code, currency_code,
                    subtotal, discount_total, tax_total, total
                ) VALUES (
                    :id, :salesOrderId, :issuerProfileId, :fiscalProfileId,
                    'INCOME', '4.0', 'DRAFT', :series, :folio,
                    :issuerRfc, :issuerName, :receiverRfc, :receiverName,
                    :paymentFormCode, :paymentMethodCode, :currencyCode,
                    :subtotal, :discountTotal, :taxTotal, :total
                )
                """, new MapSqlParameterSource()
                .addValue("id", documentId)
                .addValue("salesOrderId", source.salesOrderId())
                .addValue("issuerProfileId", issuer.issuerProfileId())
                .addValue("fiscalProfileId", receiver.fiscalProfileId())
                .addValue("series", series)
                .addValue("folio", trimToNull(command.folio()))
                .addValue("issuerRfc", issuer.rfc())
                .addValue("issuerName", issuer.legalName())
                .addValue("receiverRfc", receiver.rfc())
                .addValue("receiverName", receiver.legalName())
                .addValue("paymentFormCode", upperToNull(command.paymentFormCode()))
                .addValue("paymentMethodCode", upperToNull(command.paymentMethodCode()))
                .addValue("currencyCode", source.currencyCode())
                .addValue("subtotal", source.subtotal())
                .addValue("discountTotal", source.discountTotal())
                .addValue("taxTotal", source.taxTotal())
                .addValue("total", source.total()));

            for (FiscalDocumentSourceItem item : source.items()) {
                jdbc.update("""
                    INSERT INTO billing.fiscal_document_items (
                        fiscal_document_item_id, fiscal_document_id, sales_order_item_id,
                        sat_product_service_code, sat_unit_code, description, quantity,
                        unit_value, discount_amount, tax_amount, amount
                    ) VALUES (
                        :id, :documentId, :salesOrderItemId,
                        :satProductServiceCode, :satUnitCode, :description, :quantity,
                        :unitValue, :discountAmount, :taxAmount, :amount
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("documentId", documentId)
                    .addValue("salesOrderItemId", item.salesOrderItemId())
                    .addValue("satProductServiceCode", item.satProductServiceCode())
                    .addValue("satUnitCode", item.satUnitCode())
                    .addValue("description", item.description())
                    .addValue("quantity", item.quantity())
                    .addValue("unitValue", item.unitPrice())
                    .addValue("discountAmount", item.discountAmount())
                    .addValue("taxAmount", item.taxAmount())
                    .addValue("amount", item.lineTotal()));
            }
            insertDocumentEvent(documentId, "CREATED");
            return findFiscalDocument(documentId).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new BillingConflictException("La venta ya tiene un documento fiscal vigente o los datos fiscales son invalidos");
        }
    }

    @Override
    public List<FiscalDocument> listFiscalDocuments(UUID salesOrderId, String status) {
        return jdbc.query("""
            SELECT fiscal_document_id, sales_order_id, issuer_profile_id, fiscal_profile_id,
                   document_type, cfdi_version, status, series, folio,
                   issuer_rfc, issuer_name, receiver_rfc, receiver_name,
                   payment_form_code, payment_method_code, currency_code,
                   subtotal, discount_total, tax_total, total, issued_at, created_at
            FROM billing.fiscal_documents
            WHERE (CAST(:salesOrderId AS uuid) IS NULL OR sales_order_id = :salesOrderId)
              AND (CAST(:status AS varchar) IS NULL OR status = :status)
            ORDER BY created_at DESC
            """, new MapSqlParameterSource("salesOrderId", salesOrderId).addValue("status", status),
            (rs, rowNum) -> mapDocument(rs, loadDocumentItems(uuid(rs, "fiscal_document_id"))));
    }

    @Override
    public Optional<FiscalDocument> findFiscalDocument(UUID fiscalDocumentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                SELECT fiscal_document_id, sales_order_id, issuer_profile_id, fiscal_profile_id,
                       document_type, cfdi_version, status, series, folio,
                       issuer_rfc, issuer_name, receiver_rfc, receiver_name,
                       payment_form_code, payment_method_code, currency_code,
                       subtotal, discount_total, tax_total, total, issued_at, created_at
                FROM billing.fiscal_documents WHERE fiscal_document_id = :id
                """, new MapSqlParameterSource("id", fiscalDocumentId),
                (rs, rowNum) -> mapDocument(rs, loadDocumentItems(fiscalDocumentId))));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public FiscalDocument markFiscalDocumentReady(UUID fiscalDocumentId) {
        int updated = jdbc.update("""
            UPDATE billing.fiscal_documents
            SET status = 'READY', issued_at = clock_timestamp()
            WHERE fiscal_document_id = :id AND status = 'DRAFT'
            """, new MapSqlParameterSource("id", fiscalDocumentId));
        if (updated != 1) throw new BillingConflictException("El documento fiscal ya no esta en estado DRAFT");
        insertDocumentEvent(fiscalDocumentId, "READY");
        return findFiscalDocument(fiscalDocumentId).orElseThrow();
    }

    private List<FiscalDocumentItem> loadDocumentItems(UUID fiscalDocumentId) {
        return jdbc.query("""
            SELECT fiscal_document_item_id, sales_order_item_id, sat_product_service_code,
                   sat_unit_code, description, quantity, unit_value, discount_amount,
                   tax_amount, amount
            FROM billing.fiscal_document_items
            WHERE fiscal_document_id = :id
            ORDER BY fiscal_document_item_id
            """, new MapSqlParameterSource("id", fiscalDocumentId), this::mapDocumentItem);
    }

    private void insertDocumentEvent(UUID documentId, String eventType) {
        jdbc.update("""
            INSERT INTO billing.fiscal_document_events (
                fiscal_document_event_id, fiscal_document_id, event_type
            ) VALUES (:eventId, :documentId, :eventType)
            """, new MapSqlParameterSource("eventId", UUID.randomUUID())
            .addValue("documentId", documentId)
            .addValue("eventType", eventType));
    }

    private boolean exists(String sql, UUID id) {
        Boolean value = jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    private MapSqlParameterSource issuerParameters(
        UUID id, UUID branchId, String rfc, String legalName, String postalCode,
        String fiscalRegimeCode, String defaultSeries
    ) {
        return new MapSqlParameterSource("id", id)
            .addValue("branchId", branchId)
            .addValue("rfc", rfc)
            .addValue("legalName", legalName)
            .addValue("postalCode", postalCode)
            .addValue("fiscalRegimeCode", fiscalRegimeCode)
            .addValue("defaultSeries", defaultSeries);
    }

    private MapSqlParameterSource fiscalProfileParameters(
        UUID id, UUID customerId, String rfc, String legalName, String postalCode,
        String fiscalRegimeCode, String cfdiUseCode, String email
    ) {
        return new MapSqlParameterSource("id", id)
            .addValue("customerId", customerId)
            .addValue("rfc", rfc)
            .addValue("legalName", legalName)
            .addValue("postalCode", postalCode)
            .addValue("fiscalRegimeCode", fiscalRegimeCode)
            .addValue("cfdiUseCode", cfdiUseCode)
            .addValue("email", email);
    }

    private IssuerProfile mapIssuer(ResultSet rs, int rowNum) throws SQLException {
        return new IssuerProfile(uuid(rs, "issuer_profile_id"), uuid(rs, "branch_id"), rs.getString("rfc"),
            rs.getString("legal_name"), rs.getString("postal_code"), rs.getString("fiscal_regime_code"),
            rs.getString("default_series"), rs.getString("status"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private FiscalProfile mapFiscalProfile(ResultSet rs, int rowNum) throws SQLException {
        return new FiscalProfile(uuid(rs, "fiscal_profile_id"), uuid(rs, "customer_id"), rs.getString("rfc"),
            rs.getString("legal_name"), rs.getString("postal_code"), rs.getString("fiscal_regime_code"),
            rs.getString("cfdi_use_code"), rs.getString("email"), rs.getString("status"),
            instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private FiscalDocumentSourceItem mapSourceItem(ResultSet rs, int rowNum) throws SQLException {
        return new FiscalDocumentSourceItem(uuid(rs, "sales_order_item_id"), rs.getString("description"),
            rs.getString("sat_product_service_code"), rs.getString("sat_unit_code"), rs.getBigDecimal("quantity"),
            rs.getBigDecimal("unit_price"), rs.getBigDecimal("discount_amount"), rs.getBigDecimal("tax_amount"),
            rs.getBigDecimal("line_total"));
    }

    private FiscalDocumentItem mapDocumentItem(ResultSet rs, int rowNum) throws SQLException {
        return new FiscalDocumentItem(uuid(rs, "fiscal_document_item_id"), uuid(rs, "sales_order_item_id"),
            rs.getString("sat_product_service_code"), rs.getString("sat_unit_code"), rs.getString("description"),
            rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_value"), rs.getBigDecimal("discount_amount"),
            rs.getBigDecimal("tax_amount"), rs.getBigDecimal("amount"));
    }

    private FiscalDocument mapDocument(ResultSet rs, List<FiscalDocumentItem> items) throws SQLException {
        return new FiscalDocument(
            uuid(rs, "fiscal_document_id"), uuid(rs, "sales_order_id"), uuid(rs, "issuer_profile_id"),
            uuid(rs, "fiscal_profile_id"), rs.getString("document_type"), rs.getString("cfdi_version"),
            rs.getString("status"), rs.getString("series"), rs.getString("folio"), rs.getString("issuer_rfc"),
            rs.getString("issuer_name"), rs.getString("receiver_rfc"), rs.getString("receiver_name"),
            rs.getString("payment_form_code"), rs.getString("payment_method_code"),
            rs.getString("currency_code").trim(), rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount_total"),
            rs.getBigDecimal("tax_total"), rs.getBigDecimal("total"), instant(rs, "issued_at"),
            instant(rs, "created_at"), items
        );
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String upperToNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }
}
