# ADR-0004: Arquitectura Hexagonal Modular Para El Backend

Date: 2026-07-12
Status: Accepted

## Context

El backend atenderá clientes web y móvil y cubrirá identidad, catálogo, inventario, compras, ventas, caja, facturación, auditoría y sincronización. Una separación global por capas produciría paquetes extensos y mezclaría contextos de negocio conforme crezca el sistema.

## Decision

Implementar el backend como un monolito modular Spring Boot. Cada módulo de negocio tendrá su propio dominio, casos de uso, puertos de entrada y salida, y adaptadores. La dirección permitida de dependencias será `adapter -> application -> domain`.

El dominio estará compuesto por Java puro. No dependerá de Spring, HTTP, JPA, PostgreSQL ni SDK externos. Los controladores REST serán adaptadores de entrada y la persistencia JPA será un adaptador de salida.

Se mantendrá un solo proyecto Gradle mientras los límites puedan protegerse mediante paquetes y pruebas arquitectónicas. La separación en módulos Gradle se evaluará únicamente si existe una necesidad técnica comprobada.

## Consequences

Los cambios de cada área quedarán localizados y los casos de uso podrán probarse con puertos falsos. Habrá más tipos y mapeos que en una estructura CRUD tradicional, pero se evitará acoplar el negocio al framework o a la base de datos.

Las carpetas internas no se crearán hasta implementar el primer caso de uso de cada módulo, evitando paquetes vacíos y abstracciones prematuras.
