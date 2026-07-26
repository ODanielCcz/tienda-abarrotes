package com.odcc.tienda.modules.catalog.application.port.out;

import com.odcc.tienda.modules.catalog.domain.model.Brand;
import com.odcc.tienda.modules.catalog.application.query.BrandPage;
import com.odcc.tienda.modules.catalog.application.query.ListBrandsQuery;

import java.util.Optional;
import java.util.UUID;

public interface BrandRepositoryPort {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID excludedBrandId);

    Optional<Brand> findById(UUID brandId);

    BrandPage findAll(ListBrandsQuery query);

    Brand save(Brand brand);
}
