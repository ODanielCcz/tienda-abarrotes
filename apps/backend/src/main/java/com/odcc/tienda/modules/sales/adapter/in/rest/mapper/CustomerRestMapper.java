package com.odcc.tienda.modules.sales.adapter.in.rest.mapper;

import com.odcc.tienda.modules.sales.adapter.in.rest.request.ChangeCustomerStatusRequest;
import com.odcc.tienda.modules.sales.adapter.in.rest.request.CreateCustomerRequest;
import com.odcc.tienda.modules.sales.adapter.in.rest.request.UpdateCustomerRequest;
import com.odcc.tienda.modules.sales.application.command.ChangeCustomerStatusCommand;
import com.odcc.tienda.modules.sales.application.command.CreateCustomerCommand;
import com.odcc.tienda.modules.sales.application.command.UpdateCustomerCommand;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(config = CentralMapperConfig.class)
public interface CustomerRestMapper {

    CreateCustomerCommand toCreateCommand(CreateCustomerRequest request);

    @Mapping(target = "customerId", source = "customerId")
    UpdateCustomerCommand toUpdateCommand(UUID customerId, UpdateCustomerRequest request);

    @Mapping(target = "customerId", source = "customerId")
    ChangeCustomerStatusCommand toStatusCommand(UUID customerId, ChangeCustomerStatusRequest request);
}
