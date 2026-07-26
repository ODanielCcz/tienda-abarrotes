package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.query.CategoryPage;
import com.odcc.tienda.modules.catalog.application.query.CategorySortField;
import com.odcc.tienda.modules.catalog.application.query.ListCategoriesQuery;
import com.odcc.tienda.modules.catalog.application.query.SortDirection;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;
import com.odcc.tienda.modules.catalog.support.InMemoryCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListCategoriesServiceTest {

    private InMemoryCategoryRepository repository;
    private ListCategoriesService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCategoryRepository();
        service = new ListCategoriesService(repository);
    }

    @Test
    void shouldFilterSortAndPaginateCategories() {
        repository.save(Category.create("BEBIDAS", "Bebidas", null));
        repository.save(Category.create("LIMPIEZA", "Limpieza", null));
        repository.save(Category.create("BEBIDAS-FRIAS", "Bebidas frias", null).changeStatus(CategoryStatus.INACTIVE));

        CategoryPage page = service.execute(new ListCategoriesQuery(
            0,
            10,
            "bebidas",
            CategoryStatus.ACTIVE,
            CategorySortField.CODE,
            SortDirection.ASC
        ));

        assertEquals(1, page.totalElements());
        assertEquals("BEBIDAS", page.content().getFirst().getCode());
    }

    @Test
    void shouldRejectInvalidPageSize() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ListCategoriesQuery(0, 101, null, null, null, null)
        );
    }
}