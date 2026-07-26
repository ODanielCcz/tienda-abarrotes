package com.odcc.tienda.modules.catalog.adapter.in.rest.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductPresentationRequest(
    @NotNull(message = "La unidad de medida es obligatoria") UUID unitId,
    UUID taxId,
    @NotBlank(message = "El SKU es obligatorio") @Size(max = 80) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String sku,
    @NotBlank(message = "El nombre es obligatorio") @Size(max = 200) String name,
    @DecimalMin(value = "0.000001", message = "El factor de conversion debe ser mayor a cero") BigDecimal conversionFactor,
    @DecimalMin(value = "0.000001", message = "El contenido neto debe ser mayor a cero") BigDecimal netContent,
    @DecimalMin(value = "0.000", message = "El stock minimo no puede ser negativo") BigDecimal minimumStock
) {
}
