package com.odcc.tienda.modules.billing.adapter.in.rest.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class BillingRequests {

    private BillingRequests() {
    }

    public record IssuerProfileRequest(
        @NotNull UUID branchId,
        @NotBlank @Size(max = 13) String rfc,
        @NotBlank @Size(max = 300) String legalName,
        @NotBlank @Size(min = 5, max = 5) String postalCode,
        @NotBlank @Size(max = 5) String fiscalRegimeCode,
        @Size(max = 25) String defaultSeries
    ) {
    }

    public record FiscalProfileRequest(
        @NotNull UUID customerId,
        @NotBlank @Size(max = 13) String rfc,
        @NotBlank @Size(max = 300) String legalName,
        @NotBlank @Size(min = 5, max = 5) String postalCode,
        @NotBlank @Size(max = 5) String fiscalRegimeCode,
        @Size(max = 5) String cfdiUseCode,
        @Email @Size(max = 254) String email
    ) {
    }

    public record ChangeStatusRequest(@NotBlank String status) {
    }

    public record ProductFiscalClassificationRequest(
        @NotBlank @Size(min = 8, max = 8) String satProductServiceCode
    ) {
    }

    public record UnitFiscalClassificationRequest(
        @NotBlank @Size(max = 5) String satUnitCode
    ) {
    }

    public record CreateFiscalDocumentRequest(
        @NotNull UUID salesOrderId,
        @NotNull UUID issuerProfileId,
        @NotNull UUID fiscalProfileId,
        @Size(max = 25) String series,
        @Size(max = 50) String folio,
        @Size(max = 5) String paymentFormCode,
        @Size(max = 5) String paymentMethodCode
    ) {
    }
}
