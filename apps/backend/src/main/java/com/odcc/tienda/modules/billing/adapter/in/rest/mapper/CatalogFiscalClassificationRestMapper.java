package com.odcc.tienda.modules.billing.adapter.in.rest.mapper;

import com.odcc.tienda.modules.billing.adapter.in.rest.request.BillingRequests.ProductFiscalClassificationRequest;
import com.odcc.tienda.modules.billing.adapter.in.rest.request.BillingRequests.UnitFiscalClassificationRequest;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateProductFiscalClassificationCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateUnitFiscalClassificationCommand;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(config = CentralMapperConfig.class)
public interface CatalogFiscalClassificationRestMapper {

    @Mapping(target = "productId", source = "productId")
    UpdateProductFiscalClassificationCommand toProductCommand(
        UUID productId,
        ProductFiscalClassificationRequest request
    );

    @Mapping(target = "unitId", source = "unitId")
    UpdateUnitFiscalClassificationCommand toUnitCommand(
        UUID unitId,
        UnitFiscalClassificationRequest request
    );
}
