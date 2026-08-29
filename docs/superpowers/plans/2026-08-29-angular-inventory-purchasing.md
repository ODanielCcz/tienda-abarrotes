# Plan hijo: Inventario y compras en Angular

## Objetivo

Construir la operación de inventario y compras sobre el contexto validado de sucursal y almacén, sin calcular saldos en el navegador y respetando permisos por acción.

## Entregas

1. Consultas de inventario:
   - `GET /inventory/stock?warehouseId`
   - `GET /inventory/lots?warehouseId`
   - `GET /inventory/pallets?warehouseId`
   - `GET /inventory/movements?warehouseId`
   - Modelos exactos: `StockBalanceView`, `LotView`, `PalletView`, `StockMovementView` y sus partidas.
   - Vista con pestañas, estados de carga/vacío/error y `correlationId`.
2. Recepciones:
   - recepción simple y con pallets;
   - UUID idempotente por intento lógico;
   - conservar captura si el backend rechaza la operación;
   - recargar stock, lotes, pallets y movimientos tras confirmar.
3. Proveedores y compras:
   - mantenimiento de proveedores;
   - compras `DRAFT → CONFIRMED → PARTIALLY_RECEIVED | RECEIVED`;
   - recepción de compra conectada al inventario.
4. Inventario avanzado:
   - ajustes, traspasos, conteos, reservas y caducidades;
   - cada acción detrás de su permiso;
   - recarga del servidor después de cada escritura.

## Reglas

- Todos los requests operativos toman `warehouseId` o `branchId` del `OperationalContextStore`.
- Angular nunca modifica saldos localmente.
- Las tablas muestran nombres/SKU entregados por el backend; cuando un lote solo contiene un ID, se correlaciona con el stock consultado.
- Los errores conservan formulario e incluyen referencia de correlación.
- No se reintenta automáticamente una escritura sensible con una nueva idempotency key.

## Validación por entrega

```powershell
cd "C:\Users\mine_\OneDrive\Documentos\ODCC\odcc\tienda-abarrotes\apps\web"
npm test -- --watch=false
npm run build
```

Commits previstos:

```text
feat(web): add inventory read views
feat(web): add inventory receipts
feat(web): add suppliers and purchases
feat(web): add advanced inventory operations
```
