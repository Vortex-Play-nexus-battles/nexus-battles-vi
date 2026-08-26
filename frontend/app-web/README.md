# Aplicacion web

Vista de THE NEXUS BATTLES VI en **HTML5, CSS3 y JavaScript**, sin framework,
segun la pila tecnologica aprobada por el cliente (`Pila_Tecnologica_EmpresaA_1`,
version actualizada, seccion 4): *"no se utilizara Angular ni ningun otro
framework de interfaz"*.

> Los archivos `src/comun/interceptors/http-error.interceptor.ts` y
> `src/cuentas/directives/has-permission.directive.ts` importan `@angular/core`
> y son anteriores a esa decision. Quedan pendientes de portar; nada nuevo debe
> escribirse contra Angular.

## Convenciones que fija la pila

| Tema | Que exige |
|---|---|
| Peticiones | API Fetch para HTTP, WebSocket nativo para tiempo real |
| Estado | JavaScript modular y DOM, un archivo por responsabilidad |
| Estilos | variables CSS con la paleta de la propuesta de diseno |
| Tipografia | **Rajdhani** e **Inter**, autoalojadas |
| Componentes | tarjetas, tablas, paginacion de 16 y barra de vida en `shared/ui-kit` |
| Cuatro estados | carga, error, exito y vacio en **toda** vista que consulte datos (RNF-USA-003) |
| Pruebas | Playwright para flujos, mas pruebas de JavaScript |

## Deudas abiertas

- **`shared/ui-kit` esta vacio.** La tarjeta de producto y la rejilla de
  dieciseis viven hoy en `src/contenido/inventario/`; les corresponde mudarse
  cuando el equipo de plataforma levante el kit.
- **La propuesta de diseno no esta en el repositorio.** Sin ella no hay paleta
  ni fuentes autoalojadas: el tema provisional esta en un solo bloque de
  `vitrina.css` para sustituirlo de una vez. Ademas sigue abierta con el
  cliente la pregunta de si existe un manual de marca que la reemplace.
- **Los cuatro estados no estan centralizados.** La pila pide funciones
  comunes; hoy no existen.

## Pruebas

Unitarias sobre jsdom, con Jest y modulos ES nativos:

```bash
npm install
npm test
```

Las de comportamiento en navegador real, con Playwright, llegan con `SCRUM-321`.
