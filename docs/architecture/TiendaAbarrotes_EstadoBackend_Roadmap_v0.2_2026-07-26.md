# Estado Del Backend Y Roadmap De Modulos

| Campo | Valor |
|---|---|
| Proyecto | Tienda de Abarrotes |
| Codigo | TDA-ARQ-ROADMAP-002 |
| Version | v0.2 |
| Estado | Borrador |
| Responsable | Por definir |
| Fecha | 2026-07-26 |
| Clasificacion | Interno |

## Control De Versiones

| Version | Fecha | Autor | Cambio | Estado |
|---|---|---|---|---|
| v0.1 | 2026-07-23 | Codex | Documento historico inicial en PDF con estado y hoja de ruta | Borrador |
| v0.2 | 2026-07-26 | Codex | Actualizacion del backend implementado y modulos pendientes | Borrador |

## Proposito

Documentar el estado actual del backend de la tienda de abarrotes, registrar lo que ya fue construido y dejar una ruta clara de los modulos que faltan para completar la plataforma.

Este documento no reemplaza la documentacion de API, base de datos o decisiones tecnicas. Funciona como resumen ejecutivo-tecnico para continuar el desarrollo con orden.

## Alcance Actual

| Area | Estado | Observacion |
|---|---|---|
| Base de datos | Implementado parcialmente | PostgreSQL con Docker, Flyway, esquemas, auditoria y migraciones iniciales |
| Backend Spring Boot | Implementado parcialmente | Proyecto con Gradle Kotlin DSL, Java 21 y estructura modular hexagonal |
| Seguridad | Implementado | JWT, login, roles, permisos y autorizacion por caso de uso |
| Catalogo - marcas y categorias | Implementado | CRUD funcional controlado: crear, consultar, listar, actualizar y cambiar estado |
| Auditoria y bitacora | Implementado parcialmente | Eventos de negocio, seguridad, correlacion y restricciones append-only |
| Observabilidad | Implementado | Actuator, health, metricas Prometheus, tracing OpenTelemetry y correlation id |
| Documentacion tecnica | Implementado parcialmente | API v1, base de datos, operaciones Docker, ADR y release notes |
| Web Angular | Pendiente | Estructura contemplada, aplicacion no creada todavia |
| Movil | Pendiente | Estructura contemplada, aplicacion no creada todavia |

## Trabajo Realizado

| Codigo | Componente | Descripcion | Estado |
|---|---|---|---|
| TR-001 | Estructura del proyecto | Se definio la estructura monorepo con `apps`, `database`, `docs`, `scripts`, `deploy` y archivos raiz de control | Completado |
| TR-002 | Docker de base de datos | Se configuro PostgreSQL en Docker Compose con puerto local `5433` y migraciones Flyway | Completado |
| TR-003 | Modelo inicial de base de datos | Se crearon esquemas, tablas base, catalogos iniciales, auditoria y bitacoras | Completado parcial |
| TR-004 | Backend Spring Boot | Se creo el backend en `apps/backend` con Java 21, Spring Boot y Gradle Kotlin DSL | Completado |
| TR-005 | Arquitectura hexagonal | Se separaron capas de dominio, aplicacion, puertos, adaptadores e infraestructura | Completado |
| TR-006 | Modulo de marcas | Se implementaron casos de uso, validaciones, repositorios, persistencia JPA, controladores REST y DTOs | Completado |
| TR-007 | Respuesta API estandar | Se agrego `ApiResponseDto` como `record` para respuestas JSON consistentes | Completado |
| TR-008 | Manejo global de errores | Se agrego tratamiento centralizado para validaciones, conflictos, no encontrado, no autorizado y errores internos | Completado |
| TR-009 | Seguridad | Se implemento login, JWT, BCrypt, roles, permisos, `PreAuthorize` y respuestas seguras 401/403 | Completado |
| TR-010 | Auditoria | Se registran eventos de negocio y seguridad con actor, rol y correlation id | Completado parcial |
| TR-011 | Observabilidad | Se agregaron health checks, metricas, trazas, logs estructurados y correlation id por request | Completado |
| TR-012 | Pruebas | Se agregaron pruebas unitarias, integracion, arquitectura, seguridad, auditoria, OpenAPI y observabilidad | Completado |
| TR-013 | Documentacion | Se actualizaron README, guia API, release notes y documentos tecnicos relacionados | Completado parcial |

## Modulos Pendientes

| Prioridad | Modulo | Objetivo | Dependencias | Resultado Esperado |
|---|---|---|---|---|
| 1 | Categorias | Clasificar productos por familia o tipo, por ejemplo bebidas, limpieza, lacteos y abarrotes | Seguridad y catalogo base | Implementado en backend |
| 2 | Productos | Registrar productos con SKU/codigo, nombre, marca, categoria, unidad, precio y estado | Marcas y categorias | Catalogo principal de productos listo para inventario |
| 3 | Inventario | Controlar existencias, entradas, salidas, ajustes y stock minimo | Productos | Stock confiable por producto y preparacion para venta concurrente |
| 4 | Movimientos de inventario | Mantener kardex o historial de cada cambio de stock | Inventario | Trazabilidad de entradas, salidas, ajustes y ventas |
| 5 | Proveedores | Registrar proveedores de mercancia | Productos | Base para compras y reposicion de stock |
| 6 | Compras | Registrar compras a proveedores y aumentar inventario | Proveedores, productos e inventario | Entrada formal de mercancia con trazabilidad |
| 7 | Ventas | Registrar ventas, detalle de productos, totales y reduccion de stock | Productos, inventario y seguridad | Venta transaccional segura con control de concurrencia |
| 8 | Caja y pagos | Controlar pagos, cortes de caja y movimientos monetarios | Ventas | Registro financiero basico de operacion diaria |
| 9 | Usuarios desde API | Administrar usuarios, roles y permisos desde endpoints protegidos | Seguridad actual | Gestion operativa de usuarios sin tocar directamente la BD |
| 10 | Clientes | Registrar clientes frecuentes, credito o datos fiscales si aplica | Ventas | Base para ventas asociadas a cliente y posibles creditos |
| 11 | Reportes | Consultar ventas, productos bajos en stock, compras y movimientos | Ventas, inventario y compras | Informacion operativa para tomar decisiones |
| 12 | Aplicacion web Angular | Crear interfaz administrativa para catalogo, inventario, compras y ventas | Backend estable | Panel web funcional |
| 13 | Aplicacion movil | Crear app movil para operacion o consulta segun alcance definido | Backend estable | App movil conectada al backend |

## Siguiente Orden Recomendado

| Orden | Trabajo | Razon Tecnica |
|---|---|---|
| 1 | Categorias | Completa la base del catalogo sin tocar todavia inventario ni ventas |
| 2 | Productos | Es el nucleo del negocio; necesita marcas y categorias estables |
| 3 | Inventario | Requiere productos definidos para controlar stock correctamente |
| 4 | Movimientos de inventario | Permite auditar cada cambio antes de vender |
| 5 | Ventas | Debe construirse cuando el stock ya tenga reglas transaccionales claras |
| 6 | Compras y proveedores | Alimenta inventario y permite reposicion formal |
| 7 | Caja y pagos | Cierra el flujo operativo de venta diaria |
| 8 | Usuarios desde API | Convierte la seguridad actual en administracion usable |

## Reglas Tecnicas Pendientes De Definir

| Codigo | Regla | Decision Pendiente | Impacto |
|---|---|---|---|
| RN-001 | Stock concurrente | Definir si la venta usara bloqueo pesimista, bloqueo optimista o update condicional atomico | Evita vender mas unidades de las disponibles |
| RN-002 | Unidad de venta | Definir si productos se venden por pieza, peso, paquete o multiples unidades | Afecta producto, inventario y ventas |
| RN-003 | Precio historico | Definir si el precio de venta se congela en el detalle de venta | Evita inconsistencias en reportes historicos |
| RN-004 | Stock minimo | Definir umbral por producto o por sucursal | Permite alertas de reposicion |
| RN-005 | Multi-sucursal | Confirmar si el inventario sera global o por sucursal desde la primera version | Afecta todas las tablas de inventario y ventas |
| RN-006 | Auditoria funcional | Definir retencion y nivel de detalle de eventos de negocio | Afecta almacenamiento y cumplimiento interno |

## Riesgos

| Codigo | Riesgo | Probabilidad | Impacto | Mitigacion |
|---|---|---|---|---|
| RIE-001 | Construir ventas antes de cerrar inventario | Media | Alto | Implementar inventario y movimientos antes de ventas |
| RIE-002 | No definir unidad de venta desde producto | Media | Alto | Modelar unidad base y reglas de conversion antes de compras/ventas |
| RIE-003 | Mezclar logica de negocio en controladores | Baja | Medio | Mantener casos de uso y puertos como frontera principal |
| RIE-004 | Exponer endpoints sin permisos finos | Baja | Alto | Continuar usando permisos por caso de uso |
| RIE-005 | Duplicar reglas en web y movil | Media | Medio | Mantener reglas criticas en backend y usar clientes como capa de presentacion |

## Evidencia De Validacion

| Evidencia | Resultado |
|---|---|
| Pruebas backend | Suite final ejecutada con pruebas unitarias e integracion en verde |
| Base de datos | Migraciones Flyway aplicadas hasta version 013 |
| API local | Backend levantado en `http://localhost:8080` con perfil `local` |
| OpenAPI | Documentacion disponible en `/v3/api-docs` y Swagger UI si el backend esta activo |
| Docker | PostgreSQL disponible localmente en puerto `5433` |

## Documentos Relacionados

| Documento | Ruta |
|---|---|
| Guia API Backend v1 | `docs/api/backend-api-v1.md` |
| Modelo de base de datos | `docs/architecture/database-model.md` |
| Diccionario de datos | `docs/architecture/data-dictionary.md` |
| Operacion Docker BD | `docs/operations/database-docker.md` |
| Release notes | `docs/releases/RELEASE_NOTES.md` |
| ADR arquitectura hexagonal | `docs/decisions/ADR-0004-arquitectura-hexagonal-backend.md` |

## Pendientes

| Codigo | Pendiente | Responsable | Prioridad | Estado |
|---|---|---|---|---|
| PEN-001 | Implementar la siguiente vertical: productos | Por definir | Alta | Pendiente |
| PEN-002 | Definir reglas de inventario concurrente antes de ventas | Por definir | Alta | Pendiente |
| PEN-003 | Confirmar alcance de usuarios administrables desde API | Por definir | Media | Pendiente |
| PEN-004 | Definir si el documento debe exportarse tambien a PDF | Por definir | Baja | Pendiente |