package com.odcc.tienda.modules.catalog.adapter.out.persistence.entity;

import com.odcc.tienda.modules.catalog.domain.model.BrandStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    schema = "catalog",
    name = "brands"
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandJpaEntity {

    @Id
    @Column(
        name = "brand_id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Column(
        name = "code",
        nullable = false,
        unique = true,
        length = 50
    )
    private String code;

    @Column(
        name = "name",
        nullable = false,
        length = 150
    )
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 20
    )
    private BrandStatus status;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;
}
