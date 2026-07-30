package com.odcc.tienda.modules.billing.application.port.out;

import com.odcc.tienda.modules.billing.application.command.BillingCommands.CreateFiscalDocumentCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.CreateFiscalProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.CreateIssuerProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateFiscalProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateIssuerProfileCommand;
import com.odcc.tienda.modules.billing.application.model.BillingModels.FiscalDocument;
import com.odcc.tienda.modules.billing.application.model.BillingModels.FiscalDocumentSource;
import com.odcc.tienda.modules.billing.application.model.BillingModels.FiscalProfile;
import com.odcc.tienda.modules.billing.application.model.BillingModels.IssuerProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingRepositoryPort {
    boolean branchIsActive(UUID branchId);
    boolean customerIsActive(UUID customerId);
    boolean activeIssuerExists(UUID branchId, UUID excludedIssuerProfileId);

    IssuerProfile createIssuerProfile(CreateIssuerProfileCommand command);
    List<IssuerProfile> listIssuerProfiles(UUID branchId, String status);
    Optional<IssuerProfile> findIssuerProfile(UUID issuerProfileId);
    IssuerProfile updateIssuerProfile(UpdateIssuerProfileCommand command);
    IssuerProfile changeIssuerProfileStatus(UUID issuerProfileId, String status);

    FiscalProfile createFiscalProfile(CreateFiscalProfileCommand command);
    List<FiscalProfile> listFiscalProfiles(UUID customerId, String status);
    Optional<FiscalProfile> findFiscalProfile(UUID fiscalProfileId);
    FiscalProfile updateFiscalProfile(UpdateFiscalProfileCommand command);
    FiscalProfile changeFiscalProfileStatus(UUID fiscalProfileId, String status);

    boolean productExists(UUID productId);
    boolean unitExists(UUID unitId);
    void updateProductFiscalClassification(UUID productId, String satProductServiceCode);
    void updateUnitFiscalClassification(UUID unitId, String satUnitCode);

    Optional<FiscalDocumentSource> findFiscalDocumentSource(UUID salesOrderId);
    boolean activeIncomeDocumentExists(UUID salesOrderId);
    FiscalDocument createFiscalDocument(
        CreateFiscalDocumentCommand command,
        IssuerProfile issuer,
        FiscalProfile receiver,
        FiscalDocumentSource source
    );
    List<FiscalDocument> listFiscalDocuments(UUID salesOrderId, String status);
    Optional<FiscalDocument> findFiscalDocument(UUID fiscalDocumentId);
    FiscalDocument markFiscalDocumentReady(UUID fiscalDocumentId);
}
