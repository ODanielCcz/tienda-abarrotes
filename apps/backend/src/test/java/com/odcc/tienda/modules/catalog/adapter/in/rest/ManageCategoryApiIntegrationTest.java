package com.odcc.tienda.modules.catalog.adapter.in.rest;

import com.odcc.tienda.TestcontainersConfiguration;
import com.odcc.tienda.modules.catalog.application.port.out.CategoryRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@WithMockUser(authorities = {
    "CATALOG_CATEGORY_READ",
    "CATALOG_CATEGORY_CREATE",
    "CATALOG_CATEGORY_UPDATE",
    "CATALOG_CATEGORY_STATUS"
})
class ManageCategoryApiIntegrationTest {

    private static final String ENDPOINT = "/api/v1/catalog/categories";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepositoryPort categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateCategoryAndWriteBusinessAudit() throws Exception {
        mockMvc.perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "code": "api-category",
                          "name": "Categoria API"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("CATEGORY_CREATED"))
            .andExpect(jsonPath("$.data.code").value("API-CATEGORY"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        Integer persisted = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM catalog.categories WHERE code = ?",
            Integer.class,
            "API-CATEGORY"
        );

        Integer audited = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM audit.business_events
                WHERE event_type = 'CATEGORY_CREATED'
                  AND aggregate_type = 'CATEGORY'
                """,
            Integer.class
        );

        assertEquals(1, persisted);
        assertEquals(1, audited);
    }

    @Test
    void shouldListFilteredCategoriesFromPostgreSql() throws Exception {
        categoryRepository.save(Category.create("API-LIST-BEBIDAS", "Bebidas API", null));
        categoryRepository.save(Category.create("API-LIST-LIMPIEZA", "Limpieza API", null));
        categoryRepository.save(Category.create("API-LIST-BEBIDAS-INACTIVAS", "Bebidas inactivas API", null).changeStatus(CategoryStatus.INACTIVE));

        mockMvc.perform(
                get(ENDPOINT)
                    .queryParam("search", "bebidas")
                    .queryParam("status", "ACTIVE")
                    .queryParam("sortBy", "CODE")
                    .queryParam("direction", "ASC")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CATEGORIES_FOUND"))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].code").value("API-LIST-BEBIDAS"));
    }

    @Test
    void shouldUpdateCategoryAndWriteBusinessAudit() throws Exception {
        Category category = categoryRepository.save(Category.create("API-UPDATE-CAT-OLD", "Anterior API", null));
        Category parent = categoryRepository.save(Category.create("API-UPDATE-CAT-PARENT", "Padre API", null));

        mockMvc.perform(
                put(ENDPOINT + "/" + category.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "code": "API-UPDATE-CAT-NEW",
                          "name": "Actualizada API",
                          "parentCategoryId": "%s"
                        }
                        """.formatted(parent.getId()))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CATEGORY_UPDATED"))
            .andExpect(jsonPath("$.data.code").value("API-UPDATE-CAT-NEW"))
            .andExpect(jsonPath("$.data.parentCategoryId").value(parent.getId().toString()));

        Integer persisted = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM catalog.categories
                WHERE category_id = ? AND code = ? AND parent_category_id = ?
                """,
            Integer.class,
            category.getId(),
            "API-UPDATE-CAT-NEW",
            parent.getId()
        );

        Integer audited = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM audit.business_events
                WHERE aggregate_id = ? AND event_type = 'CATEGORY_UPDATED'
                """,
            Integer.class,
            category.getId()
        );

        assertEquals(1, persisted);
        assertEquals(1, audited);
    }

    @Test
    void shouldRejectIndirectCategoryCycle() throws Exception {
        Category root = categoryRepository.save(Category.create("API-CYCLE-ROOT", "Raiz API", null));
        Category child = categoryRepository.save(Category.create("API-CYCLE-CHILD", "Hija API", root.getId()));

        mockMvc.perform(
                put(ENDPOINT + "/" + root.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "code": "API-CYCLE-ROOT",
                          "name": "Raiz API",
                          "parentCategoryId": "%s"
                        }
                        """.formatted(child.getId()))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldDeactivateCategoryAndWriteBusinessAudit() throws Exception {
        Category category = categoryRepository.save(Category.create("API-STATUS-CAT", "Estado API", null));

        mockMvc.perform(
                patch(ENDPOINT + "/" + category.getId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"status": "INACTIVE"}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM catalog.categories WHERE category_id = ?",
            String.class,
            category.getId()
        );

        Integer audited = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM audit.business_events
                WHERE aggregate_id = ? AND event_type = 'CATEGORY_STATUS_CHANGED'
                """,
            Integer.class,
            category.getId()
        );

        assertEquals("INACTIVE", status);
        assertEquals(1, audited);
    }

    @Test
    void shouldPublishOpenApiContractForCategoryOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/catalog/categories'].get").exists())
            .andExpect(jsonPath("$.paths['/api/v1/catalog/categories'].post").exists())
            .andExpect(jsonPath("$.paths['/api/v1/catalog/categories'].post.responses['409']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/catalog/categories/{categoryId}'].put").exists())
            .andExpect(jsonPath("$.paths['/api/v1/catalog/categories/{categoryId}/status'].patch").exists());
    }
}

