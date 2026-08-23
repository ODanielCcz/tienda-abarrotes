# CI/CD Del Backend

El workflow [Backend CI](../../.github/workflows/backend-ci.yml) se ejecuta en cada pull request y push a `main`, manualmente y cada lunes. No despliega a ningún ambiente; valida el candidato antes de que otro proceso decida publicar una imagen.

## Controles

1. Ejecuta la suite Gradle con PostgreSQL/Testcontainers.
2. Genera el `bootJar`.
3. Levanta PostgreSQL aislado mediante Docker Compose.
4. Aplica Flyway y valida automáticamente la última migración versionada disponible.
5. Construye el contenedor del backend y comprueba `/actuator/health/readiness`.
6. Analiza la imagen con Trivy y bloquea vulnerabilidades `HIGH` o `CRITICAL` corregibles.
7. Conserva los reportes de Gradle durante catorce días.

## Seguridad De La Cadena De Suministro

- Gitleaks revisa el historial completo y solo admite dos fingerprints de prueba documentados en `.gitleaksignore`.
- Dependency Review bloquea dependencias nuevas con severidad `HIGH` o `CRITICAL`.
- Dependency Submission genera el grafo Gradle para Dependabot sin ejecutar código de un pull request con permisos de escritura.
- Trivy revisa filesystem, configuración e imagen del backend.
- Las acciones de GitHub se fijan por SHA y Dependabot propone sus actualizaciones.
- La imagen Java del backend se fija por digest y conserva su etiqueta legible.
- `apk upgrade` actualiza paquetes de Alpine en cada build; por ello, el digest fija la imagen base pero no convierte el build en un artefacto bit-a-bit reproducible. El escaneo semanal es obligatorio para detectar CVE aparecidos después de una compilación.
- Cada job declara los permisos mínimos requeridos. No se publica imagen ni se requiere un secreto de registro en esta etapa.

Los checks que deben proteger `main` son `Secret scan`, `Filesystem security scan`, `Dependency graph`, `Dependency review` y los jobs de `Backend CI`.

## Regla Para RC1

La etiqueta `backend-v1.0.0-rc1` se crea únicamente cuando el workflow remoto finaliza correctamente y la validación aislada local documentada en [rc1-validation.md](rc1-validation.md) también termina en verde.

## Futuro Despliegue

Cuando exista un entorno compartido, se agregará un workflow separado de despliegue con GitHub Environments, aprobación manual, secretos del entorno y una imagen identificada por digest. El workflow de validación no debe reutilizarse para desplegar.
