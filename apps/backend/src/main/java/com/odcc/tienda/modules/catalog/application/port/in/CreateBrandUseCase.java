package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.command.CreateBrandCommand;
import com.odcc.tienda.modules.catalog.domain.model.Brand;

public interface CreateBrandUseCase {

    Brand execute(CreateBrandCommand command);
}
