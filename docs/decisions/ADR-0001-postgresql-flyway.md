# ADR-0001: PostgreSQL Y Flyway Como Plataforma De Datos

Date: 2026-07-12
Status: Accepted

## Context

El sistema necesita integridad transaccional, concurrencia de inventario, múltiples clientes y evolución controlada del esquema.

## Decision

Usar PostgreSQL 18.4 y mantener `database/migrations` como fuente canónica aplicada con Flyway 12.11.0. Las aplicaciones no crearán ni actualizarán tablas automáticamente.

## Consequences

Los cambios serán reproducibles y auditables. Cada evolución exige una migración nueva; las migraciones aplicadas no se modifican.

