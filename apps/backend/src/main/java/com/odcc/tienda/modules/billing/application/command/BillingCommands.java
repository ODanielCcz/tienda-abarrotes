package com.odcc.tienda.modules.billing.application.command;

import java.util.UUID;

public final class BillingCommands {

    private BillingCommands() {
    }

    public record CreateIssuerProfileCommand(
        UUID branchId,
        String rfc,
        String legalName,
        String postalCode,
        String fiscalRegimeCode,
        String defaultSeries
    ) {
    }

    public record UpdateIssuerProfileCommand(
        UUID issuerProfileId,
        UUID branchId,
        String rfc,
        String legalName,
        String postalCode,
        String fiscalRegimeCode,
        String defaultSeries
    ) {
    }

    public record CreateFiscalProfileCommand(
        UUID customerId,
        String rfc,
        String legalName,
        String postalCode,
        String fiscalRegimeCode,
        String cfdiUseCode,
        String email
    ) {
    }

    public record UpdateFiscalProfileCommand(
        UUID fiscalProfileId,
        UUID customerId,
        String rfc,
        String legalName,
        String postalCode,
        String fiscalRegimeCode,
        String cfdiUseCode,
        String email
    ) {
    }

    public record ChangeStatusCommand(UUID resourceId, String status) {
    }

    public record UpdateProductFiscalClassificationCommand(
        UUID productId,
        String satProductServiceCode
    ) {
    }

    public record UpdateUnitFiscalClassificationCommand(
        UUID unitId,
        String satUnitCode
    ) {
    }

    public record CreateFiscalDocumentCommand(
        UUID salesOrderId,
        UUID issuerProfileId,
        UUID fiscalProfileId,
        String series,
        String folio,
        String paymentFormCode,
        String paymentMethodCode
    ) {
    }
}
