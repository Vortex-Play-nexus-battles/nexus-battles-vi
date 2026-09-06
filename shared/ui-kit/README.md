# `shared/ui-kit` — sistema visual compartido

CSS y JS derivados del archivo de Figma «Nexus Battles VI — Sistema de diseño»
(`iMcw1JhmSAD6XjzDZuYlIz`). Se escribe una vez y lo consumen las vistas de los
tres Scrum Teams; **cualquier cambio aquí pasa por revisión de los tres SM**
(CODEOWNERS).

| Qué | Dónde |
|---|---|
| Fichas de diseño (colores, tipografía, espaciado, sombras, foco) | `css/tokens.css` |
| Reinicio y reglas base de página | `css/base.css` |
| Componentes (botón, campo, tarjeta, aviso, diálogo, cabecera, paginación…) | `css/componentes.css` |
| Iconos como sprite SVG | `iconos/sprite.svg` |
| Barra de vida (RF-JUE-009) | `js/barra-vida.js` |
| Tipografías autoalojadas | `fuentes/` |
| Muestrario navegable de todo lo anterior | `guia-estilo.html` |
| Cómo se traduce cada error HTTP a la interfaz | `MAPEO-ERRORES.md` |

Para usarlo desde una vista, enlazar las tres hojas en este orden:

```html
<link rel="stylesheet" href="../../../../../shared/ui-kit/css/tokens.css" />
<link rel="stylesheet" href="../../../../../shared/ui-kit/css/base.css" />
<link rel="stylesheet" href="../../../../../shared/ui-kit/css/componentes.css" />
```

## Tipografías

Rajdhani 600/700 e Inter 400/600, subconjunto latino (cubre el español),
en `fuentes/<familia>/*.woff2` con su licencia OFL 1.1 al lado. Provienen de
los paquetes `@fontsource/inter@5.3.0` y `@fontsource/rajdhani@5.3.0`, que
empaquetan los originales de Google Fonts sin modificarlos.

La pila tecnológica exige que vayan **autoalojadas** (`.claude/rules/frontend-web.md`):
ninguna hoja del proyecto debe apuntar a `fonts.googleapis.com`. Las declara
`tokens.css` con `@font-face`, así que basta con enlazarla.

## Conflicto abierto: dos cabeceras en `develop`

Hoy conviven **dos implementaciones de la misma barra superior**, y ninguna
vista debería adoptar una tercera:

|  | `frontend/app-web/src/comun/barra-navegacion.js` | `.cabecera` de este ui-kit |
|---|---|---|
| Historia | HU-INV-004 (grupo-alpha) | Componente `Cabecera` de Figma, publicado en #271 (grupo-omega) |
| Fuente | doc *Proyecto Integrador II* §7.1 | archivo de diseño `iMcw1JhmSAD6XjzDZuYlIz` |
| Construcción | JS: `construirBarra()` genera el DOM | HTML escrito en cada vista |
| Consumidores | 6 vistas de `contenido/` y `cuentas/` | 3 vistas de `plataforma/salas-partidas/` |
| Mi Cuenta | panel desplegable en sitio | avatar + apodo + rango, sin menú |
| Créditos y campana de notificaciones | no tiene | sí |
| Tokens | `--cromo`, `--radio`, `--tipografia-titulo` (no definidos en `tokens.css`) | los de `tokens.css` |
| Sesión | recibe el rol por parámetro; visitante por omisión | literales de ejemplo, sin sesión |

Lo que debe decidirse, y quién:

1. **Cuál sobrevive.** Decisión de los tres Scrum Masters. El propio
   `barra-navegacion.md` prevé la mudanza: «si pasa a servir a los tres grupos,
   se muda sin cambiar su interfaz». La opción menos costosa es que
   `construirBarra()` pase a emitir el marcado de `.cabecera` y consuma
   `tokens.css`, conservando su contrato de sesión; las tres vistas de salas
   dejarían de repetir el HTML.
2. **Tipografía de la cabecera.** Figma pinta los destinos en Inter Regular 12
   y la marca en Inter Regular 20; las fichas de diseño (fuente de verdad
   declarada) piden Rajdhani 700 para la marca e Inter 600/14 para etiquetas.
   Hasta que diseño lo resuelva, el ui-kit sigue las fichas.

Mientras no haya acuerdo, **no se toca `barra-navegacion`** (es de otro
equipo y de otra historia) y **no se amplía `.cabecera`** más allá de
correcciones. Ver PR #271, sección «Impacto en `shared/ui-kit`», y la
conversación abierta con `@Nicolayyy`.
