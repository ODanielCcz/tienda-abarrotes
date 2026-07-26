package com.odcc.tienda.modules.catalog.adapter.out.persistence.repository;

import com.odcc.tienda.modules.catalog.adapter.out.persistence.entity.ProductJpaEntity;
import com.odcc.tienda.modules.catalog.domain.model.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SpringDataProductRepository extends JpaRepository<ProductJpaEntity, UUID> {

    @Query("""
        SELECT product
        FROM ProductJpaEntity product
        WHERE (
            :search = ''
            OR LOWER(product.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(product.description) LIKE LOWER(CONCAT('%', :search, '%'))
        )
        AND (:status IS NULL OR product.status = :status)
        AND (:categoryId IS NULL OR product.categoryId = :categoryId)
        AND (:brandId IS NULL OR product.brandId = :brandId)
        """)
    Page<ProductJpaEntity> search(
        @Param("search") String search,
        @Param("status") ProductStatus status,
        @Param("categoryId") UUID categoryId,
        @Param("brandId") UUID brandId,
        Pageable pageable
    );
}
