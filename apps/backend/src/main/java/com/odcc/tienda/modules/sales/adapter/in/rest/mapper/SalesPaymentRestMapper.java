package com.odcc.tienda.modules.sales.adapter.in.rest.mapper;

import com.odcc.tienda.modules.sales.adapter.in.rest.request.CreateSalesPaymentRequest;
import com.odcc.tienda.modules.sales.application.command.CreateSalesPaymentCommand;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(config = CentralMapperConfig.class)
public interface SalesPaymentRestMapper {

    @Mapping(target = "salesOrderId", source = "salesOrderId")
    @Mapping(target = "createdBy", source = "actorUserId")
    CreateSalesPaymentCommand toCreateCommand(
        UUID salesOrderId,
        CreateSalesPaymentRequest request,
        UUID actorUserId
    );
}
