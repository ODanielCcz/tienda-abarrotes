package com.odcc.tienda.modules.catalog.adapter.out.persistence.repository;

import com.odcc.tienda.modules.catalog.adapter.out.persistence.entity.BrandJpaEntity;
import com.odcc.tienda.modules.catalog.domain.model.BrandStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SpringDataBrandRepository extends JpaRepository<BrandJpaEntity, UUID> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID excludedBrandId);

    @Query("""
        SELECT brand
        FROM BrandJpaEntity brand
        WHERE (
            :search = ''
            OR LOWER(brand.code) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(brand.name) LIKE LOWER(CONCAT('%', :search, '%'))
        )
        AND (:status IS NULL OR brand.status = :status)
        """)
    Page<BrandJpaEntity> search(
        @Param("search") String search,
        @Param("status") BrandStatus status,
        Pageable pageable
    );
}
