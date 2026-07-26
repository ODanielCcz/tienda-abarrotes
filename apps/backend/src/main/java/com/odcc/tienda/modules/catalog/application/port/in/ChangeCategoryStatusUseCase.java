package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.command.ChangeCategoryStatusCommand;
import com.odcc.tienda.modules.catalog.domain.model.Category;

public interface ChangeCategoryStatusUseCase {

    Category execute(ChangeCategoryStatusCommand command);
}