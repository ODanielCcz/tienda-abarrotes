# ADR-0005: Las transacciones de escritura pertenecen al caso de uso

## Estado

Aceptada.

## Contexto

El backend usa arquitectura hexagonal. Los casos de uso coordinan reglas de negocio, auditoría y persistencia mediante puertos. Si cada adaptador de persistencia abre su propia transacción de escritura, los flujos compuestos futuros —por ejemplo venta, detalle, descuento de stock y auditoría— pueden quedar partidos en unidades de trabajo distintas o difíciles de razonar.

## Decisión

Las transacciones de escritura se definen desde la capa de aplicación usando `TransactionRunner`.

Los adaptadores de persistencia deben unirse a la transacción activa. Pueden mantener transacciones `readOnly` para consultas si ayudan al comportamiento de JPA, pero no deben declarar una frontera transaccional propia para comandos de escritura salvo que exista una justificación explícita.

## Consecuencias

- Un caso de uso controla qué operaciones deben confirmar o revertirse juntas.
- La auditoría funcional queda dentro de la misma unidad de trabajo que el cambio sensible.
- Los módulos de inventario y ventas podrán implementar reglas concurrentes sin repartir la consistencia entre repositorios.
- Las pruebas de aplicación pueden sustituir `TransactionRunner` por implementaciones inmediatas o controladas.
