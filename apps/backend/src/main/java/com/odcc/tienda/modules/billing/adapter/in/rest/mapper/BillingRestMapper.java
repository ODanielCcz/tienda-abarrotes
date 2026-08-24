package com.odcc.tienda.modules.billing.adapter.in.rest.mapper;

import com.odcc.tienda.modules.billing.adapter.in.rest.request.BillingRequests.ChangeStatusRequest;
import com.odcc.tienda.modules.billing.adapter.in.rest.request.BillingRequests.CreateFiscalDocumentRequest;
import com.odcc.tienda.modules.billing.adapter.in.rest.request.BillingRequests.FiscalProfileRequest;
import com.odcc.tienda.modules.billing.adapter.in.rest.request.BillingRequests.IssuerProfileRequest;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.ChangeStatusCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.CreateFiscalDocumentCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.CreateFiscalProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.CreateIssuerProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateFiscalProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateIssuerProfileCommand;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(config = CentralMapperConfig.class)
public interface BillingRestMapper {

    CreateIssuerProfileCommand toCreateIssuerCommand(IssuerProfileRequest request);

    @Mapping(target = "issuerProfileId", source = "issuerProfileId")
    UpdateIssuerProfileCommand toUpdateIssuerCommand(UUID issuerProfileId, IssuerProfileRequest request);

    CreateFiscalProfileCommand toCreateFiscalProfileCommand(FiscalProfileRequest request);

    @Mapping(target = "fiscalProfileId", source = "fiscalProfileId")
    UpdateFiscalProfileCommand toUpdateFiscalProfileCommand(UUID fiscalProfileId, FiscalProfileRequest request);

    @Mapping(target = "resourceId", source = "resourceId")
    ChangeStatusCommand toStatusCommand(UUID resourceId, ChangeStatusRequest request);

    CreateFiscalDocumentCommand toCreateDocumentCommand(CreateFiscalDocumentRequest request);
}
