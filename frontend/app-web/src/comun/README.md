# Comun — Código compartido del frontend

Utilidades y módulos compartidos entre todas las vistas de la aplicación web.
Dueño: los 3 Scrum Masters (aprobación transversal para cambios).

## Contenido

### `interceptors/`

- **`http-error.interceptor.js`** — Envoltorio de `fetch` que centraliza:
  - Inyección del token de autenticación (Keycloak)
  - Propagación del trace ID (regla de plataforma #5)
  - Interceptación de errores HTTP 403 (RFC 7807 Problem Details)
  - Notificaciones de acceso denegado (HU-RBAC-004)

## Reglas

- **Nunca** llamar a `fetch` directamente desde una vista — usar siempre `fetchWithHttpErrorInterceptor`
- Todo módulo nuevo aquí requiere aprobación de los 3 Scrum Masters
- Usar JSDoc para tipado, módulos ES2022 nativos
