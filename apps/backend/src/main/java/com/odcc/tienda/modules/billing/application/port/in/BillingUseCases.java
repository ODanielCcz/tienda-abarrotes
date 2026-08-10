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
    IssuerProfile createIssuerProfile(CreateIssuerProfileCommand command, UUID actorUserId);
    List<IssuerProfile> listIssuerProfiles(UUID branchId, String status, UUID actorUserId);
    IssuerProfile getIssuerProfile(UUID issuerProfileId, UUID actorUserId);
    IssuerProfile updateIssuerProfile(UpdateIssuerProfileCommand command, UUID actorUserId);
    IssuerProfile changeIssuerProfileStatus(ChangeStatusCommand command, UUID actorUserId);

    FiscalProfile createFiscalProfile(CreateFiscalProfileCommand command, UUID actorUserId);
    List<FiscalProfile> listFiscalProfiles(UUID customerId, String status, UUID actorUserId);
    FiscalProfile getFiscalProfile(UUID fiscalProfileId, UUID actorUserId);
    FiscalProfile updateFiscalProfile(UpdateFiscalProfileCommand command, UUID actorUserId);
    FiscalProfile changeFiscalProfileStatus(ChangeStatusCommand command, UUID actorUserId);

    void updateProductFiscalClassification(UpdateProductFiscalClassificationCommand command, UUID actorUserId);
    void updateUnitFiscalClassification(UpdateUnitFiscalClassificationCommand command, UUID actorUserId);

    FiscalDocument createFiscalDocument(CreateFiscalDocumentCommand command, UUID actorUserId);
    List<FiscalDocument> listFiscalDocuments(UUID salesOrderId, String status, UUID actorUserId);
    FiscalDocument getFiscalDocument(UUID fiscalDocumentId, UUID actorUserId);
    FiscalDocument markFiscalDocumentReady(UUID fiscalDocumentId, UUID actorUserId);
}
