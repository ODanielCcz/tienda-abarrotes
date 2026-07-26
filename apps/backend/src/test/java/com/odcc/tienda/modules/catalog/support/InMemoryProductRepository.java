package com.odcc.tienda.modules.catalog.support;

import com.odcc.tienda.modules.catalog.application.port.out.ProductRepositoryPort;
import com.odcc.tienda.modules.catalog.application.query.ListProductsQuery;
import com.odcc.tienda.modules.catalog.application.query.ProductPage;
import com.odcc.tienda.modules.catalog.application.query.ProductSortField;
import com.odcc.tienda.modules.catalog.application.query.SortDirection;
import com.odcc.tienda.modules.catalog.domain.model.Product;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryProductRepository implements ProductRepositoryPort {

    private final Map<UUID, Product> products = new LinkedHashMap<>();

    @Override
    public Optional<Product> findById(UUID productId) {
        return Optional.ofNullable(products.get(productId));
    }

    @Override
    public ProductPage findAll(ListProductsQuery query) {
        Comparator<Product> comparator = comparator(query.sortBy());
        if (query.direction() == SortDirection.DESC) {
            comparator = comparator.reversed();
        }

        var filtered = products.values().stream()
            .filter(product -> matchesSearch(product, query.search()))
            .filter(product -> query.status() == null || product.getStatus() == query.status())
            .filter(product -> query.categoryId() == null || query.categoryId().equals(product.getCategoryId()))
            .filter(product -> query.brandId() == null || query.brandId().equals(product.getBrandId()))
            .sorted(comparator)
            .toList();

        int fromIndex = Math.min(query.page() * query.size(), filtered.size());
        int toIndex = Math.min(fromIndex + query.size(), filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / query.size());
        return new ProductPage(filtered.subList(fromIndex, toIndex), query.page(), query.size(), filtered.size(), totalPages);
    }

    @Override
    public Product save(Product product) {
        products.put(product.getId(), product);
        return product;
    }

    private static boolean matchesSearch(Product product, String search) {
        if (search == null) {
            return true;
        }
        String normalizedSearch = search.toLowerCase(Locale.ROOT);
        return product.getName().toLowerCase(Locale.ROOT).contains(normalizedSearch)
            || (product.getDescription() != null && product.getDescription().toLowerCase(Locale.ROOT).contains(normalizedSearch));
    }

    private static Comparator<Product> comparator(ProductSortField sortField) {
        return switch (sortField) {
            case NAME -> Comparator.comparing(Product::getName);
            case CREATED_AT -> Comparator.comparing(Product::getCreatedAt);
            case UPDATED_AT -> Comparator.comparing(Product::getUpdatedAt);
        };
    }
}
