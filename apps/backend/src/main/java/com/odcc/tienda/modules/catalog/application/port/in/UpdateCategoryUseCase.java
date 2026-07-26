package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.command.UpdateCategoryCommand;
import com.odcc.tienda.modules.catalog.domain.model.Category;

public interface UpdateCategoryUseCase {

    Category execute(UpdateCategoryCommand command);
}