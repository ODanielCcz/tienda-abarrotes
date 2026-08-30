# Angular Web MVP v1.0.0-rc1

| Campo         | Valor                   |
| ------------- | ----------------------- |
| Componente    | `apps/web`              |
| Versión       | `1.0.0-rc.1`            |
| Estado        | Candidato de liberación |
| Fecha         | 2026-08-30              |
| Clasificación | Interno                 |

## Alcance entregado

- Autenticación JWT, guards, permisos, correlation id y modo claro/oscuro.
- Contexto operativo revalidado por sucursal, almacén y caja.
- Catálogo de marcas, categorías, productos y presentaciones.
- Inventario, recepciones, lotes, movimientos y operaciones avanzadas.
- Proveedores y compras.
- Clientes, POS, historial de ventas, pagos, caja y devoluciones.
- Dashboard y reportes operativos v1/v2.
- Administración de usuarios, roles, permisos y organización.
- Perfiles y documentos fiscales internos, sin timbrado PAC.
- Consola técnica de Sync para inbox seguro, outbox, checkpoint y conflictos.

## Evidencia del candidato

| Verificación             | Resultado                                                                                                                                |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------- |
| Suite Angular            | 58 archivos, 152 pruebas aprobadas                                                                                                       |
| Build de producción      | Aprobado, chunk inicial de 97.56 kB sin comprimir y sin advertencias                                                                     |
| E2E Playwright           | 5 pruebas aprobadas                                                                                                                      |
| Login y catálogo real    | Aprobado contra Docker local                                                                                                             |
| Venta y pago real        | Aprobado contra Docker local                                                                                                             |
| Guard sin permiso        | Redirección a `/forbidden` aprobada                                                                                                      |
| Accesibilidad automática | Sin violaciones WCAG A/AA detectadas en login, dashboard y productos; navegación por teclado, modo oscuro y tabla responsive verificados |
| Consola del navegador    | Sin errores durante los cinco flujos E2E                                                                                                 |
| Dependencias npm         | `npm audit --omit=dev`: 0 vulnerabilidades                                                                                               |
| Backend                  | Suite Gradle completa aprobada después de agregar la regresión de clientes                                                               |

Durante el E2E se detectaron y corrigieron dos defectos reales:

- una consulta PostgreSQL de clientes fallaba cuando todos los filtros opcionales eran nulos; la regresión se conserva en `CustomerApiIntegrationTest`;
- el texto de validación del login no alcanzaba contraste WCAG AA; el token de color de Angular Material quedó ajustado y protegido por Axe.

## Limitaciones conocidas

- La facturación es un documento interno; no genera XML CFDI ni se conecta con un PAC.
- Sync solo admite las operaciones seguras publicadas por el backend; no habilita ventas o pagos offline.
- No se incluyen aplicación móvil, exportaciones PDF/Excel ni promociones manuales.
- Los E2E de venta modifican datos locales y no deben ejecutarse contra producción.
- La auditoría automática de accesibilidad complementa, pero no sustituye, la revisión manual con teclado y tecnologías de asistencia.

## Reproducción

Consulta [`../operations/angular-web-mvp.md`](../operations/angular-web-mvp.md) y ejecuta en `apps/web`:

```powershell
npm ci
npm test -- --watch=false
npm run build
npm audit --omit=dev
npm run e2e
```

Las variables `E2E_USERNAME` y `E2E_PASSWORD` deben existir únicamente durante el proceso E2E.
