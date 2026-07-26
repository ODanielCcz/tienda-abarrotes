package com.odcc.tienda.modules.catalog.adapter.out.persistence.repository;

import com.odcc.tienda.modules.catalog.adapter.out.persistence.entity.CategoryJpaEntity;
import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpringDataCategoryRepository extends JpaRepository<CategoryJpaEntity, UUID> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID excludedCategoryId);

    @Query(value = """
        WITH RECURSIVE parent_chain AS (
            SELECT category_id, parent_category_id
            FROM catalog.categories
            WHERE category_id = :categoryId

            UNION ALL

            SELECT parent.category_id, parent.parent_category_id
            FROM catalog.categories parent
            INNER JOIN parent_chain child
                ON parent.category_id = child.parent_category_id
        )
        SELECT EXISTS (
            SELECT 1
            FROM parent_chain
            WHERE category_id = :ancestorCategoryId
        )
        """, nativeQuery = true)
    boolean hasAncestor(
        @Param("categoryId") UUID categoryId,
        @Param("ancestorCategoryId") UUID ancestorCategoryId
    );

    @Query("""
        SELECT category
        FROM CategoryJpaEntity category
        WHERE (
            :search = ''
            OR LOWER(category.code) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(category.name) LIKE LOWER(CONCAT('%', :search, '%'))
        )
        AND (:status IS NULL OR category.status = :status)
        """)
    Page<CategoryJpaEntity> search(
        @Param("search") String search,
        @Param("status") CategoryStatus status,
        Pageable pageable
    );

    @Query("""
        SELECT category
        FROM CategoryJpaEntity category
        ORDER BY
            CASE WHEN category.parentCategoryId IS NULL THEN 0 ELSE 1 END,
            category.parentCategoryId,
            category.name
        """)
    List<CategoryJpaEntity> findAllForTree();

    @Query("""
        SELECT category
        FROM CategoryJpaEntity category
        WHERE category.status = :status
        ORDER BY
            CASE WHEN category.parentCategoryId IS NULL THEN 0 ELSE 1 END,
            category.parentCategoryId,
            category.name
        """)
    List<CategoryJpaEntity> findAllForTreeByStatus(@Param("status") CategoryStatus status);

    @Query(value = """
        WITH RECURSIVE category_tree AS (
            SELECT
                category_id,
                parent_category_id,
                code,
                name,
                status,
                created_at,
                updated_at,
                0 AS depth
            FROM catalog.categories
            WHERE category_id = :rootCategoryId

            UNION ALL

            SELECT
                child.category_id,
                child.parent_category_id,
                child.code,
                child.name,
                child.status,
                child.created_at,
                child.updated_at,
                category_tree.depth + 1 AS depth
            FROM catalog.categories child
            INNER JOIN category_tree
                ON child.parent_category_id = category_tree.category_id
        )
        SELECT
            category_id,
            parent_category_id,
            code,
            name,
            status,
            created_at,
            updated_at
        FROM category_tree
        ORDER BY depth, parent_category_id NULLS FIRST, name
        """, nativeQuery = true)
    List<CategoryJpaEntity> findDescendantsForTree(@Param("rootCategoryId") UUID rootCategoryId);

    @Query(value = """
        WITH RECURSIVE category_tree AS (
            SELECT
                category_id,
                parent_category_id,
                code,
                name,
                status,
                created_at,
                updated_at,
                0 AS depth
            FROM catalog.categories
            WHERE category_id = :rootCategoryId
              AND status = 'ACTIVE'

            UNION ALL

            SELECT
                child.category_id,
                child.parent_category_id,
                child.code,
                child.name,
                child.status,
                child.created_at,
                child.updated_at,
                category_tree.depth + 1 AS depth
            FROM catalog.categories child
            INNER JOIN category_tree
                ON child.parent_category_id = category_tree.category_id
            WHERE child.status = 'ACTIVE'
        )
        SELECT
            category_id,
            parent_category_id,
            code,
            name,
            status,
            created_at,
            updated_at
        FROM category_tree
        ORDER BY depth, parent_category_id NULLS FIRST, name
        """, nativeQuery = true)
    List<CategoryJpaEntity> findActiveDescendantsForTree(@Param("rootCategoryId") UUID rootCategoryId);
}
