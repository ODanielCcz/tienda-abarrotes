package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.exception.CategoryNotFoundException;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import com.odcc.tienda.modules.catalog.support.InMemoryCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetCategoryByIdServiceTest {

    private InMemoryCategoryRepository repository;
    private GetCategoryByIdService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCategoryRepository();
        service = new GetCategoryByIdService(repository);
    }

    @Test
    void shouldReturnCategoryById() {
        Category category = repository.save(Category.create("GET", "Consulta", null));

        Category found = service.execute(category.getId());

        assertEquals(category.getId(), found.getId());
    }

    @Test
    void shouldRejectUnknownCategory() {
        assertThrows(CategoryNotFoundException.class, () -> service.execute(UUID.randomUUID()));
    }
}