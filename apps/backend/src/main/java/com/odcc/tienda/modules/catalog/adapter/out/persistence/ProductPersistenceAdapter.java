package com.odcc.tienda.modules.catalog.adapter.out.persistence;

import com.odcc.tienda.modules.catalog.adapter.out.persistence.entity.ProductJpaEntity;
import com.odcc.tienda.modules.catalog.adapter.out.persistence.mapper.ProductPersistenceMapper;
import com.odcc.tienda.modules.catalog.adapter.out.persistence.repository.SpringDataProductRepository;
import com.odcc.tienda.modules.catalog.application.port.out.ProductRepositoryPort;
import com.odcc.tienda.modules.catalog.application.query.ListProductsQuery;
import com.odcc.tienda.modules.catalog.application.query.ProductPage;
import com.odcc.tienda.modules.catalog.application.query.ProductSortField;
import com.odcc.tienda.modules.catalog.domain.model.Product;
import lombok.RequiredArgsConstructor;
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
public class ProductPersistenceAdapter implements ProductRepositoryPort {

    private final SpringDataProductRepository repository;
    private final ProductPersistenceMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(UUID productId) {
        return repository.findById(productId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductPage findAll(ListProductsQuery query) {
        Sort.Direction direction = query.direction().name().equals("ASC")
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;

        PageRequest pageRequest = PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(direction, toJpaProperty(query.sortBy()))
        );

        Page<ProductJpaEntity> page = repository.search(
            query.search() == null ? "" : query.search(),
            query.status(),
            query.categoryId(),
            query.brandId(),
            pageRequest
        );

        List<Product> products = page.getContent().stream().map(mapper::toDomain).toList();
        return new ProductPage(
            products,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    @Override
    public Product save(Product product) {
        ProductJpaEntity entity = mapper.toEntity(product);
        ProductJpaEntity savedEntity = repository.saveAndFlush(entity);
        return mapper.toDomain(savedEntity);
    }

    private static String toJpaProperty(ProductSortField sortField) {
        return switch (sortField) {
            case NAME -> "name";
            case CREATED_AT -> "createdAt";
            case UPDATED_AT -> "updatedAt";
        };
    }
}
