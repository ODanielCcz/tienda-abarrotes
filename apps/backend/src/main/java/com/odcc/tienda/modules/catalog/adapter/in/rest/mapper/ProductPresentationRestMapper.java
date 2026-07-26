package com.odcc.tienda.modules.catalog.adapter.in.rest.mapper;

import com.odcc.tienda.modules.catalog.adapter.in.rest.request.CreateProductPresentationRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.UpdateProductPresentationRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.ProductPresentationResponse;
import com.odcc.tienda.modules.catalog.application.command.CreateProductPresentationCommand;
import com.odcc.tienda.modules.catalog.application.command.UpdateProductPresentationCommand;
import com.odcc.tienda.modules.catalog.domain.model.ProductPresentation;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

@Mapper(
    componentModel = SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface ProductPresentationRestMapper {

    default CreateProductPresentationCommand toCreateCommand(UUID productId, CreateProductPresentationRequest request) {
        return new CreateProductPresentationCommand(
            productId,
            request.unitId(),
            request.taxId(),
            request.sku(),
            request.name(),
            request.conversionFactor(),
            request.netContent(),
            request.minimumStock()
        );
    }

    default UpdateProductPresentationCommand toUpdateCommand(UUID presentationId, UpdateProductPresentationRequest request) {
        return new UpdateProductPresentationCommand(
            presentationId,
            request.unitId(),
            request.taxId(),
            request.sku(),
            request.name(),
            request.conversionFactor(),
            request.netContent(),
            request.minimumStock()
        );
    }

    ProductPresentationResponse toResponse(ProductPresentation presentation);
}
