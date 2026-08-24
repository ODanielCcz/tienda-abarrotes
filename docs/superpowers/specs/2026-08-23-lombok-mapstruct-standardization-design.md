# Diseño: estandarización de Lombok y MapStruct

**Fecha:** 2026-08-23
**Estado:** Propuesto para implementación
**Alcance:** `apps/backend`

## Contexto

El backend ya compila con Lombok y MapStruct, utiliza inyección por constructor y contiene ocho mappers funcionales. Sin embargo, el uso de MapStruct está concentrado en catálogo y autenticación, mientras que módulos posteriores conservan mapeos estructurales manuales. Lombok está bien extendido, pero aún existen algunos constructores triviales y no hay una política documentada que indique qué anotación corresponde a cada tipo de clase.

La meta no es agregar anotaciones a todos los archivos. La meta es revisar todo el backend y aplicar Lombok o MapStruct únicamente cuando reduzcan código accidental sin ocultar invariantes, validaciones ni decisiones de negocio.

## Objetivos

- Definir una política única de Lombok por capa y tipo de clase.
- Estandarizar la configuración de todos los mappers MapStruct.
- Eliminar constructores triviales que solo asignan dependencias.
- Sustituir mapeos estructurales repetitivos en adaptadores REST y persistencia.
- Mantener explícitas las validaciones, normalizaciones, cálculos y fábricas de dominio.
- Proteger las reglas mediante compilación, ArchUnit y pruebas existentes.

## Fuera de alcance

- Cambiar contratos JSON, rutas HTTP o códigos de respuesta.
- Cambiar reglas de negocio o transacciones.
- Modificar el esquema de base de datos.
- Introducir Lombok o MapStruct en Angular.
- Reemplazar constructores de dominio que preservan invariantes.
- Convertir mapeos JDBC desde `ResultSet` en MapStruct.

## Alternativas consideradas

### 1. Aplicar Lombok y MapStruct mecánicamente en todos los archivos

Reduce líneas rápidamente, pero puede ocultar validaciones, generar setters públicos innecesarios y mover lógica de negocio a expresiones de mapping. Se descarta.

### 2. Mantener el estado actual y corregir solo dos constructores

Tiene poco riesgo, pero mantiene la inconsistencia entre módulos y no resuelve el crecimiento del código manual. Se descarta.

### 3. Estandarización guiada por políticas

Se revisa todo el backend, pero cada clase recibe únicamente la herramienta adecuada para su responsabilidad. Es la alternativa seleccionada.

## Política de Lombok

| Tipo de clase | Decisión |
|---|---|
| Caso de uso, controlador o adaptador con dependencias `final` | Usar `@RequiredArgsConstructor`. |
| Clase que necesita logging | Usar `@Slf4j`; no crear loggers manuales. |
| Comando, consulta, respuesta o read model inmutable | Preferir `record`; no duplicar con Lombok. |
| Agregado o entidad de dominio | Mantener constructores y fábricas explícitas; permitir `@Getter`, `@EqualsAndHashCode` y `@ToString` solo cuando no rompan invariantes. |
| Entidad JPA | Permitir `@Getter`, `@Builder` y `@NoArgsConstructor(access = PROTECTED)`; evitar `@Data` y limitar setters a los realmente necesarios. |
| Excepción | Mantener constructores explícitos porque expresan el contrato del error. |
| Clase con validación, copia defensiva o valores derivados en constructor | Mantener constructor explícito. |
| Clase utilitaria estática | Constructor privado explícito o `@UtilityClass` únicamente si no altera pruebas ni visibilidad. |

Reglas adicionales:

- No se utilizará `@Data` en dominio ni persistencia.
- No se utilizará inyección directa en campos.
- `@RequiredArgsConstructor` solo se aplicará cuando el constructor generado sea semánticamente equivalente al actual.
- No se utilizará `@AllArgsConstructor` como sustituto de una fábrica de dominio.
- Se añadirá `lombok.config` para detener herencia accidental de configuración y marcar código generado cuando sea compatible con las herramientas actuales.

## Política de MapStruct

Se creará una configuración central compartida por adaptadores:

```java
@MapperConfig(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface CentralMapperConfig {
}
```

Reglas:

- MapStruct solo vivirá en adaptadores de entrada o salida.
- Dominio y aplicación no importarán MapStruct.
- Los IDs provenientes de la ruta, el usuario autenticado y otros valores de contexto serán parámetros explícitos del mapper.
- Los mappings de listas y objetos anidados se generarán cuando sean estructurales.
- Las fábricas como `Brand.restore(...)` continuarán en métodos `default`, porque preservan invariantes del dominio.
- Normalización, autorización, precios, impuestos, stock y decisiones de estado permanecerán en dominio o aplicación.
- No se crearán mappers para reemplazar una sola construcción trivial si el nuevo tipo agrega más código que el que elimina.

## Módulos que se revisarán

### Catálogo y autenticación

- Migrar los ocho mappers existentes a `CentralMapperConfig`.
- Conservar los métodos `default` que restauran agregados.
- Verificar que los ocho `MapperImpl` se generen durante una compilación limpia.

### Inventario

- Crear mappers REST para recepciones, ajustes, traspasos, conteos y reservas.
- Sustituir helpers repetitivos de request a command y de modelo a response.
- Mantener fuera del mapper las reglas de lote, caducidad y stock.

### Compras

- Crear mapper para partidas y pallets anidados.
- Mantener `purchaseId`, actor e idempotencia como contexto explícito.
- No mover al mapper la validación de cantidades recibibles.

### Organización y facturación

- Crear mappers únicamente para transformaciones estructurales repetidas.
- Conservar en los casos de uso la normalización de códigos, RFC, moneda y zona horaria.

### Ventas, clientes, caja y sincronización

- Reemplazar mappings repetitivos de request/response.
- Mantener actor, sucursal, dispositivo, secuencia e idempotencia como parámetros explícitos.
- No mapear decisiones de precio, descuento, impuestos, stock o conflictos offline.

## Clases con constructor manual intencional

Permanecerán explícitas, entre otras:

- agregados `Brand`, `Category`, `Product` y `ProductPresentation`;
- `RateLimitKeyEncoder`, por validación y copia defensiva del secreto;
- `InMemoryLoginRateLimiter`, por valores derivados de configuración;
- `ApiPayloadSizeFilter`, por selección de constructor, `@Value`, normalización y soporte de pruebas;
- excepciones con metadatos propios.

Los constructores de asignación directa, como los de `RedisLoginRateLimiter` y `LoginRateLimitMetrics`, sí podrán sustituirse por `@RequiredArgsConstructor`.

## Guardas arquitectónicas

Se ampliarán las pruebas ArchUnit para validar:

- dominio y aplicación no dependen de Lombok orientado a mutabilidad ni de MapStruct;
- no existe inyección en campos;
- los mappers se encuentran en paquetes `adapter`;
- los controladores no dependen de entidades JPA;
- las entidades JPA no se exponen como responses REST.

## Compatibilidad

El refactor no debe cambiar:

- nombres o tipos de campos JSON;
- códigos HTTP y códigos de negocio;
- validaciones y mensajes existentes;
- orden transaccional;
- SQL, migraciones o restricciones;
- comportamiento de idempotencia, autorización y auditoría.

## Estrategia de pruebas

1. Ejecutar compilación limpia para regenerar Lombok y MapStruct.
2. Ejecutar pruebas enfocadas por módulo después de cada grupo de mappers.
3. Ejecutar ArchUnit.
4. Ejecutar toda la suite backend con Docker/Testcontainers.
5. Construir `bootJar` e imagen Docker.
6. Comparar contratos OpenAPI antes y después cuando sea posible.

Comandos finales:

```powershell
cd "C:\Users\mine_\OneDrive\Documentos\ODCC\odcc\tienda-abarrotes\apps\backend"
.\gradlew.bat clean compileJava --no-daemon
.\gradlew.bat test --no-daemon
.\gradlew.bat bootJar --no-daemon
```

## Criterios de aceptación

- Todos los mappers comparten la configuración central.
- Cada mapper genera implementación durante compilación limpia.
- No existe inyección directa en campos.
- Los constructores manuales restantes contienen una razón observable: validación, transformación, compatibilidad con framework o contrato de error.
- Dominio y aplicación permanecen independientes de MapStruct y Spring.
- No se introduce `@Data` en dominio o persistencia.
- Los contratos REST y el comportamiento funcional permanecen sin cambios.
- Suite backend, ArchUnit, `bootJar` e imagen Docker terminan correctamente.
