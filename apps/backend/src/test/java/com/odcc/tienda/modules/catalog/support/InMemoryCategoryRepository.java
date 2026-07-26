package com.odcc.tienda.modules.catalog.support;

import com.odcc.tienda.modules.catalog.application.port.out.CategoryRepositoryPort;
import com.odcc.tienda.modules.catalog.application.query.CategoryPage;
import com.odcc.tienda.modules.catalog.application.query.CategorySortField;
import com.odcc.tienda.modules.catalog.application.query.ListCategoriesQuery;
import com.odcc.tienda.modules.catalog.application.query.SortDirection;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class InMemoryCategoryRepository implements CategoryRepositoryPort {

    private final Map<UUID, Category> categories = new LinkedHashMap<>();
    private int saveCount;

    @Override
    public boolean existsByCode(String code) {
        return categories.values().stream().anyMatch(category -> category.getCode().equals(code));
    }

    @Override
    public boolean existsByCodeAndIdNot(String code, UUID excludedCategoryId) {
        return categories.values().stream().anyMatch(category ->
            !category.getId().equals(excludedCategoryId) && category.getCode().equals(code)
        );
    }

    @Override
    public Optional<Category> findById(UUID categoryId) {
        return Optional.ofNullable(categories.get(categoryId));
    }

    @Override
    public boolean hasAncestor(UUID categoryId, UUID ancestorCategoryId) {
        Set<UUID> visited = new HashSet<>();
        UUID currentCategoryId = categoryId;

        while (currentCategoryId != null && visited.add(currentCategoryId)) {
            if (currentCategoryId.equals(ancestorCategoryId)) {
                return true;
            }

            Category currentCategory = categories.get(currentCategoryId);
            currentCategoryId = currentCategory == null ? null : currentCategory.getParentCategoryId();
        }

        return false;
    }

    @Override
    public CategoryPage findAll(ListCategoriesQuery query) {
        Comparator<Category> comparator = comparator(query.sortBy());
        if (query.direction() == SortDirection.DESC) {
            comparator = comparator.reversed();
        }

        var filtered = categories.values().stream()
            .filter(category -> matchesSearch(category, query.search()))
            .filter(category -> query.status() == null || category.getStatus() == query.status())
            .sorted(comparator)
            .toList();

        int fromIndex = Math.min(query.page() * query.size(), filtered.size());
        int toIndex = Math.min(fromIndex + query.size(), filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / query.size());

        return new CategoryPage(filtered.subList(fromIndex, toIndex), query.page(), query.size(), filtered.size(), totalPages);
    }

    @Override
    public List<Category> findAllForTree(CategoryStatus status) {
        return categories.values().stream()
            .filter(category -> status == null || category.getStatus() == status)
            .sorted(treeComparator())
            .toList();
    }

    @Override
    public List<Category> findDescendantsForTree(UUID rootCategoryId, CategoryStatus status) {
        Category root = categories.get(rootCategoryId);
        if (root == null || (status != null && root.getStatus() != status)) {
            return List.of();
        }

        List<Category> descendants = new ArrayList<>();
        collectDescendants(root, status, descendants);
        return descendants.stream().sorted(treeComparator()).toList();
    }

    @Override
    public Category save(Category category) {
        categories.put(category.getId(), category);
        saveCount++;
        return category;
    }

    public int saveCount() {
        return saveCount;
    }

    private void collectDescendants(Category category, CategoryStatus status, List<Category> descendants) {
        descendants.add(category);

        categories.values().stream()
            .filter(child -> category.getId().equals(child.getParentCategoryId()))
            .filter(child -> status == null || child.getStatus() == status)
            .sorted(Comparator.comparing(Category::getName))
            .forEach(child -> collectDescendants(child, status, descendants));
    }

    private static boolean matchesSearch(Category category, String search) {
        if (search == null) {
            return true;
        }
        String normalizedSearch = search.toLowerCase(Locale.ROOT);
        return category.getCode().toLowerCase(Locale.ROOT).contains(normalizedSearch)
            || category.getName().toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }

    private static Comparator<Category> comparator(CategorySortField sortField) {
        return switch (sortField) {
            case CODE -> Comparator.comparing(Category::getCode);
            case NAME -> Comparator.comparing(Category::getName);
            case CREATED_AT -> Comparator.comparing(Category::getCreatedAt);
            case UPDATED_AT -> Comparator.comparing(Category::getUpdatedAt);
        };
    }

    private static Comparator<Category> treeComparator() {
        return Comparator
            .comparing((Category category) -> category.getParentCategoryId() == null ? 0 : 1)
            .thenComparing(category -> Optional.ofNullable(category.getParentCategoryId()).map(UUID::toString).orElse(""))
            .thenComparing(Category::getName);
    }
}
