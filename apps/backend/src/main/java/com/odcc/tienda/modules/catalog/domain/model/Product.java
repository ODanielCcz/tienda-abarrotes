package com.odcc.tienda.modules.catalog.domain.model;

import com.odcc.tienda.modules.catalog.domain.exception.InvalidProductException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@ToString
@EqualsAndHashCode(of = "id")
public final class Product {

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;

    private final UUID id;
    private final UUID categoryId;
    private final UUID brandId;
    private final String name;
    private final String description;
    private final ProductType productType;
    private final boolean tracksInventory;
    private final boolean tracksLots;
    private final boolean tracksExpiration;
    private final ProductStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Product(
        UUID id,
        UUID categoryId,
        UUID brandId,
        String name,
        String description,
        ProductType productType,
        boolean tracksInventory,
        boolean tracksLots,
        boolean tracksExpiration,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "El id del producto es obligatorio");
        this.categoryId = categoryId;
        this.brandId = brandId;
        this.name = normalizeName(name);
        this.description = normalizeDescription(description);
        this.productType = Objects.requireNonNull(productType, "El tipo de producto es obligatorio");
        this.tracksInventory = tracksInventory;
        this.tracksLots = tracksLots;
        this.tracksExpiration = tracksExpiration;
        this.status = Objects.requireNonNull(status, "El estado del producto es obligatorio");
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creacion es obligatoria");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualizacion es obligatoria");

        validateTrackingRules();
    }

    public static Product create(
        UUID categoryId,
        UUID brandId,
        String name,
        String description,
        ProductType productType,
        boolean tracksInventory,
        boolean tracksLots,
        boolean tracksExpiration
    ) {
        Instant now = Instant.now();
        return new Product(
            UUID.randomUUID(),
            categoryId,
            brandId,
            name,
            description,
            productType == null ? ProductType.GOODS : productType,
            tracksInventory,
            tracksLots,
            tracksExpiration,
            ProductStatus.ACTIVE,
            now,
            now
        );
    }

    public static Product restore(
        UUID id,
        UUID categoryId,
        UUID brandId,
        String name,
        String description,
        ProductType productType,
        boolean tracksInventory,
        boolean tracksLots,
        boolean tracksExpiration,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        return new Product(
            id,
            categoryId,
            brandId,
            name,
            description,
            productType,
            tracksInventory,
            tracksLots,
            tracksExpiration,
            status,
            createdAt,
            updatedAt
        );
    }

    public Product update(
        UUID categoryId,
        UUID brandId,
        String name,
        String description,
        ProductType productType,
        boolean tracksInventory,
        boolean tracksLots,
        boolean tracksExpiration
    ) {
        return new Product(
            id,
            categoryId,
            brandId,
            name,
            description,
            productType == null ? this.productType : productType,
            tracksInventory,
            tracksLots,
            tracksExpiration,
            status,
            createdAt,
            Instant.now()
        );
    }

    public Product changeStatus(ProductStatus newStatus) {
        Objects.requireNonNull(newStatus, "El nuevo estado del producto es obligatorio");
        if (status == newStatus) {
            return this;
        }
        return new Product(
            id,
            categoryId,
            brandId,
            name,
            description,
            productType,
            tracksInventory,
            tracksLots,
            tracksExpiration,
            newStatus,
            createdAt,
            Instant.now()
        );
    }

    private void validateTrackingRules() {
        if (tracksExpiration && !tracksLots) {
            throw new InvalidProductException("Un producto con caducidad debe controlar lotes");
        }
        if (productType == ProductType.SERVICE && (tracksInventory || tracksLots || tracksExpiration)) {
            throw new InvalidProductException("Un servicio no puede controlar inventario, lotes o caducidad");
        }
        if (!tracksInventory && (tracksLots || tracksExpiration)) {
            throw new InvalidProductException("Un producto sin inventario no puede controlar lotes o caducidad");
        }
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidProductException("El nombre del producto es obligatorio");
        }
        String normalizedName = name.trim();
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new InvalidProductException("El nombre del producto no puede superar 200 caracteres");
        }
        return normalizedName;
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String normalizedDescription = description.trim();
        if (normalizedDescription.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidProductException("La descripcion del producto no puede superar 1000 caracteres");
        }
        return normalizedDescription;
    }
}
