package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.command.CreateCategoryCommand;
import com.odcc.tienda.modules.catalog.domain.model.Category;

public interface CreateCategoryUseCase {

    Category execute(CreateCategoryCommand command);
}