package com.odcc.tienda.modules.catalog.adapter.in.rest;

import com.odcc.tienda.modules.catalog.adapter.in.rest.mapper.CategoryRestMapper;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.ChangeCategoryStatusRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.CreateCategoryRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.UpdateCategoryRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.CategoryResponse;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.CategoryTreeResponse;
import com.odcc.tienda.modules.catalog.application.command.ChangeCategoryStatusCommand;
import com.odcc.tienda.modules.catalog.application.command.CreateCategoryCommand;
import com.odcc.tienda.modules.catalog.application.command.UpdateCategoryCommand;
import com.odcc.tienda.modules.catalog.application.model.CategoryTreeNode;
import com.odcc.tienda.modules.catalog.application.port.in.ChangeCategoryStatusUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.CreateCategoryUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.GetCategoryByIdUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.GetCategoryTreeByParentUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.GetCategoryTreeUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ListCategoriesUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.UpdateCategoryUseCase;
import com.odcc.tienda.modules.catalog.application.query.CategoryPage;
import com.odcc.tienda.modules.catalog.application.query.CategorySortField;
import com.odcc.tienda.modules.catalog.application.query.CategoryTreeQuery;
import com.odcc.tienda.modules.catalog.application.query.ListCategoriesQuery;
import com.odcc.tienda.modules.catalog.application.query.SortDirection;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import com.odcc.tienda.shared.web.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/categories")
@RequiredArgsConstructor
@Tag(name = "Categorias", description = "Administracion del catalogo de categorias")
public class CategoryController {

    private final CreateCategoryUseCase createCategoryUseCase;
    private final GetCategoryByIdUseCase getCategoryByIdUseCase;
    private final ListCategoriesUseCase listCategoriesUseCase;
    private final GetCategoryTreeUseCase getCategoryTreeUseCase;
    private final GetCategoryTreeByParentUseCase getCategoryTreeByParentUseCase;
    private final UpdateCategoryUseCase updateCategoryUseCase;
    private final ChangeCategoryStatusUseCase changeCategoryStatusUseCase;
    private final CategoryRestMapper mapper;

    @PostMapping
    @Operation(summary = "Crear una categoria")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Categoria creada"),
        @ApiResponse(responseCode = "400", description = "Solicitud o categoria invalida"),
        @ApiResponse(responseCode = "401", description = "Token ausente o invalido"),
        @ApiResponse(responseCode = "403", description = "Permiso insuficiente"),
        @ApiResponse(responseCode = "404", description = "Categoria padre no encontrada"),
        @ApiResponse(responseCode = "409", description = "Codigo de categoria duplicado"),
        @ApiResponse(responseCode = "500", description = "Error interno seguro")
    })
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_CREATE')")
    public ResponseEntity<ApiResponseDto<CategoryResponse>> create(
        @Valid @RequestBody CreateCategoryRequest request,
        HttpServletRequest servletRequest
    ) {
        CreateCategoryCommand command = mapper.toCommand(request);
        Category createdCategory = createCategoryUseCase.execute(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(
            HttpStatus.CREATED,
            "CATEGORY_CREATED",
            "Categoria creada correctamente",
            mapper.toResponse(createdCategory),
            servletRequest.getRequestURI()
        ));
    }

    @GetMapping("/tree")
    @Operation(summary = "Consultar arbol completo de categorias")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Arbol consultado"),
        @ApiResponse(responseCode = "401", description = "Token ausente o invalido"),
        @ApiResponse(responseCode = "403", description = "Permiso insuficiente"),
        @ApiResponse(responseCode = "500", description = "Error interno seguro")
    })
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_READ')")
    public ResponseEntity<ApiResponseDto<List<CategoryTreeResponse>>> tree(
        @RequestParam(required = false) CategoryStatus status,
        HttpServletRequest servletRequest
    ) {
        List<CategoryTreeNode> result = getCategoryTreeUseCase.execute(new CategoryTreeQuery(status));
        List<CategoryTreeResponse> response = result.stream().map(mapper::toResponse).toList();

        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "CATEGORY_TREE_FOUND",
            "Arbol de categorias consultado correctamente",
            response,
            servletRequest.getRequestURI()
        ));
    }

    @GetMapping("/{categoryId}/tree")
    @Operation(summary = "Consultar arbol desde una categoria padre")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Arbol de categoria consultado"),
        @ApiResponse(responseCode = "401", description = "Token ausente o invalido"),
        @ApiResponse(responseCode = "403", description = "Permiso insuficiente"),
        @ApiResponse(responseCode = "404", description = "Categoria no encontrada"),
        @ApiResponse(responseCode = "500", description = "Error interno seguro")
    })
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_READ')")
    public ResponseEntity<ApiResponseDto<CategoryTreeResponse>> treeByParent(
        @PathVariable("categoryId") UUID categoryId,
        @RequestParam(required = false) CategoryStatus status,
        HttpServletRequest servletRequest
    ) {
        CategoryTreeNode result = getCategoryTreeByParentUseCase.execute(
            categoryId,
            new CategoryTreeQuery(status)
        );

        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "CATEGORY_TREE_FOUND",
            "Arbol de categoria consultado correctamente",
            mapper.toResponse(result),
            servletRequest.getRequestURI()
        ));
    }

    @GetMapping("/{categoryId}")
    @Operation(summary = "Consultar una categoria por id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Categoria encontrada"),
        @ApiResponse(responseCode = "400", description = "Identificador invalido"),
        @ApiResponse(responseCode = "401", description = "Token ausente o invalido"),
        @ApiResponse(responseCode = "403", description = "Permiso insuficiente"),
        @ApiResponse(responseCode = "404", description = "Categoria no encontrada"),
        @ApiResponse(responseCode = "500", description = "Error interno seguro")
    })
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_READ')")
    public ResponseEntity<ApiResponseDto<CategoryResponse>> getById(
        @PathVariable("categoryId") UUID categoryId,
        HttpServletRequest servletRequest
    ) {
        Category category = getCategoryByIdUseCase.execute(categoryId);
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "CATEGORY_FOUND",
            "Categoria encontrada correctamente",
            mapper.toResponse(category),
            servletRequest.getRequestURI()
        ));
    }

    @GetMapping
    @Operation(summary = "Listar categorias con filtros y paginacion")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado paginado"),
        @ApiResponse(responseCode = "400", description = "Filtro o paginacion invalidos"),
        @ApiResponse(responseCode = "401", description = "Token ausente o invalido"),
        @ApiResponse(responseCode = "403", description = "Permiso insuficiente"),
        @ApiResponse(responseCode = "500", description = "Error interno seguro")
    })
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_READ')")
    public ResponseEntity<ApiResponseDto<PageResponse<CategoryResponse>>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) CategoryStatus status,
        @RequestParam(defaultValue = "NAME") CategorySortField sortBy,
        @RequestParam(defaultValue = "ASC") SortDirection direction,
        HttpServletRequest servletRequest
    ) {
        CategoryPage result = listCategoriesUseCase.execute(new ListCategoriesQuery(
            page,
            size,
            search,
            status,
            sortBy,
            direction
        ));

        List<CategoryResponse> categories = result.content().stream().map(mapper::toResponse).toList();
        PageResponse<CategoryResponse> response = new PageResponse<>(
            categories,
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages(),
            result.page() == 0,
            result.totalPages() == 0 || result.page() + 1 >= result.totalPages()
        );

        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "CATEGORIES_FOUND",
            "Categorias consultadas correctamente",
            response,
            servletRequest.getRequestURI()
        ));
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "Actualizar codigo, nombre y categoria padre")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Categoria actualizada"),
        @ApiResponse(responseCode = "400", description = "Solicitud o categoria invalida"),
        @ApiResponse(responseCode = "401", description = "Token ausente o invalido"),
        @ApiResponse(responseCode = "403", description = "Permiso insuficiente"),
        @ApiResponse(responseCode = "404", description = "Categoria no encontrada"),
        @ApiResponse(responseCode = "409", description = "Codigo de categoria duplicado"),
        @ApiResponse(responseCode = "500", description = "Error interno seguro")
    })
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_UPDATE')")
    public ResponseEntity<ApiResponseDto<CategoryResponse>> update(
        @PathVariable("categoryId") UUID categoryId,
        @Valid @RequestBody UpdateCategoryRequest request,
        HttpServletRequest servletRequest
    ) {
        UpdateCategoryCommand command = mapper.toCommand(categoryId, request);
        Category updatedCategory = updateCategoryUseCase.execute(command);
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "CATEGORY_UPDATED",
            "Categoria actualizada correctamente",
            mapper.toResponse(updatedCategory),
            servletRequest.getRequestURI()
        ));
    }

    @PatchMapping("/{categoryId}/status")
    @Operation(summary = "Activar o desactivar una categoria")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado actualizado"),
        @ApiResponse(responseCode = "400", description = "Estado invalido"),
        @ApiResponse(responseCode = "401", description = "Token ausente o invalido"),
        @ApiResponse(responseCode = "403", description = "Permiso insuficiente"),
        @ApiResponse(responseCode = "404", description = "Categoria no encontrada"),
        @ApiResponse(responseCode = "500", description = "Error interno seguro")
    })
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_STATUS')")
    public ResponseEntity<ApiResponseDto<CategoryResponse>> changeStatus(
        @PathVariable("categoryId") UUID categoryId,
        @Valid @RequestBody ChangeCategoryStatusRequest request,
        HttpServletRequest servletRequest
    ) {
        Category updatedCategory = changeCategoryStatusUseCase.execute(new ChangeCategoryStatusCommand(
            categoryId,
            request.status()
        ));
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "CATEGORY_STATUS_UPDATED",
            "Estado de la categoria actualizado correctamente",
            mapper.toResponse(updatedCategory),
            servletRequest.getRequestURI()
        ));
    }
}
