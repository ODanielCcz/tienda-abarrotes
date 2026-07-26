package com.odcc.tienda.modules.catalog.adapter.out.persistence;

import com.odcc.tienda.modules.catalog.adapter.out.persistence.entity.BrandJpaEntity;
import com.odcc.tienda.modules.catalog.adapter.out.persistence.mapper.BrandPersistenceMapper;
import com.odcc.tienda.modules.catalog.adapter.out.persistence.repository.SpringDataBrandRepository;
import com.odcc.tienda.modules.catalog.application.exception.BrandCodeAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.port.out.BrandRepositoryPort;
import com.odcc.tienda.modules.catalog.application.query.BrandPage;
import com.odcc.tienda.modules.catalog.application.query.BrandSortField;
import com.odcc.tienda.modules.catalog.application.query.ListBrandsQuery;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import com.odcc.tienda.shared.infrastructure.persistence.DataIntegrityViolationClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BrandPersistenceAdapter implements BrandRepositoryPort {

    private static final String BRAND_CODE_UNIQUE_CONSTRAINT = "brands_code_key";

    private final SpringDataBrandRepository repository;
    private final BrandPersistenceMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCodeAndIdNot(
        String code,
        UUID excludedBrandId
    ) {
        return repository.existsByCodeAndIdNot(code, excludedBrandId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Brand> findById(UUID brandId) {
        return repository
            .findById(brandId)
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public BrandPage findAll(ListBrandsQuery query) {
        Sort.Direction direction = query.direction().name().equals("ASC")
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;

        PageRequest pageRequest = PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(direction, toJpaProperty(query.sortBy()))
        );

        Page<BrandJpaEntity> page = repository.search(
            query.search() == null ? "" : query.search(),
            query.status(),
            pageRequest
        );

        List<Brand> brands = page
            .getContent()
            .stream()
            .map(mapper::toDomain)
            .toList();

        return new BrandPage(
            brands,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    @Override
    public Brand save(Brand brand) {
        BrandJpaEntity entity = mapper.toEntity(brand);

        try {
            BrandJpaEntity savedEntity = repository.saveAndFlush(entity);

            return mapper.toDomain(savedEntity);
        } catch (DataIntegrityViolationException exception) {
            if (DataIntegrityViolationClassifier.matchesConstraint(exception, BRAND_CODE_UNIQUE_CONSTRAINT)) {
                throw new BrandCodeAlreadyExistsException(
                    brand.getCode()
                );
            }

            throw exception;
        }
    }

    private static String toJpaProperty(BrandSortField sortField) {
        return switch (sortField) {
            case CODE -> "code";
            case NAME -> "name";
            case CREATED_AT -> "createdAt";
        };
    }
}
