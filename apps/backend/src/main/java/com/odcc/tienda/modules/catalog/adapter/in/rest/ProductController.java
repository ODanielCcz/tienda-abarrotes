package com.odcc.tienda.modules.catalog.adapter.in.rest;

import com.odcc.tienda.modules.catalog.adapter.in.rest.mapper.ProductPresentationRestMapper;
import com.odcc.tienda.modules.catalog.adapter.in.rest.mapper.ProductRestMapper;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.ChangeProductStatusRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.CreateProductPresentationRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.CreateProductRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.UpdateProductRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.ProductPresentationResponse;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.ProductResponse;
import com.odcc.tienda.modules.catalog.application.command.ChangeProductStatusCommand;
import com.odcc.tienda.modules.catalog.application.command.CreateProductCommand;
import com.odcc.tienda.modules.catalog.application.command.CreateProductPresentationCommand;
import com.odcc.tienda.modules.catalog.application.command.UpdateProductCommand;
import com.odcc.tienda.modules.catalog.application.port.in.ChangeProductStatusUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.CreateProductPresentationUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.CreateProductUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.GetProductByIdUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ListProductPresentationsUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ListProductsUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.UpdateProductUseCase;
import com.odcc.tienda.modules.catalog.application.query.ListProductsQuery;
import com.odcc.tienda.modules.catalog.application.query.ProductPage;
import com.odcc.tienda.modules.catalog.application.query.ProductSortField;
import com.odcc.tienda.modules.catalog.application.query.SortDirection;
import com.odcc.tienda.modules.catalog.domain.model.Product;
import com.odcc.tienda.modules.catalog.domain.model.ProductPresentation;
import com.odcc.tienda.modules.catalog.domain.model.ProductStatus;
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
@RequestMapping("/api/v1/catalog/products")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Administracion del catalogo de productos")
public class ProductController {

    private final CreateProductUseCase createProductUseCase;
    private final GetProductByIdUseCase getProductByIdUseCase;
    private final ListProductsUseCase listProductsUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final ChangeProductStatusUseCase changeProductStatusUseCase;
    private final CreateProductPresentationUseCase createProductPresentationUseCase;
    private final ListProductPresentationsUseCase listProductPresentationsUseCase;
    private final ProductRestMapper mapper;
    private final ProductPresentationRestMapper presentationMapper;

    @PostMapping
    @Operation(summary = "Crear un producto")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Producto creado"),
        @ApiResponse(responseCode = "400", description = "Solicitud o producto invalido"),
        @ApiResponse(responseCode = "401", description = "Token ausente o invalido"),
        @ApiResponse(responseCode = "403", description = "Permiso insuficiente"),
        @ApiResponse(responseCode = "404", description = "Categoria o marca no encontrada"),
        @ApiResponse(responseCode = "500", description = "Error interno seguro")
    })
    @PreAuthorize("hasAuthority('CATALOG_PRODUCT_CREATE')")
    public ResponseEntity<ApiResponseDto<ProductResponse>> create(
        @Valid @RequestBody CreateProductRequest request,
        HttpServletRequest servletRequest
    ) {
        CreateProductCommand command = mapper.toCommand(request);
        Product createdProduct = createProductUseCase.execute(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(
            HttpStatus.CREATED,
            "PRODUCT_CREATED",
            "Producto creado correctamente",
            mapper.toResponse(createdProduct),
            servletRequest.getRequestURI()
        ));
    }

    @PostMapping("/{productId}/presentations")
    @Operation(summary = "Crear una presentacion vendible para un producto")
    @PreAuthorize("hasAuthority('CATALOG_PRESENTATION_CREATE')")
    public ResponseEntity<ApiResponseDto<ProductPresentationResponse>> createPresentation(
        @PathVariable("productId") UUID productId,
        @Valid @RequestBody CreateProductPresentationRequest request,
        HttpServletRequest servletRequest
    ) {
        CreateProductPresentationCommand command = presentationMapper.toCreateCommand(productId, request);
        ProductPresentation createdPresentation = createProductPresentationUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(
            HttpStatus.CREATED,
            "PRODUCT_PRESENTATION_CREATED",
            "Presentacion creada correctamente",
            presentationMapper.toResponse(createdPresentation),
            servletRequest.getRequestURI()
        ));
    }

    @GetMapping("/{productId}/presentations")
    @Operation(summary = "Listar presentaciones de un producto")
    @PreAuthorize("hasAuthority('CATALOG_PRESENTATION_READ')")
    public ResponseEntity<ApiResponseDto<List<ProductPresentationResponse>>> listPresentations(
        @PathVariable("productId") UUID productId,
        HttpServletRequest servletRequest
    ) {
        List<ProductPresentationResponse> response = listProductPresentationsUseCase.execute(productId)
            .stream()
            .map(presentationMapper::toResponse)
            .toList();
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "PRODUCT_PRESENTATIONS_FOUND",
            "Presentaciones consultadas correctamente",
            response,
            servletRequest.getRequestURI()
        ));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Consultar un producto por id")
    @PreAuthorize("hasAuthority('CATALOG_PRODUCT_READ')")
    public ResponseEntity<ApiResponseDto<ProductResponse>> getById(
        @PathVariable("productId") UUID productId,
        HttpServletRequest servletRequest
    ) {
        Product product = getProductByIdUseCase.execute(productId);
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "PRODUCT_FOUND",
            "Producto encontrado correctamente",
            mapper.toResponse(product),
            servletRequest.getRequestURI()
        ));
    }

    @GetMapping
    @Operation(summary = "Listar productos con filtros y paginacion")
    @PreAuthorize("hasAuthority('CATALOG_PRODUCT_READ')")
    public ResponseEntity<ApiResponseDto<PageResponse<ProductResponse>>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) ProductStatus status,
        @RequestParam(required = false) UUID categoryId,
        @RequestParam(required = false) UUID brandId,
        @RequestParam(defaultValue = "NAME") ProductSortField sortBy,
        @RequestParam(defaultValue = "ASC") SortDirection direction,
        HttpServletRequest servletRequest
    ) {
        ProductPage result = listProductsUseCase.execute(new ListProductsQuery(
            page,
            size,
            search,
            status,
            categoryId,
            brandId,
            sortBy,
            direction
        ));

        List<ProductResponse> products = result.content().stream().map(mapper::toResponse).toList();
        PageResponse<ProductResponse> response = new PageResponse<>(
            products,
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages(),
            result.page() == 0,
            result.totalPages() == 0 || result.page() + 1 >= result.totalPages()
        );

        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "PRODUCTS_FOUND",
            "Productos consultados correctamente",
            response,
            servletRequest.getRequestURI()
        ));
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Actualizar un producto")
    @PreAuthorize("hasAuthority('CATALOG_PRODUCT_UPDATE')")
    public ResponseEntity<ApiResponseDto<ProductResponse>> update(
        @PathVariable("productId") UUID productId,
        @Valid @RequestBody UpdateProductRequest request,
        HttpServletRequest servletRequest
    ) {
        UpdateProductCommand command = mapper.toCommand(productId, request);
        Product updatedProduct = updateProductUseCase.execute(command);
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "PRODUCT_UPDATED",
            "Producto actualizado correctamente",
            mapper.toResponse(updatedProduct),
            servletRequest.getRequestURI()
        ));
    }

    @PatchMapping("/{productId}/status")
    @Operation(summary = "Cambiar estado de un producto")
    @PreAuthorize("hasAuthority('CATALOG_PRODUCT_STATUS')")
    public ResponseEntity<ApiResponseDto<ProductResponse>> changeStatus(
        @PathVariable("productId") UUID productId,
        @Valid @RequestBody ChangeProductStatusRequest request,
        HttpServletRequest servletRequest
    ) {
        Product updatedProduct = changeProductStatusUseCase.execute(new ChangeProductStatusCommand(productId, request.status()));
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "PRODUCT_STATUS_UPDATED",
            "Estado del producto actualizado correctamente",
            mapper.toResponse(updatedProduct),
            servletRequest.getRequestURI()
        ));
    }
}
