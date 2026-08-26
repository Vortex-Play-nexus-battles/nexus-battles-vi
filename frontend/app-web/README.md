# Aplicacion web

Vista de THE NEXUS BATTLES VI en **HTML, CSS y JavaScript plano**, sin
framework, segun la pila que el cliente aprobo el 2026-08-24.

> Los archivos `src/comun/interceptors/http-error.interceptor.ts` y
> `src/cuentas/directives/has-permission.directive.ts` importan `@angular/core`
> y son anteriores a esa decision. Quedan pendientes de portar; nada nuevo debe
> escribirse contra Angular.

## Modulos

| Ruta | Que hay |
|---|---|
| `src/contenido/inventario/` | vitrina del inventario (HU-INV-001) |

## Pruebas

Sobre jsdom, con Jest y modulos ES nativos:

```bash
npm install
npm test
```
