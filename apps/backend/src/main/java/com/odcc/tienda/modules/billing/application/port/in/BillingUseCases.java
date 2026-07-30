package com.odcc.tienda.modules.billing.application.port.in;

import com.odcc.tienda.modules.billing.application.command.BillingCommands.ChangeStatusCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.CreateFiscalDocumentCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.CreateFiscalProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.CreateIssuerProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateFiscalProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateIssuerProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateProductFiscalClassificationCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateUnitFiscalClassificationCommand;
import com.odcc.tienda.modules.billing.application.model.BillingModels.FiscalDocument;
import com.odcc.tienda.modules.billing.application.model.BillingModels.FiscalProfile;
import com.odcc.tienda.modules.billing.application.model.BillingModels.IssuerProfile;

import java.util.List;
import java.util.UUID;

public interface BillingUseCases {
    IssuerProfile createIssuerProfile(CreateIssuerProfileCommand command);
    List<IssuerProfile> listIssuerProfiles(UUID branchId, String status);
    IssuerProfile getIssuerProfile(UUID issuerProfileId);
    IssuerProfile updateIssuerProfile(UpdateIssuerProfileCommand command);
    IssuerProfile changeIssuerProfileStatus(ChangeStatusCommand command);

    FiscalProfile createFiscalProfile(CreateFiscalProfileCommand command);
    List<FiscalProfile> listFiscalProfiles(UUID customerId, String status);
    FiscalProfile getFiscalProfile(UUID fiscalProfileId);
    FiscalProfile updateFiscalProfile(UpdateFiscalProfileCommand command);
    FiscalProfile changeFiscalProfileStatus(ChangeStatusCommand command);

    void updateProductFiscalClassification(UpdateProductFiscalClassificationCommand command);
    void updateUnitFiscalClassification(UpdateUnitFiscalClassificationCommand command);

    FiscalDocument createFiscalDocument(CreateFiscalDocumentCommand command);
    List<FiscalDocument> listFiscalDocuments(UUID salesOrderId, String status);
    FiscalDocument getFiscalDocument(UUID fiscalDocumentId);
    FiscalDocument markFiscalDocumentReady(UUID fiscalDocumentId);
}
