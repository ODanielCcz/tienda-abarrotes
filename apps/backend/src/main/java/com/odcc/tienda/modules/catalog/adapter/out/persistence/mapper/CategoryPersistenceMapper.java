package com.odcc.tienda.modules.catalog.adapter.out.persistence.mapper;

import com.odcc.tienda.modules.catalog.adapter.out.persistence.entity.CategoryJpaEntity;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface CategoryPersistenceMapper {

    CategoryJpaEntity toEntity(Category category);

    default Category toDomain(CategoryJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Category.restore(
            entity.getId(),
            entity.getParentCategoryId(),
            entity.getCode(),
            entity.getName(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}