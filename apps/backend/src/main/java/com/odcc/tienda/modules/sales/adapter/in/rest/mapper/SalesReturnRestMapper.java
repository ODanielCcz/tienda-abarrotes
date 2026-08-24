package com.odcc.tienda.modules.sales.adapter.in.rest.mapper;

import com.odcc.tienda.modules.sales.adapter.in.rest.request.ConfirmSalesReturnRequest;
import com.odcc.tienda.modules.sales.adapter.in.rest.request.CreateSalesReturnItemRequest;
import com.odcc.tienda.modules.sales.adapter.in.rest.request.CreateSalesReturnRequest;
import com.odcc.tienda.modules.sales.application.command.ConfirmSalesReturnCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesReturnCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesReturnItemCommand;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(config = CentralMapperConfig.class)
public interface SalesReturnRestMapper {

    CreateSalesReturnItemCommand toCommand(CreateSalesReturnItemRequest request);

    @Mapping(target = "salesOrderId", source = "salesOrderId")
    @Mapping(target = "createdBy", source = "actorUserId")
    CreateSalesReturnCommand toCreateCommand(
        UUID salesOrderId,
        CreateSalesReturnRequest request,
        UUID actorUserId
    );

    @Mapping(target = "returnId", source = "returnId")
    @Mapping(target = "confirmedBy", source = "actorUserId")
    ConfirmSalesReturnCommand toConfirmCommand(
        UUID returnId,
        ConfirmSalesReturnRequest request,
        UUID actorUserId
    );
}
