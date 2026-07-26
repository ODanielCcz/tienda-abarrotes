package com.odcc.tienda.modules.catalog.domain.model;

import com.odcc.tienda.modules.catalog.domain.exception.InvalidBrandException;
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
public final class Brand {

    private static final int MAX_CODE_LENGTH = 50;
    private static final int MAX_NAME_LENGTH = 150;

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]+$");

    private final UUID id;
    private final String code;
    private final String name;
    private final BrandStatus status;
    private final Instant createdAt;

    private Brand(UUID id, String code, String name, BrandStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "El id de la marca es obligatorio");
        this.code = normalizeCode(code);
        this.name = normalizeName(name);
        this.status = Objects.requireNonNull(
            status,
            "El estado de la marca es obligatorio"
        );
        this.createdAt = Objects.requireNonNull(
            createdAt,
            "La fecha de creación es obligatoria"
        );
    }

    public static Brand create(String code, String name) {
        return new Brand(
            UUID.randomUUID(),
            code,
            name,
            BrandStatus.ACTIVE,
            Instant.now()
        );
    }

    public static Brand restore(UUID id, String code, String name, BrandStatus status,  Instant createdAt) {
        return new Brand(id, code, name, status, createdAt);
    }

    public Brand update(String code, String name) {
        return new Brand(
            id,
            code,
            name,
            status,
            createdAt
        );
    }

    public Brand changeStatus(BrandStatus newStatus) {
        Objects.requireNonNull(
            newStatus,
            "El nuevo estado de la marca es obligatorio"
        );

        if (status == newStatus) {
            return this;
        }

        return new Brand(
            id,
            code,
            name,
            newStatus,
            createdAt
        );
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidBrandException(
                "El código de la marca es obligatorio"
            );
        }

        String normalizedCode = code
            .trim()
            .toUpperCase(Locale.ROOT);

        if (normalizedCode.length() > MAX_CODE_LENGTH) {
            throw new InvalidBrandException(
                "El código de la marca no puede superar 50 caracteres"
            );
        }

        if (!CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new InvalidBrandException(
                "El código solo puede contener letras, números, guiones y guiones bajos"
            );
        }

        return normalizedCode;
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidBrandException(
                "El nombre de la marca es obligatorio"
            );
        }

        String normalizedName = name.trim();

        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new InvalidBrandException(
                "El nombre de la marca no puede superar 150 caracteres"
            );
        }

        return normalizedName;
    }
}
