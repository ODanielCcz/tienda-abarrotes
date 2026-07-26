package com.odcc.tienda.modules.catalog.adapter.in.rest.mapper;

import com.odcc.tienda.modules.catalog.adapter.in.rest.request.CreateBrandRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.UpdateBrandRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.BrandResponse;
import com.odcc.tienda.modules.catalog.application.command.CreateBrandCommand;
import com.odcc.tienda.modules.catalog.application.command.UpdateBrandCommand;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapping;

import java.util.UUID;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

@Mapper(
    componentModel = SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
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
