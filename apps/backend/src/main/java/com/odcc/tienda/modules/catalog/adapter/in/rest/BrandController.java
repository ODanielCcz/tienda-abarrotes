package com.odcc.tienda.modules.catalog.adapter.in.rest;

import com.odcc.tienda.modules.catalog.adapter.in.rest.mapper.BrandRestMapper;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.CreateBrandRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.UpdateBrandRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.request.ChangeBrandStatusRequest;
import com.odcc.tienda.modules.catalog.adapter.in.rest.response.BrandResponse;
import com.odcc.tienda.modules.catalog.application.command.CreateBrandCommand;
import com.odcc.tienda.modules.catalog.application.command.UpdateBrandCommand;
import com.odcc.tienda.modules.catalog.application.port.in.CreateBrandUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.GetBrandByIdUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ListBrandsUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.UpdateBrandUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ChangeBrandStatusUseCase;
import com.odcc.tienda.modules.catalog.application.query.BrandPage;
import com.odcc.tienda.modules.catalog.application.query.BrandSortField;
import com.odcc.tienda.modules.catalog.application.query.ListBrandsQuery;
import com.odcc.tienda.modules.catalog.application.query.SortDirection;
import com.odcc.tienda.modules.catalog.domain.model.BrandStatus;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
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

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog/brands")
@RequiredArgsConstructor
@Tag(name = "Marcas", description = "Administración del catálogo de marcas")
public class BrandController {

    private final CreateBrandUseCase createBrandUseCase;
    private final GetBrandByIdUseCase getBrandByIdUseCase;
    private final ListBrandsUseCase listBrandsUseCase;
    private final UpdateBrandUseCase updateBrandUseCase;
    private final ChangeBrandStatusUseCase changeBrandStatusUseCase;
    private final BrandRestMapper mapper;

    @PostMapping
    @Operation(summary = "Crear una marca")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Marca creada"),
        @ApiResponse(responseCode = "400", description = "Solicitud o marca inválida"),
        @ApiResponse(responseCode = "401", description = "Token ausente o inválido"),
        @ApiResponse(responseCode = "403", description = "Permiso insuficiente"),
        @ApiResponse(responseCode = "409", description = "Código de marca duplicado"),
        @ApiResponse(responseCode = "500", description = "Error interno seguro")
    })
    @PreAuthorize("hasAuthority('CATALOG_BRAND_CREATE')")
    public ResponseEntity<ApiResponseDto<BrandResponse>> create(
        @Valid @RequestBody CreateBrandRequest request,
        HttpServletRequest servletRequest
    ) {
        CreateBrandCommand command = mapper.toCommand(request);

        Brand createdBrand = createBrandUseCase.execute(command);

        BrandResponse response = mapper.toResponse(createdBrand);

        ApiResponseDto<BrandResponse> apiResponse =
            ApiResponseDto.success(
                HttpStatus.CREATED,
                "BRAND_CREATED",
                "Marca creada correctamente",
                response,
                servletRequest.getRequestURI()
            );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(apiResponse);
    }

    @GetMapping("/{brandId}")
    @Operation(summary = "Consultar una marca por id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Marca encontrada"),
        @ApiResponse(responseCode = "400", description = "Identificador inválido"),
        @ApiResponse(responseCode = "401", description = "Token ausente o inválido"),
        @ApiResponse(responseCode = "403", description = "Permiso insuficiente"),
        @ApiResponse(responseCode = "404", description = "Marca no encontrada"),
        @ApiResponse(responseCode = "500", description = "Error interno seguro")
    })
    @PreAuthorize("hasAuthority('CATALOG_BRAND_READ')")
    public ResponseEntity<ApiResponseDto<BrandResponse>> getById(
        @PathVariable("brandId") UUID brandId,
        HttpServletRequest servletRequest
    ) {
        Brand brand = getBrandByIdUseCase.execute(brandId);

        BrandResponse response = mapper.toResponse(brand);

        ApiResponseDto<BrandResponse> apiResponse =
            ApiResponseDto.success(
                HttpStatus.OK,
                "BRAND_FOUND",
                "Marca encontrada correctamente",
                response,
                servletRequest.getRequestURI()
            );

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping
    @Operation(summary = "Listar marcas con filtros y paginación")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado paginado"),
        @ApiResponse(responseCode = "400", description = "Filtro o paginación inválidos"),
        @ApiResponse(responseCode = "401", description = "Token ausente o inválido"),
        @ApiResponse(responseCode = "403", description = "Permiso insuficiente"),
        @ApiResponse(responseCode = "500", description = "Error interno seguro")
    })
    @PreAuthorize("hasAuthority('CATALOG_BRAND_READ')")
    public ResponseEntity<ApiResponseDto<PageResponse<BrandResponse>>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) BrandStatus status,
        @RequestParam(defaultValue = "NAME") BrandSortField sortBy,
        @RequestParam(defaultValue = "ASC") SortDirection direction,
        HttpServletRequest servletRequest
    ) {
        ListBrandsQuery query = new ListBrandsQuery(
            page,
            size,
            search,
            status,
            sortBy,
            direction
        );

        BrandPage result = listBrandsUseCase.execute(query);

        List<BrandResponse> brands = result
            .content()
            .stream()
            .map(mapper::toResponse)
            .toList();

        PageResponse<BrandResponse> response = new PageResponse<>(
            brands,
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages(),
            result.page() == 0,
            result.totalPages() == 0 || result.page() + 1 >= result.totalPages()
        );

        return ResponseEntity.ok(
            ApiResponseDto.success(
                HttpStatus.OK,
                "BRANDS_FOUND",
                "Marcas consultadas correctamente",
                response,
                servletRequest.getRequestURI()
            )
        );
    }

    @PutMapping("/{brandId}")
    @Operation(summary = "Actualizar el código y nombre de una marca")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Marca actualizada"),
        @ApiResponse(responseCode = "400", description = "Solicitud o marca inválida"),
        @ApiResponse(responseCode = "401", description = "Token ausente o inválido"),
        @ApiResponse(responseCode = "403", description = "Permiso insuficiente"),
        @ApiResponse(responseCode = "404", description = "Marca no encontrada"),
        @ApiResponse(responseCode = "409", description = "Código de marca duplicado"),
        @ApiResponse(responseCode = "500", description = "Error interno seguro")
    })
    @PreAuthorize("hasAuthority('CATALOG_BRAND_UPDATE')")
    public ResponseEntity<ApiResponseDto<BrandResponse>> update(
        @PathVariable("brandId") UUID brandId,
        @Valid @RequestBody UpdateBrandRequest request,
        HttpServletRequest servletRequest
    ) {
        UpdateBrandCommand command = mapper.toCommand(brandId, request);
        Brand updatedBrand = updateBrandUseCase.execute(command);

        return ResponseEntity.ok(
            ApiResponseDto.success(
                HttpStatus.OK,
                "BRAND_UPDATED",
                "Marca actualizada correctamente",
                mapper.toResponse(updatedBrand),
                servletRequest.getRequestURI()
            )
        );
    }

    @PatchMapping("/{brandId}/status")
    @Operation(summary = "Activar o desactivar una marca")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado actualizado"),
        @ApiResponse(responseCode = "400", description = "Estado inválido"),
        @ApiResponse(responseCode = "401", description = "Token ausente o inválido"),
        @ApiResponse(responseCode = "403", description = "Permiso insuficiente"),
        @ApiResponse(responseCode = "404", description = "Marca no encontrada"),
        @ApiResponse(responseCode = "500", description = "Error interno seguro")
    })
    @PreAuthorize("hasAuthority('CATALOG_BRAND_STATUS')")
    public ResponseEntity<ApiResponseDto<BrandResponse>> changeStatus(
        @PathVariable("brandId") UUID brandId,
        @Valid @RequestBody ChangeBrandStatusRequest request,
        HttpServletRequest servletRequest
    ) {
        Brand updatedBrand = changeBrandStatusUseCase.execute(mapper.toStatusCommand(brandId, request));

        return ResponseEntity.ok(
            ApiResponseDto.success(
                HttpStatus.OK,
                "BRAND_STATUS_UPDATED",
                "Estado de la marca actualizado correctamente",
                mapper.toResponse(updatedBrand),
                servletRequest.getRequestURI()
            )
        );
    }
}
