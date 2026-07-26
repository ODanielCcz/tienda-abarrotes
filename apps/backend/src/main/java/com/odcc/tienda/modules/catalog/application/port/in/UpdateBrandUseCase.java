package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.command.UpdateBrandCommand;
import com.odcc.tienda.modules.catalog.domain.model.Brand;

public interface UpdateBrandUseCase {

    Brand execute(UpdateBrandCommand command);
}
