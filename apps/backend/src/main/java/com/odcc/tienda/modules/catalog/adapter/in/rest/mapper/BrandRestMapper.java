package com.odcc.tienda.modules.catalog.adapter.in.rest.mapper;

import com.odcc.tienda.modules.catalog.adapter.in.rest.request.CreateBrandRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.UpdateBrandRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.BrandResponse;
import com.odcc.tienda.modules.catalog.application.command.CreateBrandCommand;
import com.odcc.tienda.modules.catalog.application.command.UpdateBrandCommand;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(config = CentralMapperConfig.class)
public interface BrandRestMapper {

    CreateBrandCommand toCommand(CreateBrandRequest request);

    @Mapping(target = "brandId", source = "brandId")
    @Mapping(target = "code", source = "request.code")
    @Mapping(target = "name", source = "request.name")
    UpdateBrandCommand toCommand(
        UUID brandId,
        UpdateBrandRequest request
    );

    BrandResponse toResponse(Brand brand);
}
