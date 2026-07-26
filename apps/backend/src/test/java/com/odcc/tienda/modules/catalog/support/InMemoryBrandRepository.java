package com.odcc.tienda.modules.catalog.support;

import com.odcc.tienda.modules.catalog.application.port.out.BrandRepositoryPort;
import com.odcc.tienda.modules.catalog.application.query.BrandPage;
import com.odcc.tienda.modules.catalog.application.query.BrandSortField;
import com.odcc.tienda.modules.catalog.application.query.ListBrandsQuery;
import com.odcc.tienda.modules.catalog.application.query.SortDirection;
import com.odcc.tienda.modules.catalog.domain.model.Brand;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryBrandRepository implements BrandRepositoryPort {

    private final Map<UUID, Brand> brands = new LinkedHashMap<>();
    private int saveCount;

    @Override
    public boolean existsByCode(String code) {
        return brands
            .values()
            .stream()
            .anyMatch(brand -> brand.getCode().equals(code));
    }

    @Override
    public boolean existsByCodeAndIdNot(
        String code,
        UUID excludedBrandId
    ) {
        return brands
            .values()
            .stream()
            .anyMatch(brand ->
                !brand.getId().equals(excludedBrandId)
                    && brand.getCode().equals(code)
            );
    }

    @Override
    public Optional<Brand> findById(UUID brandId) {
        return Optional.ofNullable(brands.get(brandId));
    }

    @Override
    public BrandPage findAll(ListBrandsQuery query) {
        Comparator<Brand> comparator = comparator(query.sortBy());

        if (query.direction() == SortDirection.DESC) {
            comparator = comparator.reversed();
        }

        var filtered = brands
            .values()
            .stream()
            .filter(brand -> matchesSearch(brand, query.search()))
            .filter(brand ->
                query.status() == null || brand.getStatus() == query.status()
            )
            .sorted(comparator)
            .toList();

        int fromIndex = Math.min(query.page() * query.size(), filtered.size());
        int toIndex = Math.min(fromIndex + query.size(), filtered.size());
        int totalPages = filtered.isEmpty()
            ? 0
            : (int) Math.ceil((double) filtered.size() / query.size());

        return new BrandPage(
            filtered.subList(fromIndex, toIndex),
            query.page(),
            query.size(),
            filtered.size(),
            totalPages
        );
    }

    @Override
    public Brand save(Brand brand) {
        brands.put(brand.getId(), brand);
        saveCount++;
        return brand;
    }

    public int saveCount() {
        return saveCount;
    }

    private static boolean matchesSearch(Brand brand, String search) {
        if (search == null) {
            return true;
        }

        String normalizedSearch = search.toLowerCase(Locale.ROOT);

        return brand.getCode().toLowerCase(Locale.ROOT).contains(normalizedSearch)
            || brand.getName().toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }

    private static Comparator<Brand> comparator(BrandSortField sortField) {
        return switch (sortField) {
            case CODE -> Comparator.comparing(Brand::getCode);
            case NAME -> Comparator.comparing(Brand::getName);
            case CREATED_AT -> Comparator.comparing(Brand::getCreatedAt);
        };
    }
}
