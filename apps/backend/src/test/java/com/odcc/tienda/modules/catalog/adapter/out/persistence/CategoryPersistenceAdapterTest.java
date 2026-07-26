package com.odcc.tienda.modules.catalog.adapter.out.persistence;

import com.odcc.tienda.TestcontainersConfiguration;
import com.odcc.tienda.modules.catalog.application.exception.CategoryCodeAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.port.out.CategoryRepositoryPort;
import com.odcc.tienda.modules.catalog.application.query.CategoryPage;
import com.odcc.tienda.modules.catalog.application.query.CategorySortField;
import com.odcc.tienda.modules.catalog.application.query.ListCategoriesQuery;
import com.odcc.tienda.modules.catalog.application.query.SortDirection;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class CategoryPersistenceAdapterTest {

    @Autowired
    private CategoryRepositoryPort categoryRepository;

    @Test
    void shouldSaveCategoryInPostgreSql() {
        Category category = Category.create("TEST-CATEGORY", "Categoria de prueba", null);

        Category savedCategory = categoryRepository.save(category);

        assertNotNull(savedCategory.getId());
        assertEquals("TEST-CATEGORY", savedCategory.getCode());
        assertEquals("Categoria de prueba", savedCategory.getName());
        assertEquals(CategoryStatus.ACTIVE, savedCategory.getStatus());
        assertNotNull(savedCategory.getCreatedAt());
        assertNotNull(savedCategory.getUpdatedAt());
        assertTrue(categoryRepository.existsByCode("TEST-CATEGORY"));
    }

    @Test
    void shouldSaveCategoryWithParentInPostgreSql() {
        Category parent = categoryRepository.save(Category.create("PERSIST-PARENT", "Padre", null));

        Category child = categoryRepository.save(Category.create("PERSIST-CHILD", "Hijo", parent.getId()));

        assertEquals(parent.getId(), child.getParentCategoryId());
    }

    @Test
    void shouldRejectDuplicatedCodeInPostgreSql() {
        categoryRepository.save(Category.create("DUPLICATED-CATEGORY", "Primera", null));
        Category duplicated = Category.create("DUPLICATED-CATEGORY", "Segunda", null);

        assertThrows(CategoryCodeAlreadyExistsException.class, () -> categoryRepository.save(duplicated));
    }

    @Test
    void shouldNotMapForeignKeyViolationAsDuplicatedCode() {
        Category category = Category.create("FK-CATEGORY-NOT-DUP", "Categoria con padre inexistente", UUID.randomUUID());

        assertThrows(DataIntegrityViolationException.class, () -> categoryRepository.save(category));
    }

    @Test
    void shouldFindCategoryByIdInPostgreSql() {
        Category category = categoryRepository.save(Category.create("FIND-CATEGORY", "Consultable", null));

        Optional<Category> foundCategory = categoryRepository.findById(category.getId());

        assertTrue(foundCategory.isPresent());
        assertEquals(category.getId(), foundCategory.get().getId());
        assertEquals("FIND-CATEGORY", foundCategory.get().getCode());
    }

    @Test
    void shouldReturnEmptyWhenCategoryDoesNotExistInPostgreSql() {
        Optional<Category> foundCategory = categoryRepository.findById(UUID.randomUUID());

        assertTrue(foundCategory.isEmpty());
    }

    @Test
    void shouldDetectCategoryAncestorInPostgreSql() {
        Category root = categoryRepository.save(Category.create("PERSIST-CHAIN-ROOT", "Raiz", null));
        Category child = categoryRepository.save(Category.create("PERSIST-CHAIN-CHILD", "Hija", root.getId()));

        assertTrue(categoryRepository.hasAncestor(child.getId(), root.getId()));
        assertEquals(false, categoryRepository.hasAncestor(root.getId(), child.getId()));
    }

    @Test
    void shouldFilterSortAndPaginateCategoriesInPostgreSql() {
        categoryRepository.save(Category.create("PERSIST-BEBIDAS", "Bebidas", null));
        categoryRepository.save(Category.create("PERSIST-LIMPIEZA", "Limpieza", null));
        categoryRepository.save(Category.create("PERSIST-BEBIDAS-INACTIVAS", "Bebidas inactivas", null).changeStatus(CategoryStatus.INACTIVE));

        CategoryPage page = categoryRepository.findAll(new ListCategoriesQuery(
            0,
            10,
            "bebidas",
            CategoryStatus.ACTIVE,
            CategorySortField.NAME,
            SortDirection.ASC
        ));

        assertEquals(1, page.totalElements());
        assertEquals("PERSIST-BEBIDAS", page.content().getFirst().getCode());
    }

    @Test
    void shouldUpdateExistingCategoryInPostgreSql() {
        Category saved = categoryRepository.save(Category.create("PERSIST-OLD-CAT", "Anterior", null));

        Category updated = categoryRepository.save(saved.update("PERSIST-NEW-CAT", "Actualizada", null));

        assertEquals(saved.getId(), updated.getId());
        assertEquals("PERSIST-NEW-CAT", updated.getCode());
        assertEquals("Actualizada", updated.getName());
    }

    @Test
    void shouldExcludeCurrentCategoryWhenCheckingDuplicatedCode() {
        Category saved = categoryRepository.save(Category.create("PERSIST-EXCLUDE-CAT", "Categoria", null));

        boolean duplicated = categoryRepository.existsByCodeAndIdNot(saved.getCode(), saved.getId());

        assertEquals(false, duplicated);
    }
    @Test
    void shouldFindAllCategoriesForTreeInPostgreSql() {
        Category root = categoryRepository.save(Category.create("PERSIST-TREE-ROOT", "Raiz arbol", null));
        categoryRepository.save(Category.create("PERSIST-TREE-CHILD", "Hija arbol", root.getId()));
        categoryRepository.save(Category.create("PERSIST-TREE-INACTIVE", "Inactiva arbol", null).changeStatus(CategoryStatus.INACTIVE));

        List<Category> categories = categoryRepository.findAllForTree(null);
        List<Category> activeCategories = categoryRepository.findAllForTree(CategoryStatus.ACTIVE);

        assertTrue(categories.stream().anyMatch(category -> category.getCode().equals("PERSIST-TREE-INACTIVE")));
        assertTrue(activeCategories.stream().noneMatch(category -> category.getCode().equals("PERSIST-TREE-INACTIVE")));
        assertTrue(activeCategories.stream().anyMatch(category -> category.getCode().equals("PERSIST-TREE-ROOT")));
        assertTrue(activeCategories.stream().anyMatch(category -> category.getCode().equals("PERSIST-TREE-CHILD")));
    }

    @Test
    void shouldFindDescendantsForTreeInPostgreSql() {
        Category root = categoryRepository.save(Category.create("PERSIST-DESC-ROOT", "Raiz descendientes", null));
        Category child = categoryRepository.save(Category.create("PERSIST-DESC-CHILD", "Hija descendientes", root.getId()));
        categoryRepository.save(Category.create("PERSIST-DESC-GRAND", "Nieta descendientes", child.getId()));
        categoryRepository.save(Category.create("PERSIST-DESC-OTHER", "Otra raiz", null));

        List<Category> descendants = categoryRepository.findDescendantsForTree(root.getId(), null);

        assertEquals(3, descendants.size());
        assertTrue(descendants.stream().anyMatch(category -> category.getCode().equals("PERSIST-DESC-ROOT")));
        assertTrue(descendants.stream().anyMatch(category -> category.getCode().equals("PERSIST-DESC-CHILD")));
        assertTrue(descendants.stream().anyMatch(category -> category.getCode().equals("PERSIST-DESC-GRAND")));
        assertTrue(descendants.stream().noneMatch(category -> category.getCode().equals("PERSIST-DESC-OTHER")));
    }

    @Test
    void shouldFindOnlyActiveDescendantsForTreeInPostgreSql() {
        Category root = categoryRepository.save(Category.create("PERSIST-ACTIVE-DESC-ROOT", "Raiz activa", null));
        categoryRepository.save(Category.create("PERSIST-ACTIVE-DESC-CHILD", "Hija activa", root.getId()));
        categoryRepository.save(Category.create("PERSIST-INACTIVE-DESC-CHILD", "Hija inactiva", root.getId()).changeStatus(CategoryStatus.INACTIVE));

        List<Category> descendants = categoryRepository.findDescendantsForTree(root.getId(), CategoryStatus.ACTIVE);

        assertEquals(2, descendants.size());
        assertTrue(descendants.stream().anyMatch(category -> category.getCode().equals("PERSIST-ACTIVE-DESC-ROOT")));
        assertTrue(descendants.stream().anyMatch(category -> category.getCode().equals("PERSIST-ACTIVE-DESC-CHILD")));
        assertTrue(descendants.stream().noneMatch(category -> category.getCode().equals("PERSIST-INACTIVE-DESC-CHILD")));
    }
}

