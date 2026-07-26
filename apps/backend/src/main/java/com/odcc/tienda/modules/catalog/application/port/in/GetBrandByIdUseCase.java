package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.domain.model.Brand;

import java.util.UUID;

public interface GetBrandByIdUseCase {

    Brand execute(UUID brandId);
}
