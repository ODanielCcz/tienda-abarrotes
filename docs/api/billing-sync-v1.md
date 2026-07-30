# Facturación Y Sync Offline V1

Todos los endpoints usan `ApiResponseDto`, JWT Bearer, `reason` y `correlationId`.

## Matriz De Endpoints Y Permisos

| Método | Ruta | Permiso |
|---|---|---|
| POST | `/api/v1/billing/issuer-profiles` | `BILLING_ISSUER_PROFILE_CREATE` |
| GET | `/api/v1/billing/issuer-profiles` | `BILLING_ISSUER_PROFILE_READ` |
| GET | `/api/v1/billing/issuer-profiles/{id}` | `BILLING_ISSUER_PROFILE_READ` |
| PUT | `/api/v1/billing/issuer-profiles/{id}` | `BILLING_ISSUER_PROFILE_UPDATE` |
| PATCH | `/api/v1/billing/issuer-profiles/{id}/status` | `BILLING_ISSUER_PROFILE_STATUS` |
| POST | `/api/v1/billing/fiscal-profiles` | `BILLING_FISCAL_PROFILE_CREATE` |
| GET | `/api/v1/billing/fiscal-profiles` | `BILLING_FISCAL_PROFILE_READ` |
| GET | `/api/v1/billing/fiscal-profiles/{id}` | `BILLING_FISCAL_PROFILE_READ` |
| PUT | `/api/v1/billing/fiscal-profiles/{id}` | `BILLING_FISCAL_PROFILE_UPDATE` |
| PATCH | `/api/v1/billing/fiscal-profiles/{id}/status` | `BILLING_FISCAL_PROFILE_STATUS` |
| PUT | `/api/v1/catalog/products/{id}/fiscal-classification` | `CATALOG_FISCAL_CLASSIFICATION_UPDATE` |
| PUT | `/api/v1/catalog/units/{id}/fiscal-classification` | `CATALOG_FISCAL_CLASSIFICATION_UPDATE` |
| POST | `/api/v1/billing/fiscal-documents` | `BILLING_FISCAL_DOCUMENT_CREATE` |
| GET | `/api/v1/billing/fiscal-documents` | `BILLING_FISCAL_DOCUMENT_READ` |
| GET | `/api/v1/billing/fiscal-documents/{id}` | `BILLING_FISCAL_DOCUMENT_READ` |
| POST | `/api/v1/billing/fiscal-documents/{id}/ready` | `BILLING_FISCAL_DOCUMENT_READY` |
| POST | `/api/v1/sync/inbox` | `SYNC_INBOX_WRITE` |
| GET | `/api/v1/sync/outbox` | `SYNC_OUTBOX_READ` |
| GET | `/api/v1/sync/devices/{id}/checkpoint` | `SYNC_CHECKPOINT_READ` |
| POST | `/api/v1/sync/devices/{id}/checkpoint` | `SYNC_CHECKPOINT_ACK` |
| GET | `/api/v1/sync/conflicts` | `SYNC_CONFLICT_READ` |
| POST | `/api/v1/sync/conflicts/{id}/resolve` | `SYNC_CONFLICT_RESOLVE` |

## Flujo Fiscal Mínimo

1. Clasificar producto y unidad con códigos SAT.
2. Crear un perfil emisor activo para la sucursal.
3. Crear un perfil receptor activo para el cliente.
4. Crear el documento desde una venta `CONFIRMED` y `PAID`.
5. Revisar snapshots y totales.
6. Marcar el documento como `READY`.

```json
{
  "salesOrderId": "00000000-0000-0000-0000-000000000001",
  "issuerProfileId": "00000000-0000-0000-0000-000000000002",
  "fiscalProfileId": "00000000-0000-0000-0000-000000000003",
  "series": "A",
  "folio": "1001",
  "paymentFormCode": "01",
  "paymentMethodCode": "PUE"
}
```

## Envelope Sync

```json
{
  "operationId": "00000000-0000-0000-0000-000000000101",
  "deviceId": "00000000-0000-0000-0000-000000000201",
  "deviceSequence": 1,
  "idempotencyKey": "00000000-0000-0000-0000-000000000301",
  "operationType": "INVENTORY_COUNT_CREATE",
  "aggregateType": "INVENTORY_COUNT",
  "aggregateId": null,
  "clientCreatedAt": "2026-07-29T12:00:00Z",
  "payload": {
    "warehouseId": "00000000-0000-0000-0000-000000000011",
    "notes": "Conteo offline de pasillo 1"
  }
}
```

Operaciones admitidas: `INVENTORY_COUNT_CREATE` y `CART_UPSERT`. El límite de outbox es 100 por defecto y 500 como máximo.
