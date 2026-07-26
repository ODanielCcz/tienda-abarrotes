# Tienda de Abarrotes
Sistema modular para administrar una tienda de abarrotes.

## Stack

- Java 21
- Spring Boot
- Gradle Kotlin DSL
- PostgreSQL
- Flyway
- Docker Compose
- JWT
- Arquitectura hexagonal
- Clean Architecture

## Módulos actuales

- Autenticación y seguridad
- Usuarios, roles y permisos base
- Auditoría funcional
- Catálogo
  - Marcas
  - Categorías
  - Productos
  - Presentaciones
- Inventario
  - Stock
  - Lotes
  - Pallets
  - Movimientos
- Compras
  - Proveedores
  - Órdenes de compra
  - Recepción
- Ventas
  - Creación de venta
  - Descuento real de stock
  - FEFO para lotes
  - Cancelación y reposición de stock

## Ejecutar localmente

```powershell
docker compose up -d database backend
