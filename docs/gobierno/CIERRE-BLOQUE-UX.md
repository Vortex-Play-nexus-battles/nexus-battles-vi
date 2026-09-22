# Cierre del bloque de rediseño de frontend (PR-UX-1 … PR-UX-8)

22-sep-2026. Cierra lo que abrió `AUDITORIA-FRONTEND.md`.

## El diagnóstico se confirmó, y resultó ser peor de lo escrito

La auditoría decía: *el sistema de diseño ya existe y está bien; el problema es
que las vistas no lo usan*. Eso era cierto, y al ejecutarlo aparecieron cuatro
formas de «no usarlo» que la auditoría no había medido, todas con consecuencia
para alguien:

| Forma | Tamaño | A quién afecta |
|---|---|---|
| Copiar los **tokens** sobre un selector propio | 56 declaraciones en 4 hojas | Quien activa «aumentar contraste»: el kit redefine 43 de esas variables en `@media (prefers-contrast: more)` y la copia local ganaba. **Ocho vistas ignoraban la preferencia.** |
| Redefinir los **componentes** con su mismo nombre | 14 bloques en 4 hojas | Todo el mundo: la misma `.tarjeta` con tres paddings y dos radios; el `.aviso` siempre azul aunque el modificador dijera error |
| Cargar el kit **a medias** | 8 vistas sin `base.css` | Esas vistas rellenaban el hueco por su cuenta y no tenían `.solo-lectores`, el foco único ni el arreglo de `[hidden]` |
| Usar variables que **no existen** | 12 referencias en 3 hojas | `panel-metricas.css` traía una paleta **oscura** escondida en los valores de respaldo: fichas azul marino sobre un panel blanco, y siempre iban a salir así |

## Y tres controles que estaban en pantalla sin comunicar nada

- **El turno, invisible.** En un juego por turnos, lo único que cambiaba al
  cambiar el turno era que los botones de atacar pasaban de grises a activos.
  Quien usa lector de pantalla se enteraba tabulando; quien esperaba no sabía a
  quién; quien jugaba contra la máquina no veía nada. El dato ya venía en el
  canal.
- **El botón del asistente, muerto en nueve vistas.** Ningún JavaScript lo
  escuchaba. CA-03 de HU-CHA-001 ya decía qué hacer mientras el servicio no
  exista.
- **Una tarjeta que decía ser pulsable y no lo era.** `.tarjeta--pulsable`
  sobre un `<article>` sin manejador: cursor de mano en toda la superficie,
  y solo funcionaba un botón pequeño.

## Un defecto de seguridad demostrado

El interceptor HTTP compartido —el que usan **todas** las vistas— pintaba el
mensaje de error del servidor con `innerHTML`:

```js
toast.innerHTML = `<span…>⛔</span> <span>${mensaje}</span>`;
```

La prueba escrita antes del arreglo lo demuestra sin ambigüedad: con un
`detail` que contenga `<img src=x onerror=…>`, el DOM acaba con un elemento
`<img onerror="alert(1)">` de verdad. `mensaje` es el `detail` del problem
detail que devuelve cada servicio.

El mismo patrón estaba en el panel de administración de parámetros, donde
`parametro.opciones` —valor del servicio— se interpolaba sin escapar dentro de
`<option value="…">`.

## Cifras

| | Antes (5fc87d8) | Ahora |
|---|---|---|
| CSS local en `src/` | 21 archivos · 5.562 líneas | **15 archivos · 4.247 líneas** |
| Vistas que cargan el kit **entero** | 23 de 31 | **31 de 31** |
| Capa `comun/ui` | 10 módulos · 993 líneas | **13 módulos · 1.440 líneas** |
| Pruebas Jest | 860 | **966** |
| Módulos con `innerHTML` | 14 | **8**, todos de otros equipos y listados |
| Tokens del kit copiados en vistas | 56 | **0** |
| Componentes del kit redefinidos | 14 | **0** |
| Variables inexistentes referenciadas | 12 | **0** |

74 archivos tocados: +4.562 / −2.904.

## Lo que impide que vuelva

Tres guardianes, todos con su rojo comprobado antes del verde:

| Prueba | Qué falla |
|---|---|
| `comun/tokens-sin-sombra.test.js` | Una hoja de vista redeclara un token del kit · redefine una clase del kit · usa una `var(--x)` que no existe en ninguna parte |
| `comun/marcado-sin-plantillas.test.js` | Aparece un `innerHTML` nuevo, **o** uno de los pendientes se arregla y nadie borra su línea |
| `comun/interceptors/aviso-flotante.test.js` | El texto del servidor vuelve a pintarse como marcado |

El segundo es un trinquete, no una barrida: los ocho módulos que siguen usando
`innerHTML` están listados uno a uno con su dueño. Ocho son de los otros dos
equipos —incluido `publicar-subasta.js`, en la lista de módulos protegidos de
HU-SUB-001— y migrarlos desde fuera de su historia sería lo que el Charter
prohíbe.

## Recorrido de los siete bloques

| Bloque | PR | Qué dejó |
|---|---|---|
| PR-UX-1 | #590 | Auditoría de 31 vistas + capa `comun/ui` (10 módulos, 53 pruebas) |
| PR-UX-2 | #591 | Entrada al juego, home del jugador, Mi Cuenta en 4 pestañas (#567), historial de créditos (#569, ms-finanzas 1.3.0). −1.034 líneas de CSS local |
| PR-UX-3 | #592 | Indicador de turno vivo, asistente con comportamiento real |
| PR-UX-4 | #592 | El árbol del torneo usa el componente `Encuentro` del kit, por rondas |
| PR-UX-5 | #594 | Fin de las copias de tokens: vuelve el alto contraste a 8 vistas |
| PR-UX-6 | #595 | Un solo componente por concepto; `comun/paginacion.css` retirado |
| PR-UX-7 | #596 | El marcado se construye con nodos en plataforma |
| PR-UX-8 | este | El interceptor compartido y el cierre |

## Lo que queda abierto

- **#593** — registrar equipo de torneo pide el UUID del compañero, no su
  apodo. Necesita un endpoint de ms-identidad y una decisión del PO (si el
  compañero debe aceptar la invitación).
- **Los ocho `innerHTML`** de grupo-2 y grupo-4, listados en el guardián.
- **El vocabulario paralelo**: `pujas.css` (182 clases), `vitrina.css`,
  `tienda.css` y `tema-cuentas.css` nombran con otras palabras cosas que el
  kit ya tiene (`--texto-secundario` frente a `--texto-2`). No tapan nada y no
  son un fallo de accesibilidad, pero son dos idiomas para lo mismo. Se irán
  resolviendo en los bloques que toquen cada vista, con su dueño.
- **Comprobación visual en dev.** Los cambios de comportamiento están
  afirmados sobre el DOM en las pruebas; los de aspecto —sobre todo añadir
  `base.css` a ocho vistas que se maquetaron sin él— quedan pendientes de
  mirarlos desplegados.

## Lo que este bloque NO hizo, a propósito

- No migró a ningún framework ni abrió un segundo sistema de diseño.
- No bajó ninguna compuerta de calidad ni añadió excepciones a las 12 reglas
  de plataforma.
- No tocó `comun/cabecera-app.js`, que está protegido por la auditoría
  HU-SUB-001, aunque habría sido el sitio más corto para montar el asistente.
- No rediseñó la tarjeta del listado de salas: el contrato documenta
  explícitamente que tiene dos propiedades porque así está en el conjunto
  14:33 del Figma. Cambiarla sería rediseñar la fuente.
- No implementó el chatbot (grupo-4, Sprint 3) ni el buscador de apodos.
