# Frontend — Código fuente (`src/`)

Código HTML, CSS y JavaScript de la interfaz web de **The Nexus Battles VI**.
Sin framework ni paso de compilación — cada vista es un `.html` + `.js` del mismo nombre,
servidos como recursos estáticos por Spring Boot.

## Estructura

| Carpeta | Contenido | Dueño |
|---------|-----------|-------|
| `comun/` | Interceptores HTTP, utilidades compartidas entre vistas | 3 Scrum Masters |
| `cuentas/` | Login, registro, demo RBAC, directivas de permisos | Equipo Cuentas |
| `contenido/` | Inventario, vitrina de productos | Equipo Contenido |
| `plataforma/` | Moderación, sanciones, salas de batalla, chat | Equipo 6 |

## Reglas

- Una página HTML + un JS del mismo nombre por vista
- Lo que se repite en dos vistas sube a `comun/` o `shared/ui-kit/`
- Consumo de API siempre a través de `comun/interceptors/http-error.interceptor.js`
- Verificación de tipos con JSDoc
- Calidad: ESLint + Prettier · Pruebas E2E: Playwright

Ver [frontend-web.md](../../../.claude/rules/frontend-web.md) para la referencia completa.
