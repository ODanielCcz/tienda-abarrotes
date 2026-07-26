package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.exception.CategoryNotFoundException;
import com.odcc.tienda.modules.catalog.application.model.CategoryTreeNode;
import com.odcc.tienda.modules.catalog.application.query.CategoryTreeQuery;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;
import com.odcc.tienda.modules.catalog.support.InMemoryCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetCategoryTreeServiceTest {

    private InMemoryCategoryRepository repository;
    private GetCategoryTreeService treeService;
    private GetCategoryTreeByParentService treeByParentService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCategoryRepository();
        treeService = new GetCategoryTreeService(repository);
        treeByParentService = new GetCategoryTreeByParentService(repository);
    }

    @Test
    void shouldReturnEmptyTreeWhenThereAreNoCategories() {
        List<CategoryTreeNode> tree = treeService.execute(new CategoryTreeQuery(null));

        assertTrue(tree.isEmpty());
    }

    @Test
    void shouldBuildCategoryTreeWithChildren() {
        Category beverages = repository.save(Category.create("TREE-BEBIDAS", "Bebidas", null));
        repository.save(Category.create("TREE-REFRESCOS", "Refrescos", beverages.getId()));
        repository.save(Category.create("TREE-AGUA", "Agua", beverages.getId()));
        repository.save(Category.create("TREE-LIMPIEZA", "Limpieza", null));

        List<CategoryTreeNode> tree = treeService.execute(new CategoryTreeQuery(null));

        assertEquals(2, tree.size());
        CategoryTreeNode beveragesNode = findByCode(tree, "TREE-BEBIDAS");
        assertEquals(2, beveragesNode.children().size());
    }

    @Test
    void shouldFilterTreeByStatusAndPromoteOrphanChildrenAsRoots() {
        Category inactiveParent = repository.save(
            Category.create("TREE-INACTIVE-PARENT", "Padre inactivo", null)
                .changeStatus(CategoryStatus.INACTIVE)
        );
        repository.save(Category.create("TREE-ACTIVE-CHILD", "Hija activa", inactiveParent.getId()));

        List<CategoryTreeNode> tree = treeService.execute(new CategoryTreeQuery(CategoryStatus.ACTIVE));

        assertEquals(1, tree.size());
        assertEquals("TREE-ACTIVE-CHILD", tree.getFirst().code());
        assertEquals(inactiveParent.getId(), tree.getFirst().parentCategoryId());
    }

    @Test
    void shouldReturnTreeFromParentWithDescendants() {
        Category root = repository.save(Category.create("TREE-ROOT", "Raiz", null));
        Category child = repository.save(Category.create("TREE-CHILD", "Hija", root.getId()));
        repository.save(Category.create("TREE-GRANDCHILD", "Nieta", child.getId()));

        CategoryTreeNode tree = treeByParentService.execute(root.getId(), new CategoryTreeQuery(null));

        assertEquals("TREE-ROOT", tree.code());
        assertEquals(1, tree.children().size());
        assertEquals("TREE-CHILD", tree.children().getFirst().code());
        assertEquals("TREE-GRANDCHILD", tree.children().getFirst().children().getFirst().code());
    }

    @Test
    void shouldRejectUnknownParentTree() {
        UUID unknownId = UUID.randomUUID();

        assertThrows(
            CategoryNotFoundException.class,
            () -> treeByParentService.execute(unknownId, new CategoryTreeQuery(null))
        );
    }

    @Test
    void shouldRejectInactiveRootWhenActiveStatusIsRequested() {
        Category inactiveRoot = repository.save(
            Category.create("TREE-INACTIVE-ROOT", "Raiz inactiva", null)
                .changeStatus(CategoryStatus.INACTIVE)
        );

        assertThrows(
            CategoryNotFoundException.class,
            () -> treeByParentService.execute(inactiveRoot.getId(), new CategoryTreeQuery(CategoryStatus.ACTIVE))
        );
    }

    @Test
    void shouldHideInactiveChildrenWhenActiveStatusIsRequested() {
        Category root = repository.save(Category.create("TREE-ACTIVE-ROOT", "Raiz activa", null));
        repository.save(Category.create("TREE-ACTIVE-LEAF", "Activa", root.getId()));
        repository.save(
            Category.create("TREE-INACTIVE-LEAF", "Inactiva", root.getId())
                .changeStatus(CategoryStatus.INACTIVE)
        );

        CategoryTreeNode tree = treeByParentService.execute(root.getId(), new CategoryTreeQuery(CategoryStatus.ACTIVE));

        assertEquals(1, tree.children().size());
        assertEquals("TREE-ACTIVE-LEAF", tree.children().getFirst().code());
    }

    private static CategoryTreeNode findByCode(List<CategoryTreeNode> nodes, String code) {
        return nodes.stream()
            .filter(node -> node.code().equals(code))
            .findFirst()
            .orElseThrow();
    }
}
