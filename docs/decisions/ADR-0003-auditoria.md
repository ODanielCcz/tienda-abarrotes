# ADR-0003: Auditoría Funcional Y Técnica En Capas

Date: 2026-07-12
Status: Accepted

## Context

Las tablas de negocio no detectan cuentas PostgreSQL o privilegios creados fuera de la aplicación, y los event triggers no cubren roles compartidos del clúster.

## Decision

Combinar tablas append-only, inventario de cuentas PostgreSQL, vistas de conciliación, logs nativos y pgAudit 18.0 para clases ROLE y DDL.

## Consequences

Existe trazabilidad funcional y administrativa. Las bitácoras no guardan secretos; su volumen y retención deben vigilarse.

