package com.odcc.tienda.modules.catalog.adapter.in.rest.mapper;

import com.odcc.tienda.modules.catalog.adapter.in.rest.request.ChangeBrandStatusRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.ChangeCategoryStatusRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.ChangeProductPresentationStatusRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.ChangeProductStatusRequest;
import com.odcc.tienda.modules.catalog.domain.model.BrandStatus;
import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;
import com.odcc.tienda.modules.catalog.domain.model.ProductPresentationStatus;
import com.odcc.tienda.modules.catalog.domain.model.ProductStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogStatusRestMappersTest {

    @Test
    void mapsCatalogIdentifiersFromRoutesAndStatusesFromRequests() {
        UUID id = UUID.randomUUID();

        var brand = Mappers.getMapper(BrandRestMapper.class)
            .toStatusCommand(id, new ChangeBrandStatusRequest(BrandStatus.INACTIVE));
        var category = Mappers.getMapper(CategoryRestMapper.class)
            .toStatusCommand(id, new ChangeCategoryStatusRequest(CategoryStatus.INACTIVE));
        var product = Mappers.getMapper(ProductRestMapper.class)
            .toStatusCommand(id, new ChangeProductStatusRequest(ProductStatus.INACTIVE));
        var presentation = Mappers.getMapper(ProductPresentationRestMapper.class)
            .toStatusCommand(id, new ChangeProductPresentationStatusRequest(ProductPresentationStatus.INACTIVE));

        assertThat(brand.brandId()).isEqualTo(id);
        assertThat(brand.status()).isEqualTo(BrandStatus.INACTIVE);
        assertThat(category.categoryId()).isEqualTo(id);
        assertThat(category.status()).isEqualTo(CategoryStatus.INACTIVE);
        assertThat(product.productId()).isEqualTo(id);
        assertThat(product.status()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(presentation.presentationId()).isEqualTo(id);
        assertThat(presentation.status()).isEqualTo(ProductPresentationStatus.INACTIVE);
    }
}
