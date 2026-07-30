package com.odcc.tienda.modules.billing.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BillingModels {

    private BillingModels() {
    }

    public record IssuerProfile(
        UUID issuerProfileId,
        UUID branchId,
        String rfc,
        String legalName,
        String postalCode,
        String fiscalRegimeCode,
        String defaultSeries,
        String status,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record FiscalProfile(
        UUID fiscalProfileId,
        UUID customerId,
        String rfc,
        String legalName,
        String postalCode,
        String fiscalRegimeCode,
        String cfdiUseCode,
        String email,
        String status,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record FiscalDocumentItem(
        UUID fiscalDocumentItemId,
        UUID salesOrderItemId,
        String satProductServiceCode,
        String satUnitCode,
        String description,
        BigDecimal quantity,
        BigDecimal unitValue,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal amount
    ) {
    }

    public record FiscalDocument(
        UUID fiscalDocumentId,
        UUID salesOrderId,
        UUID issuerProfileId,
        UUID fiscalProfileId,
        String documentType,
        String cfdiVersion,
        String status,
        String series,
        String folio,
        String issuerRfc,
        String issuerName,
        String receiverRfc,
        String receiverName,
        String paymentFormCode,
        String paymentMethodCode,
        String currencyCode,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal total,
        Instant issuedAt,
        Instant createdAt,
        List<FiscalDocumentItem> items
    ) {
    }

    public record FiscalDocumentSource(
        UUID salesOrderId,
        UUID branchId,
        UUID customerId,
        String orderStatus,
        String paymentStatus,
        String currencyCode,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal total,
        List<FiscalDocumentSourceItem> items
    ) {
    }

    public record FiscalDocumentSourceItem(
        UUID salesOrderItemId,
        String description,
        String satProductServiceCode,
        String satUnitCode,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal lineTotal
    ) {
    }
}
