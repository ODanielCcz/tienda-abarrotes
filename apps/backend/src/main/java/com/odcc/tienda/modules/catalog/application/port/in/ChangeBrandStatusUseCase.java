package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.command.ChangeBrandStatusCommand;
import com.odcc.tienda.modules.catalog.domain.model.Brand;

public interface ChangeBrandStatusUseCase {

    Brand execute(ChangeBrandStatusCommand command);
}
