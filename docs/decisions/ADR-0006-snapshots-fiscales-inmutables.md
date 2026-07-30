# ADR-0006: Snapshots Fiscales Inmutables

## Estado

Aceptada.

## Contexto

Los datos del emisor, receptor, productos, unidades, impuestos y precios pueden cambiar después de una venta. Un documento fiscal histórico no debe cambiar al actualizar el catálogo o un perfil fiscal.

## Decisión

Los documentos fiscales internos se crean a partir de una venta pagada y congelan los datos fiscales en `billing.fiscal_documents` y `billing.fiscal_document_items`.

- El documento nace en `DRAFT`.
- El cambio a `READY` es explícito.
- Un documento `READY` es inmutable.
- Los códigos SAT se obtienen del catálogo vigente al crear el documento.
- Solo puede existir un documento `INCOME` vigente por venta.
- En el MVP no se genera XML, no se timbra y no se conecta un PAC.

## Consecuencias

- Los cambios posteriores en perfiles y catálogo no alteran documentos históricos.
- Corregir un documento listo requerirá en una versión posterior un flujo fiscal de cancelación o sustitución.
- La venta, los perfiles y la clasificación SAT deben estar completos antes de preparar el documento.
