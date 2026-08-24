package com.odcc.tienda.modules.catalog.adapter.out.persistence.mapper;

import com.odcc.tienda.modules.catalog.adapter.out.persistence.entity.ProductJpaEntity;
import com.odcc.tienda.modules.catalog.domain.model.Product;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = CentralMapperConfig.class)
public interface ProductPersistenceMapper {

    ProductJpaEntity toEntity(Product product);

    default Product toDomain(ProductJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Product.restore(
            entity.getId(),
            entity.getCategoryId(),
            entity.getBrandId(),
            entity.getName(),
            entity.getDescription(),
            entity.getProductType(),
            entity.isTracksInventory(),
            entity.isTracksLots(),
            entity.isTracksExpiration(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
