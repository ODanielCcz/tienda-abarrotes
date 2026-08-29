package com.odcc.tienda.modules.catalog.adapter.in.rest.mapper;

import com.odcc.tienda.modules.catalog.adapter.in.rest.response.CatalogTaxResponse;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.CatalogUnitResponse;
import com.odcc.tienda.modules.catalog.domain.model.CatalogTax;
import com.odcc.tienda.modules.catalog.domain.model.CatalogUnit;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = CentralMapperConfig.class)
public interface CatalogReferenceDataRestMapper {
    CatalogUnitResponse toResponse(CatalogUnit unit);

    CatalogTaxResponse toResponse(CatalogTax tax);
}
