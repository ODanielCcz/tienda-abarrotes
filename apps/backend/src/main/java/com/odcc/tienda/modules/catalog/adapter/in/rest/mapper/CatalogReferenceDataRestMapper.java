package com.odcc.tienda.modules.catalog.adapter.in.rest.mapper;

import com.odcc.tienda.modules.catalog.adapter.in.rest.response.CatalogTaxResponse;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.CatalogUnitResponse;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.CatalogPriceResponse;
import com.odcc.tienda.modules.catalog.application.model.CatalogPrice;
import com.odcc.tienda.modules.catalog.domain.model.CatalogTax;
import com.odcc.tienda.modules.catalog.domain.model.CatalogUnit;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = CentralMapperConfig.class)
public interface CatalogReferenceDataRestMapper {
    CatalogPriceResponse toResponse(CatalogPrice price);
    CatalogUnitResponse toResponse(CatalogUnit unit);

    CatalogTaxResponse toResponse(CatalogTax tax);
}
