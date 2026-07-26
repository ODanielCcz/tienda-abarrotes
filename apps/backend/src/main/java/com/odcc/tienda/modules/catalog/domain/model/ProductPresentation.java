package com.odcc.tienda.modules.catalog.domain.model;

import com.odcc.tienda.modules.catalog.domain.exception.InvalidProductException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Getter
@ToString
@EqualsAndHashCode(of = "id")
public final class ProductPresentation {

    private static final int MAX_SKU_LENGTH = 80;
    private static final int MAX_NAME_LENGTH = 200;
    private static final Pattern SKU_PATTERN = Pattern.compile("^[A-Z0-9_-]+$");

    private final UUID id;
    private final UUID productId;
    private final UUID unitId;
    private final UUID taxId;
    private final String sku;
    private final String name;
    private final BigDecimal conversionFactor;
    private final BigDecimal netContent;
    private final BigDecimal minimumStock;
    private final ProductPresentationStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ProductPresentation(
        UUID id,
        UUID productId,
        UUID unitId,
        UUID taxId,
        String sku,
        String name,
        BigDecimal conversionFactor,
        BigDecimal netContent,
        BigDecimal minimumStock,
        ProductPresentationStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "El id de la presentacion es obligatorio");
        this.productId = Objects.requireNonNull(productId, "El producto es obligatorio");
        this.unitId = Objects.requireNonNull(unitId, "La unidad de medida es obligatoria");
        this.taxId = taxId;
        this.sku = normalizeSku(sku);
        this.name = normalizeName(name);
        this.conversionFactor = positive(conversionFactor == null ? BigDecimal.ONE : conversionFactor, "El factor de conversion debe ser mayor a cero");
        this.netContent = netContent == null ? null : positive(netContent, "El contenido neto debe ser mayor a cero");
        this.minimumStock = nonNegative(minimumStock == null ? BigDecimal.ZERO : minimumStock, "El stock minimo no puede ser negativo");
        this.status = Objects.requireNonNull(status, "El estado de la presentacion es obligatorio");
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creacion es obligatoria");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualizacion es obligatoria");
    }

    public static ProductPresentation create(UUID productId, UUID unitId, UUID taxId, String sku, String name, BigDecimal conversionFactor, BigDecimal netContent, BigDecimal minimumStock) {
        Instant now = Instant.now();
        return new ProductPresentation(UUID.randomUUID(), productId, unitId, taxId, sku, name, conversionFactor, netContent, minimumStock, ProductPresentationStatus.ACTIVE, now, now);
    }

    public static ProductPresentation restore(UUID id, UUID productId, UUID unitId, UUID taxId, String sku, String name, BigDecimal conversionFactor, BigDecimal netContent, BigDecimal minimumStock, ProductPresentationStatus status, Instant createdAt, Instant updatedAt) {
        return new ProductPresentation(id, productId, unitId, taxId, sku, name, conversionFactor, netContent, minimumStock, status, createdAt, updatedAt);
    }

    public ProductPresentation update(UUID unitId, UUID taxId, String sku, String name, BigDecimal conversionFactor, BigDecimal netContent, BigDecimal minimumStock) {
        return new ProductPresentation(id, productId, unitId, taxId, sku, name, conversionFactor, netContent, minimumStock, status, createdAt, Instant.now());
    }

    public ProductPresentation changeStatus(ProductPresentationStatus newStatus) {
        return new ProductPresentation(id, productId, unitId, taxId, sku, name, conversionFactor, netContent, minimumStock, newStatus, createdAt, Instant.now());
    }

    private static String normalizeSku(String sku) {
        if (sku == null || sku.isBlank()) throw new InvalidProductException("El SKU de la presentacion es obligatorio");
        String normalized = sku.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_SKU_LENGTH) throw new InvalidProductException("El SKU no puede superar 80 caracteres");
        if (!SKU_PATTERN.matcher(normalized).matches()) throw new InvalidProductException("El SKU solo puede contener letras, numeros, guiones y guiones bajos");
        return normalized;
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) throw new InvalidProductException("El nombre de la presentacion es obligatorio");
        String normalized = name.trim();
        if (normalized.length() > MAX_NAME_LENGTH) throw new InvalidProductException("El nombre de la presentacion no puede superar 200 caracteres");
        return normalized;
    }

    private static BigDecimal positive(BigDecimal value, String message) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) throw new InvalidProductException(message);
        return value;
    }

    private static BigDecimal nonNegative(BigDecimal value, String message) {
        if (value.compareTo(BigDecimal.ZERO) < 0) throw new InvalidProductException(message);
        return value;
    }
}
