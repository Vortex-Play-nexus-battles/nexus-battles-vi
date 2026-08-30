# Contexto de trabajo para Cursor — Nexus Battles VI / Sprint 1

## Proyecto y estado actual

- Repositorio local: `/Users/andresnunez/Desktop/PI2`
- Remoto: `https://github.com/Vortex-Play-nexus-battles/nexus-battles-vi`
- Rama de trabajo: `develop`
- El usuario es **Andrés Núñez** (AN en el tablero del Sprint 1).
- No hacer commit, push ni modificar trabajo ajeno sin autorización expresa de Andrés.

La rama local `develop` está dos commits por delante de `origin/develop`:

- `3d13955 feat(frontend): migrar interceptor 403 y directiva hasPermission de RBAC a JavaScript estandar (ES2022)`
- `6b0e3c5 chore(frontend): eliminar archivos temporales TypeScript (interceptor y directiva de permisos)`

El commit `6ea881f fix(identidad): adaptar persistencia de roles` ya fue enviado a `origin/develop`.

## Responsabilidad de Andrés en Sprint 1

Andrés tiene asignadas estas historias:

### HU-RBAC-001 — Modelo y matriz de permisos por rol

Objetivo: UI debe habilitar/ocultar acciones por rol de acuerdo con Tabla 24 y el servidor debe rechazar intentos no permitidos.

Criterios oficiales:

1. Cada cuenta tiene uno de cuatro roles: Jugador, Moderador, Administrador, Super Administrador; un rol no reconocido recibe mínimo privilegio.
2. Las **10 acciones** de la Tabla 24 deben seguir exactamente la matriz; Moderador tiene permiso `Temporal` al suspender usuarios.
3. Las combinaciones no contempladas se deniegan por defecto y una inconsistencia entre matriz y permisos efectivos genera una alerta.
4. Frontend: selector con descripción de rol en HU-USR-002/003; ocultar o deshabilitar acciones administrativas no permitidas y mostrar acceso denegado ante intentos directos.

### HU-RBAC-004 — Verificación de autorización en el servidor

Objetivo: los microservicios verifican credencial de sesión y permiso antes de operar, independientemente de la UI.

Criterios oficiales:

1. Cada microservicio valida credencial de sesión y permiso antes de ejecutar la operación.
2. Si autorización no está disponible, denegar (fail-closed).
3. Registrar intentos no autorizados.
4. Frontend muestra "no tienes permiso para esta acción" tras 403.
5. Alcance técnico: mecanismo reutilizable y aplicado a cuentas, roles y auditoría; prueba de bypass.

## Lo que está implementado y verificado

### Backend RBAC en `services/cuentas/ms-identidad`

- `Role`: cuatro roles.
- `RolEntity` + `RolSeeder`: persistencia de roles y siembra inicial.
- `RbacMatrixRepository`: matriz central en memoria.
- `RbacAuthorizationService`: evaluación con `default-deny`.
- `@RequirePermission` + `SecurityInterceptor`: deniega rutas anotadas y produce Problem Details 403.
- `RbacController`: consulta de roles, matriz y evaluación de permisos.
- `AuthAdminService` sigue usando `Role` como contrato público; internamente convierte a `RolEntity` mediante `RolService`.
- `mvn test` en `services/cuentas/ms-identidad` pasa: **9 pruebas correctas**.
- Los módulos `ms-identidad`, `ms-cumplimiento` y `ms-ecommerce` pasan `mvn compile`.

### Frontend local (pendiente de publicar)

- `frontend/app-web/src/cuentas/directives/has-permission.directive.js`: helper de UI RBAC.
- `frontend/app-web/src/comun/interceptors/http-error.interceptor.js`: captura 403 y muestra mensaje al usuario.

## Integración con Edwin (HU-RBAC-003)

Edwin implementa asignación y modificación de roles. No modificar ni eliminar `Action.ASIGNAR_ROL` sin coordinar con él.

Edwin debe actualizar `develop`, levantar su PostgreSQL local y utilizar:

```java
authAdminService.actualizarRol(usuarioId, nuevoRol);
```

Su endpoint debe protegerse con:

```java
@RequirePermission(Action.ASIGNAR_ROL)
```

Actualmente el permiso `ASIGNAR_ROL` se reserva para `SUPER_ADMINISTRADOR`.

## Brechas conocidas: NO afirmar que las historias estén cerradas

### HU-RBAC-001

- La historia oficial pide 10 acciones / 40 combinaciones, pero el código tiene 11 acciones / 44 combinaciones porque incluye `ASIGNAR_ROL` de HU-RBAC-003.
- Backend y frontend contienen 11 acciones, mientras `contracts/openapi/rbac.yaml` aún lista 10. Se requiere acuerdo con Edwin y Scrum Master antes de cambiar contratos compartidos.
- No existe una alerta en tiempo de ejecución para inconsistencias entre matriz y permisos efectivos.
- No existen pantallas reales de creación/edición de cuentas; frontend actual solo contiene los dos helpers JS.

### HU-RBAC-004

- `SecurityInterceptor` usa `X-User-Role` y `X-User-Name`; esos headers son manipulables. No existe JWT ni sesión validada.
- El motor RBAC es un bean local, no un servicio remoto con manejo real de caída/timeout entre microservicios.
- Los rechazos se registran mediante log `AUDIT_TRAIL`, pero aún no se persisten ni se envían a auditoría.
- `ms-cumplimiento` aún es un esqueleto: no hay endpoints de auditoría que integrar/proteger.
- Aplicación actual de `@RequirePermission`: perfil y endpoint demo de baneo; no hay endpoints administrativos reales terminados.

## Dependencias y límites de trabajo

- **HU-AUT-004** (iniciar sesión) debe entregar credencial verificable antes de reemplazar headers por JWT/sesión.
- El responsable de auditoría debe acordar el contrato para registrar denegaciones.
- `contracts/` es compartido: no cambiar `rbac.yaml` unilateralmente.
- No editar módulos de otros equipos (`ms-cumplimiento`, ecommerce, frontend ajeno) sin autorización.
- No sustituir el mecanismo actual por JWT todavía: hacerlo antes de que login esté listo bloquearía a los compañeros.

## Comandos útiles

```bash
cd /Users/andresnunez/Desktop/PI2
git status --short --branch

cd infrastructure/contenedores
docker compose -f docker-compose.ms-identidad.yml up -d

cd ../../services/cuentas/ms-identidad
mvn test
```

## Próximo trabajo seguro para Andrés

1. Coordinar con Edwin y Scrum Master la definición formal 10/40 frente a 11/44.
2. Publicar los dos commits frontend locales solo si Andrés lo autoriza.
3. Esperar contrato/entrega de login y auditoría antes de implementar la parte final de HU-RBAC-004.
4. Dar soporte a Edwin para que consuma `AuthAdminService` y aplique `@RequirePermission(Action.ASIGNAR_ROL)`.
