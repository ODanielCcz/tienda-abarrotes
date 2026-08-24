package com.odcc.tienda.modules.sales.adapter.in.rest.mapper;

import com.odcc.tienda.modules.sales.adapter.in.rest.request.CreateSalesOrderItemRequest;
import com.odcc.tienda.modules.sales.adapter.in.rest.request.CreateSalesOrderRequest;
import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderItemCommand;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = CentralMapperConfig.class)
public interface SalesOrderRestMapper {

    CreateSalesOrderCommand toCreateCommand(CreateSalesOrderRequest request);

    CreateSalesOrderItemCommand toCommand(CreateSalesOrderItemRequest request);
}
