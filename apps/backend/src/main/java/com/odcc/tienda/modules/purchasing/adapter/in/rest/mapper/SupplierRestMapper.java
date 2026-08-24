package com.odcc.tienda.modules.purchasing.adapter.in.rest.mapper;

import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.ChangeSupplierStatusRequest;
import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.CreateSupplierRequest;
import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.UpdateSupplierRequest;
import com.odcc.tienda.modules.purchasing.application.command.ChangeSupplierStatusCommand;
import com.odcc.tienda.modules.purchasing.application.command.CreateSupplierCommand;
import com.odcc.tienda.modules.purchasing.application.command.UpdateSupplierCommand;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(config = CentralMapperConfig.class)
public interface SupplierRestMapper {

    CreateSupplierCommand toCreateCommand(CreateSupplierRequest request);

    @Mapping(target = "supplierId", source = "supplierId")
    UpdateSupplierCommand toUpdateCommand(
        UUID supplierId,
        UpdateSupplierRequest request
    );

    @Mapping(target = "supplierId", source = "supplierId")
    ChangeSupplierStatusCommand toStatusCommand(
        UUID supplierId,
        ChangeSupplierStatusRequest request
    );
}
