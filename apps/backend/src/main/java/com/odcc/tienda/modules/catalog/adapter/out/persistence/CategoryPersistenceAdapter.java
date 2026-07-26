package com.odcc.tienda.modules.catalog.adapter.out.persistence;

import com.odcc.tienda.modules.catalog.adapter.out.persistence.entity.CategoryJpaEntity;
import com.odcc.tienda.modules.catalog.adapter.out.persistence.mapper.CategoryPersistenceMapper;
import com.odcc.tienda.modules.catalog.adapter.out.persistence.repository.SpringDataCategoryRepository;
import com.odcc.tienda.modules.catalog.application.exception.CategoryCodeAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.port.out.CategoryRepositoryPort;
import com.odcc.tienda.modules.catalog.application.query.CategoryPage;
import com.odcc.tienda.modules.catalog.application.query.CategorySortField;
import com.odcc.tienda.modules.catalog.application.query.ListCategoriesQuery;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;
import com.odcc.tienda.shared.infrastructure.persistence.DataIntegrityViolationClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CategoryPersistenceAdapter implements CategoryRepositoryPort {

    private static final String CATEGORY_CODE_UNIQUE_CONSTRAINT = "categories_code_key";

    private final SpringDataCategoryRepository repository;
    private final CategoryPersistenceMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCodeAndIdNot(String code, UUID excludedCategoryId) {
        return repository.existsByCodeAndIdNot(code, excludedCategoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findById(UUID categoryId) {
        return repository.findById(categoryId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAncestor(UUID categoryId, UUID ancestorCategoryId) {
        return repository.hasAncestor(categoryId, ancestorCategoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryPage findAll(ListCategoriesQuery query) {
        Sort.Direction direction = query.direction().name().equals("ASC")
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;

        PageRequest pageRequest = PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(direction, toJpaProperty(query.sortBy()))
        );

        Page<CategoryJpaEntity> page = repository.search(
            query.search() == null ? "" : query.search(),
            query.status(),
            pageRequest
        );

        List<Category> categories = page.getContent().stream().map(mapper::toDomain).toList();
        return new CategoryPage(
            categories,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findAllForTree(CategoryStatus status) {
        List<CategoryJpaEntity> categories = status == null
            ? repository.findAllForTree()
            : repository.findAllForTreeByStatus(status);

        return categories.stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findDescendantsForTree(UUID rootCategoryId, CategoryStatus status) {
        List<CategoryJpaEntity> categories = status == CategoryStatus.ACTIVE
            ? repository.findActiveDescendantsForTree(rootCategoryId)
            : repository.findDescendantsForTree(rootCategoryId);

        if (status != null && status != CategoryStatus.ACTIVE) {
            categories = categories.stream()
                .filter(category -> category.getStatus() == status)
                .toList();
        }

        return categories.stream().map(mapper::toDomain).toList();
    }

    @Override
    public Category save(Category category) {
        CategoryJpaEntity entity = mapper.toEntity(category);
        try {
            CategoryJpaEntity savedEntity = repository.saveAndFlush(entity);
            return mapper.toDomain(savedEntity);
        } catch (DataIntegrityViolationException exception) {
            if (DataIntegrityViolationClassifier.matchesConstraint(exception, CATEGORY_CODE_UNIQUE_CONSTRAINT)) {
                throw new CategoryCodeAlreadyExistsException(category.getCode());
            }

            throw exception;
        }
    }

    private static String toJpaProperty(CategorySortField sortField) {
        return switch (sortField) {
            case CODE -> "code";
            case NAME -> "name";
            case CREATED_AT -> "createdAt";
            case UPDATED_AT -> "updatedAt";
        };
    }
}
