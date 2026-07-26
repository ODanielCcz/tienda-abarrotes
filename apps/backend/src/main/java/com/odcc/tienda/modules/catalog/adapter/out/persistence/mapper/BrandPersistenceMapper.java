package com.odcc.tienda.modules.catalog.adapter.out.persistence.mapper;

import com.odcc.tienda.modules.catalog.adapter.out.persistence.entity.BrandJpaEntity;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface BrandPersistenceMapper {

    BrandJpaEntity toEntity(Brand brand);

    default Brand toDomain(BrandJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        return Brand.restore(
            entity.getId(),
            entity.getCode(),
            entity.getName(),
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }

}
