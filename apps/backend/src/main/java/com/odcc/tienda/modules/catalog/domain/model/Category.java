package com.odcc.tienda.modules.catalog.domain.model;

import com.odcc.tienda.modules.catalog.domain.exception.InvalidCategoryException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Getter
@ToString
@EqualsAndHashCode(of = "id")
public final class Category {

    private static final int MAX_CODE_LENGTH = 50;
    private static final int MAX_NAME_LENGTH = 150;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]+$");

    private final UUID id;
    private final UUID parentCategoryId;
    private final String code;
    private final String name;
    private final CategoryStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Category(
        UUID id,
        UUID parentCategoryId,
        String code,
        String name,
        CategoryStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "El id de la categoria es obligatorio");
        this.parentCategoryId = parentCategoryId;
        this.code = normalizeCode(code);
        this.name = normalizeName(name);
        this.status = Objects.requireNonNull(status, "El estado de la categoria es obligatorio");
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creacion es obligatoria");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualizacion es obligatoria");

        if (id.equals(parentCategoryId)) {
            throw new InvalidCategoryException("Una categoria no puede ser padre de si misma");
        }
    }

    public static Category create(String code, String name, UUID parentCategoryId) {
        Instant now = Instant.now();
        return new Category(UUID.randomUUID(), parentCategoryId, code, name, CategoryStatus.ACTIVE, now, now);
    }

    public static Category restore(
        UUID id,
        UUID parentCategoryId,
        String code,
        String name,
        CategoryStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        return new Category(id, parentCategoryId, code, name, status, createdAt, updatedAt);
    }

    public Category update(String code, String name, UUID parentCategoryId) {
        return new Category(id, parentCategoryId, code, name, status, createdAt, Instant.now());
    }

    public Category changeStatus(CategoryStatus newStatus) {
        Objects.requireNonNull(newStatus, "El nuevo estado de la categoria es obligatorio");
        if (status == newStatus) {
            return this;
        }
        return new Category(id, parentCategoryId, code, name, newStatus, createdAt, Instant.now());
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidCategoryException("El codigo de la categoria es obligatorio");
        }
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        if (normalizedCode.length() > MAX_CODE_LENGTH) {
            throw new InvalidCategoryException("El codigo de la categoria no puede superar 50 caracteres");
        }
        if (!CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new InvalidCategoryException("El codigo solo puede contener letras, numeros, guiones y guiones bajos");
        }
        return normalizedCode;
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidCategoryException("El nombre de la categoria es obligatorio");
        }
        String normalizedName = name.trim();
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new InvalidCategoryException("El nombre de la categoria no puede superar 150 caracteres");
        }
        return normalizedName;
    }
}