package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.model.CategoryTreeNode;
import com.odcc.tienda.modules.catalog.domain.model.Category;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CategoryTreeAssembler {

    private CategoryTreeAssembler() {
    }

    static List<CategoryTreeNode> assembleForest(List<Category> categories) {
        Map<UUID, MutableCategoryTreeNode> nodes = toMutableNodes(categories);
        List<MutableCategoryTreeNode> roots = new ArrayList<>();

        for (MutableCategoryTreeNode node : nodes.values()) {
            UUID parentCategoryId = node.category.getParentCategoryId();
            MutableCategoryTreeNode parent = parentCategoryId == null ? null : nodes.get(parentCategoryId);

            if (parent == null) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        }

        return roots.stream().map(MutableCategoryTreeNode::toImmutable).toList();
    }

    static CategoryTreeNode assembleRoot(UUID rootCategoryId, List<Category> categories) {
        Map<UUID, MutableCategoryTreeNode> nodes = toMutableNodes(categories);
        MutableCategoryTreeNode root = nodes.get(rootCategoryId);

        if (root == null) {
            return null;
        }

        for (MutableCategoryTreeNode node : nodes.values()) {
            if (node.category.getId().equals(rootCategoryId)) {
                continue;
            }

            MutableCategoryTreeNode parent = nodes.get(node.category.getParentCategoryId());
            if (parent != null) {
                parent.children.add(node);
            }
        }

        return root.toImmutable();
    }

    private static Map<UUID, MutableCategoryTreeNode> toMutableNodes(List<Category> categories) {
        Map<UUID, MutableCategoryTreeNode> nodes = new LinkedHashMap<>();
        for (Category category : categories) {
            nodes.put(category.getId(), new MutableCategoryTreeNode(category));
        }
        return nodes;
    }

    private static final class MutableCategoryTreeNode {

        private final Category category;
        private final List<MutableCategoryTreeNode> children = new ArrayList<>();

        private MutableCategoryTreeNode(Category category) {
            this.category = category;
        }

        private CategoryTreeNode toImmutable() {
            return new CategoryTreeNode(
                category.getId(),
                category.getParentCategoryId(),
                category.getCode(),
                category.getName(),
                category.getStatus(),
                category.getCreatedAt(),
                category.getUpdatedAt(),
                children.stream().map(MutableCategoryTreeNode::toImmutable).toList()
            );
        }
    }
}
