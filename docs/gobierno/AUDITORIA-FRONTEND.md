# Auditoría del frontend — estado real antes del bloque UX

22-sep-2026, sobre `develop`. Complementa `AUDITORIA-UX-VISTAS.md` (R3, que
resolvió **navegación y sesión**) mirando ahora **hacia dentro de cada vista**.

## El hallazgo que cambia el diagnóstico

El sistema de diseño **ya existe** y está bien: `shared/ui-kit/css/tokens.css`
tiene 92 fichas (color, rareza, vida, espaciado, radio, sombra, tipografía,
motion, foco) y `componentes.css` define 2.457 líneas con 120 clases derivadas
de Figma.

El problema es que **las vistas no lo usan**:

| Componente que el kit ya dibuja | Vistas que lo usan |
|---|---|
| `tarjeta--subasta`, `tarjeta--producto` | **0** |
| `marco-heroe` (y sus rarezas) | **0** |
| `esqueleto` (carga) | **0** |
| `distintivo` (estado, rol, rareza) | **0** |
| `ranura`, `metrica`, `pestanas`, `encuentro`, `logro`, `lote`, `linea-tiempo` | **0** |
| `estado-vista` | 6 de 31 |
| `tarjeta` | 23 de 31 |

Y en su lugar hay **5.562 líneas de CSS local** repartidas en 21 archivos:

| Archivo | Líneas | Colores a mano | `var(--token)` |
|---|---|---|---|
| `cuentas/pujas.css` | 1.515 | 80 | 145 |
| `contenido/inventario/vitrina.css` | 531 | 18 | 56 |
| `cuentas/tema-cuentas.css` | 387 | 28 | 35 |
| `cuentas/gestion-usuarios.css` | 353 | 33 | **0** |
| `cuentas/subastas-vitrina.css` | 341 | 0 | 106 |
| `cuentas/perfil.css` | 314 | 29 | **0** |
| `cuentas/crear-cuenta-admin.css` | 313 | 35 | **0** |
| `cuentas/tienda.css` | 188 | 21 | **0** |

Cuatro archivos no usan **ni una sola** ficha de diseño. Tres de esos cuatro
son, exactamente, las vistas que en las capturas se ven como otra aplicación.
`tema-cuentas.css` es un tema paralelo completo.

## Duplicación en JavaScript

| Qué | Copias |
|---|---|
| `function nodo()` — crear un elemento con clase y texto | 4 |
| `function pintarAviso()` — el aviso de la vista | 6, en **dos familias incompatibles** |
| `document.createElement` a pelo | 34 archivos |
| Funciones de formato propias (fecha, créditos) | 6 |

Las dos familias de `pintarAviso` importan: tres vistas convertían **la zona**
en el aviso (`zona.className = 'aviso aviso--error'`) y no ponían `role`, así
que un lector de pantalla no se enteraba de que algo había fallado. Las otras
tres sí creaban un hijo con `role`. El mismo aviso, dos comportamientos de
accesibilidad distintos según en qué pantalla estuvieras.

## Matriz de las 31 vistas

`shell` = usa `cabecera-app.js`. `vacío`/`carga`/`error` = la vista resuelve
ese estado.

| Vista | Dominio | shell | guard | vacío | carga | error | JS |
|---|---|---|---|---|---|---|---|
| `contenido/inventario/inventario` | Contenido | sí | sí | sí | sí | sí | 569 |
| `contenido/productos/productos` | Contenido | sí | — | sí | **no** | sí | 205 |
| `cuentas/auditoria` | Cuentas | sí | sí | sí | sí | sí | 228 |
| `cuentas/crear-cuenta-admin` | Cuentas | **no** | no | **no** | sí | sí | 432 |
| `cuentas/gestion-usuarios` | Cuentas | **no** | no | **no** | **no** | sí | 645 |
| `cuentas/historial-transacciones` | Cuentas | sí | sí | sí | sí | sí | 163 |
| `cuentas/index` | Cuentas | **no** | no | sí | **no** | **no** | 95 |
| `cuentas/login` | Cuentas | sí | — | sí | **no** | sí | 195 |
| `cuentas/mis-cofres` | Cuentas | sí | sí | sí | sí | sí | 129 |
| `cuentas/perfil` | Cuentas | **no** | no | sí | sí | sí | 513 |
| `cuentas/publicar-subasta` | Cuentas | sí | **no** | sí | sí | sí | 416 |
| `cuentas/pujas` | Cuentas | sí | **no** | sí | sí | sí | **2.297** |
| `cuentas/registro` | Cuentas | sí | — | sí | **no** | sí | 294 |
| `cuentas/restablecer-confirmar` | Cuentas | sí | — | sí | **no** | sí | 128 |
| `cuentas/restablecer-solicitar` | Cuentas | sí | — | **no** | **no** | sí | 112 |
| `cuentas/subastas` | Cuentas | sí | — | sí | sí | sí | 345 |
| `cuentas/tienda` | Cuentas | sí | sí | sí | sí | sí | 215 |
| `plataforma/admin-parametros/parametros-admin` | Plataforma | sí | sí | — | — | — | 349 |
| `plataforma/comentarios/publicar-comentario` | Plataforma | sí | sí | sí | sí | sí | 828 |
| `plataforma/metricas/panel-metricas` | Plataforma | sí | sí | sí | sí | sí | 343 |
| `plataforma/metricas/tablero-tecnico` | Plataforma | sí | sí | sí | **no** | sí | 326 |
| `plataforma/moderacion/lista-negra-admin` | Plataforma | sí | sí | sí | sí | sí | 234 |
| `plataforma/moderacion/mis-sanciones` | Plataforma | sí | sí | — | — | — | 552 |
| `plataforma/moderacion/sanciones-admin` | Plataforma | sí | sí | — | — | — | 552 |
| `plataforma/notificaciones/notificaciones` | Plataforma | sí | sí | sí | — | — | 261 |
| `plataforma/salas-partidas/batallas` | Plataforma | sí | sí | sí | sí | sí | 444 |
| `plataforma/salas-partidas/chat` | Plataforma | sí | sí | sí | **no** | sí | 212 |
| `plataforma/salas-partidas/crear-sala` | Plataforma | sí | sí | sí | sí | sí | 401 |
| `plataforma/salas-partidas/sala-batalla` | Plataforma | sí | sí | sí | **no** | sí | 176 |
| `plataforma/salas-partidas/validacion-heroe` | Plataforma | sí | sí | sí | sí | sí | 419 |
| `plataforma/torneos/torneos` | Plataforma | sí | **no** | sí | **no** | sí | 546 |

**27 de 31** vistas llevan el shell. Las 4 que no, todas de Cuentas:
`index`, `perfil`, `gestion-usuarios`, `crear-cuenta-admin`.
**10 de 31** no tienen estado de carga: el rectángulo en blanco de las
capturas de Torneo y Subastas es exactamente eso.

## Lo que se ve en las capturas, explicado

| Captura | Qué se ve | Causa medida |
|---|---|---|
| Login | correcto pero vacío y sin identidad | `login.css` tiene **1 línea**; toda la pantalla se apoya en el kit sin composición propia |
| Mi perfil | panel administrativo | `perfil.css`: 314 líneas, **0** fichas de diseño, y sin el shell |
| Subastas | filtros + «Error 502 al consultar subastas» | sin `estadoDeError`: el mensaje técnico llega crudo a quien juega |
| Torneo | página casi vacía | sin estado de carga ni estado vacío; el `<h1>` además se sale del contenedor |

## Conclusión

No hace falta inventar un sistema de diseño: hace falta **una capa de
componentes en JavaScript que lo use**, y migrar las vistas a ella. Ese es el
contenido de PR-UX-1 y de los PR que le siguen.
