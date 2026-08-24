package com.odcc.tienda.modules.catalog.adapter.in.rest.mapper;

import com.odcc.tienda.modules.catalog.adapter.in.rest.request.ChangeProductStatusRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.CreateProductRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.UpdateProductRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.ProductResponse;
import com.odcc.tienda.modules.catalog.application.command.ChangeProductStatusCommand;
import com.odcc.tienda.modules.catalog.application.command.CreateProductCommand;
import com.odcc.tienda.modules.catalog.application.command.UpdateProductCommand;
import com.odcc.tienda.modules.catalog.domain.model.Product;
import com.odcc.tienda.modules.catalog.domain.model.ProductType;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(config = CentralMapperConfig.class)
public interface ProductRestMapper {

    default CreateProductCommand toCommand(CreateProductRequest request) {
        return new CreateProductCommand(
            request.categoryId(),
            request.brandId(),
            request.name(),
            request.description(),
            request.productType() == null ? ProductType.GOODS : request.productType(),
            request.tracksInventory() == null || request.tracksInventory(),
            request.tracksLots() != null && request.tracksLots(),
            request.tracksExpiration() != null && request.tracksExpiration()
        );
    }

    default UpdateProductCommand toCommand(UUID productId, UpdateProductRequest request) {
        return new UpdateProductCommand(
            productId,
            request.categoryId(),
            request.brandId(),
            request.name(),
            request.description(),
            request.productType() == null ? ProductType.GOODS : request.productType(),
            request.tracksInventory() == null || request.tracksInventory(),
            request.tracksLots() != null && request.tracksLots(),
            request.tracksExpiration() != null && request.tracksExpiration()
        );
    }

    @Mapping(target = "productId", source = "productId")
    @Mapping(target = "status", source = "request.status")
    ChangeProductStatusCommand toStatusCommand(UUID productId, ChangeProductStatusRequest request);

    ProductResponse toResponse(Product product);
}
