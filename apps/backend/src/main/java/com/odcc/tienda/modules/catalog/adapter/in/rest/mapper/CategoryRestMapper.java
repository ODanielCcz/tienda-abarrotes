package com.odcc.tienda.modules.catalog.adapter.in.rest.mapper;

import com.odcc.tienda.modules.catalog.adapter.in.rest.request.CreateCategoryRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.UpdateCategoryRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.CategoryResponse;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.CategoryTreeResponse;
import com.odcc.tienda.modules.catalog.application.command.CreateCategoryCommand;
import com.odcc.tienda.modules.catalog.application.command.UpdateCategoryCommand;
import com.odcc.tienda.modules.catalog.application.model.CategoryTreeNode;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

@Mapper(
    componentModel = SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface CategoryRestMapper {

    CreateCategoryCommand toCommand(CreateCategoryRequest request);

    @Mapping(target = "categoryId", source = "categoryId")
    @Mapping(target = "code", source = "request.code")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "parentCategoryId", source = "request.parentCategoryId")
    UpdateCategoryCommand toCommand(UUID categoryId, UpdateCategoryRequest request);

    CategoryResponse toResponse(Category category);

    CategoryTreeResponse toResponse(CategoryTreeNode node);
}
