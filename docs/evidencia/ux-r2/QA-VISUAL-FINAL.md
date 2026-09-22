# QA visual final — cierre de UX-R2

**Fecha:** 22 de septiembre de 2026 · **Rama base:** `develop` tras UX-R2.10
**Medición:** `tests/visual/` — 31 vistas × 5 anchuras = **155 combinaciones**
**Informe crudo:** `docs/evidencia/ux-r2/INFORME.md` · `informe.json`
(las capturas van como artefacto de CI, 14 días; no se versionan)

---

## 1 · El resultado, en una línea

**0 de 155 combinaciones con hallazgos.**

| Motivo medido | Inicio de UX-R2 | Cierre |
|---|---:|---:|
| Contraste AA fallido | 35 | **0** |
| Cabecera en más de dos filas | 48 | **0** |
| Objetivo táctil por debajo de 44 px | 327 | **0** |
| Desbordamiento horizontal | 6 | **0** |
| Elemento fuera del viewport | 9 | **0** |
| Error técnico visible al jugador | 5 | **0** |
| Texto cortado sin elipsis | 0 | **0** |
| Clases CSS inexistentes | 5 | **0** |
| **Combinaciones con hallazgos** | **57** | **0** |

Las cinco anchuras son 1440, 1280, 1024, 768 y 375. El arné se ejecuta
en el modo **sin-servicios**: cada vista se mide en su estado degradado, que
es el más exigente — si una pantalla se sostiene sin backend, se sostiene.

---

## 2 · Las 31 vistas

Criterios de la clasificación:

- **PASA** — sin hallazgos del arné en las cinco anchuras, la jerarquía se
  lee, los estados de RNF-USA-003 están, y el shell es el común.
- **PASA CON DEUDA MENOR** — lo anterior, y además una deuda **anotada** que
  no afecta a la interacción primaria ni a un criterio de aceptación.
- **REQUIERE CORRECCIÓN** — cualquier hallazgo del arné, o una jerarquía que
  no se sostiene. **Ninguna vista queda aquí.**

| # | Vista | 1440 | 1280 | 1024 | 768 | 375 | A11y | Estados | Resultado |
|---:|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|---|
| 1 | login | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | PASA |
| 2 | registro | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | PASA |
| 3 | restablecer-solicitar | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | PASA |
| 4 | restablecer-confirmar | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | PASA |
| 5 | home | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E·D | PASA |
| 6 | perfil | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·E | PASA |
| 7 | historial-transacciones | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA CON DEUDA MENOR |
| 8 | mis-cofres | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA |
| 9 | batallas | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E·D | PASA |
| 10 | crear-sala | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·E | PASA CON DEUDA MENOR |
| 11 | sala-batalla | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·E·D | PASA CON DEUDA MENOR |
| 12 | validacion-heroe | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·E | PASA |
| 13 | chat | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA |
| 14 | inventario | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA |
| 15 | productos | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA |
| 16 | subastas | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA |
| 17 | pujas | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA CON DEUDA MENOR |
| 18 | publicar-subasta | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA |
| 19 | tienda | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA |
| 20 | torneos | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E·D | PASA CON DEUDA MENOR |
| 21 | publicar-comentario | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA |
| 22 | notificaciones | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E·D | PASA |
| 23 | mis-sanciones | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA |
| 24 | gestion-usuarios | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA |
| 25 | crear-cuenta-admin | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·E | PASA |
| 26 | auditoria | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA |
| 27 | lista-negra-admin | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA CON DEUDA MENOR |
| 28 | sanciones-admin | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA |
| 29 | parametros-admin | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E | PASA |
| 30 | panel-metricas | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E·D | PASA |
| 31 | tablero-tecnico | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | C·V·E·D | PASA |

`C` carga · `V` vacío · `E` error · `D` degradado · `n/a` la vista no
consulta nada que pueda faltar.

**PASA: 25 · PASA CON DEUDA MENOR: 6 · REQUIERE CORRECCIÓN: 0.**

---

## 3 · Las seis deudas menores, una por una

Ninguna afecta a la interacción primaria de su vista ni a un criterio de
aceptación abierto.

### 7 · historial-transacciones
La tabla se desplaza a lo ancho dentro de `.tabla-envoltorio` en móvil. Es
la solución correcta para ocho columnas en 375 px, pero una tabla que se
desplaza siempre se lee peor que una lista de tarjetas. **No se rehace**:
el patrón se repite en `auditoria`, `gestion-usuarios` y `sanciones-admin`,
así que convertirlo es un componente del kit, no un parche por vista.

### 10 · crear-sala
La apuesta de créditos aparece deshabilitada con «Las apuestas todavía no
están disponibles». Es honesto y está probado, pero el control deshabilitado
ocupa el sitio de algo que no existe. Depende de una decisión del PO sobre
cuándo se abre la apuesta.

### 11 · sala-batalla
La vista lleva su lógica de arranque en un `<script type="module">` dentro del
`.html`, no en un `.js` aparte — es la que más JavaScript embebido tiene de
las 31. Funciona y está probada por `sala-batalla.test.js`, pero rompe la
convención «un `.html` y un `.js` por vista» de CLAUDE.md.

(CA-04 **sí** quedó cumplido en UX-R2.10; ver §5.)

### 17 · pujas
`pujas.js` son 2.297 líneas de plantillas de cadena con `innerHTML`.
**Saneado** en cada interpolación desde UX-R2.8c, con guardián y pruebas de
carga real — no es un agujero. Es deuda de **estructura**: la vista debería
construir nodos. Reescribirla ahora, en la pantalla que mueve créditos, es
más riesgo que beneficio.

### 20 · torneos
**D-24** (premios y transmisión) sigue pendiente del Product Owner. El vacío
y el error ya tienen intención y salida; lo que falta es comportamiento que
nadie ha decidido todavía. No se inventa.

### 27 · lista-negra-admin
La vista lleva su CSS en un `<style>` en línea, no en una hoja aparte. Es la
única de las 31 así. Funciona y ya no desborda (UX-R2.9), pero rompe la
convención «un `.html` y un `.css` por vista» de CLAUDE.md.

---

## 4 · Revisión manual: la promesa del rediseño

No basta con que los detectores estén en cero. Estas son las preguntas de
experiencia, contestadas mirando las capturas.

| Pregunta | Respuesta |
|---|---|
| **Home** — ¿parece un hub del jugador? | Sí. Jerarquía JUGAR → identidad → héroe equipado → créditos → competición/mercado → avisos, con el CTA dominando (UX-R2.6). |
| **Batallas** — ¿listar → crear → lobby → combatir → resultado es un mismo modo de juego? | Sí. Mismo shell, mismos componentes de juego, misma tipografía; el paso al combate ya no cambia de idioma visual. |
| **Combate** — ¿es el pico visual? | Sí. HUD, campo con puestos, barras de vida con umbral 60/40, indicador de turno vivo, panel de desenlace con `--t-display-tam`. Es la única pantalla que usa el tamaño display. |
| **Colección** — ¿parece colección, no CRUD? | Sí desde UX-R2.5: retrato con marco, icono por tipo, parte de armadura, y el equipamiento sobre `ranura` real. |
| **Torneos** — ¿el vacío tiene intención? | Sí: dice que no hay torneo abierto, explica el formato por temporadas y ofrece jugar una sala. **No inventa una fecha** — hay prueba de que no aparece ningún «en N días». |
| **Mercado** — ¿coherente con el backend degradado? | Sí. Un servicio caído dice que es el servicio, con reintento que reintenta de verdad; nunca se presenta como catálogo vacío. |
| **Cuenta** — ¿parte del mismo producto? | Sí. Cuatro pestañas dentro del shell común, con la cabecera y la paleta de todos. |
| **Administración** — ¿más densa sin ser otra aplicación? | Sí. Tablas densas y filtros, pero misma cabecera, mismos botones, mismos estados y mismo lenguaje de error. |

---

## 5 · HU-JUE-017 (#494), criterio por criterio

| Criterio | Estado | Evidencia |
|---|---|---|
| CA-01 · el campo de batalla ocupa la pantalla | **CUMPLE** | Medido: 84,4 % de alto y 97,6 % de ancho a 1360×768; 89,2 % / 98,3 % a 1920×1080 (`tests/visual/medir-campo.mjs`). |
| CA-02 · participantes distribuidos en dos bandos | **CUMPLE** | `campo.js` reparte por bando con carriles independientes; prueba de unicidad dentro de cada lado. |
| CA-03 · estado de cada participante visible | **CUMPLE** | Barra de vida con umbral de color y valor numérico; desde UX-R2.10 además acusa el daño con su cifra. |
| CA-04 · vista de alto impacto al iniciar (presentación de los héroes) y al terminar | **CUMPLE** | El final ya estaba (`panelDeResultado` con desenlace y reparto). El inicio se implementó en UX-R2.10 con **la señal real**: `sala.partida.iniciada` trae `participantes`, `ordenDeTurnos` y `turnoActual`, o sea el reparto completo y quién abre. La presentación se dispara con ese aviso y se cierra al pulsar, con Escape, o con el primer aviso del canal — **sin ningún temporizador**. 16 pruebas. |
| CA-05 · se adapta a pantallas menores | **CUMPLE** | Media query propia del combate; 0 hallazgos en 1024, 768 y 375. |
| CA-06 · desenlace claro | **CUMPLE** | `panelDeResultado` con `role="status"`, victoria/derrota en texto, créditos cuando el libro responde. |

**6 de 6 — HU-JUE-017 se puede cerrar.**

Nota sobre CA-04: durante UX-R2.3 se dejó pendiente con el motivo «hace falta
una señal del servidor que no existe». Al revisarlo criterio por criterio en
el cierre resultó que **la señal sí existía**: `sala.partida.iniciada` del
contrato `salas-partidas.yaml`, que el E2E ya ejercita jugando una partida
real. El diagnóstico anterior era incorrecto, no el criterio. Se implementó
sobre esa señal, y la presentación **no usa ningún tiempo fijo**: se cierra
con una acción de la persona o con el siguiente evento del canal.

---

## 6 · Accesibilidad

| Comprobación | Resultado |
|---|---|
| Contraste AA (4,5:1 texto, 3:1 controles) | 0 fallos en 155 combinaciones |
| Objetivo táctil ≥ 44×44 (WCAG 2.5.5) | 0 por debajo |
| Foco visible (RNF-ACC-002) | **Defecto corregido en UX-R2.8d**: 7 reglas del kit usaban una ficha de *sombra* como color de `outline` → declaración inválida → el foco no se dibujaba en casillas, radios, desplegables, estrellas, ranuras, acciones de combate y zona de carga. Guardián añadido. |
| Trampa de teclado (WCAG 2.1.2) | Escape cierra el menú de navegación y el de cuenta, y devuelve el foco |
| `aria-current` en la navegación | Sí, en las 31 vistas |
| `aria-live` en cambios de estado | Turno, desenlace, avisos y **las cifras de daño/curación/puja** (UX-R2.10) |
| Etiquetas de formulario | `campo.js` las genera con `for`/`id`; el tope automático dejó de depender del placeholder |
| Errores asociados al campo | `campo__error` con `aria-describedby` |
| `prefers-reduced-motion` | Los tokens de duración valen `0ms`; **ninguna información vive solo en el movimiento** — probado: la cifra es texto con `aria-live` y la urgencia del contador es color y peso, no parpadeo |
| Alto contraste (`prefers-contrast: more`) | Recuperado en 6 vistas más en UX-R2.10, al quitar los alias con hex escrito a mano |

**axe-core no se ejecutó**: no está en el repositorio y añadirlo es una
dependencia nueva de desarrollo. Las ocho comprobaciones de arriba se miden
con el arné propio, que es determinista y ya está en CI. Queda anotado como
mejora del arné, no como bloqueo.

---

## 7 · Lo que se decidió NO hacer

| Qué | Por qué |
|---|---|
| Reescribir `pujas.js` a nodos | 2.297 líneas en la vista que mueve créditos. Saneado y con guardián; la estructura es deuda declarada. |
| Rehacer el árbol de doble eliminación | Funciona, está probado y agrupado por ronda (PR-UX-4, #585). No se rehace lo que está bien. |
| Rediseñar la tarjeta del lobby | Contradice una decisión documentada de Figma (HU-SAL-002). Se revirtió a propósito. |
| Inventar premios y transmisión de torneo | **D-24**, pendiente del PO. |
| Resolver apodo → uid (#593) | Es una decisión de producto —un directorio público de identificadores toca privacidad—, no de infraestructura. |
| Añadir axe-core | Dependencia nueva; el arné propio ya cubre lo medible sin coste. |

---

## 8 · Regresión funcional

| Suite | Resultado | Nota |
|---|---|---|
| **Jest** (unitarias + guardianes) | **1120 / 1120** | Era 966 al empezar UX-R2 |
| **ESLint** | verde sobre todo `src/` | |
| **Prettier** | verde sobre todo `src/` | |
| **Playwright visual** (arné) | **156 / 156**, 0 hallazgos | |
| **Playwright aceptación** (inventario, Grupo 2) | 2 pasan / 44 fallan | **No es regresión.** La línea base documentada en **#581** es 45 de 46 fallando desde antes del rediseño: las pruebas abren `inventario.html` **sin sesión** y el shell las manda al login. Hoy falla **una menos**. CI las corre con `continue-on-error`. |
| **E2E completo** | corre en `.github/workflows/e2e.yml` | Banco de 10 servicios reales; no se ejecuta en local por capacidad |

### Guardianes activos al cierre

| Guardián | Qué impide que vuelva |
|---|---|
| `sin-innerhtml.test.js` | Un `innerHTML` con interpolación sin revisar |
| `escapar.test.js` | Que `esc()` deje pasar un carácter que saca un valor de su sitio |
| `clases-del-kit.test.js` | Clases fantasma · que el kit pierda una clase de maquetación · la tarjeta-enlace subrayada · **una ficha de sombra usada como color de `outline`** |
| `tokens-sin-sombra.test.js` | Tokens del kit redeclarados · clases del kit redefinidas · **`var(--x)` sin respaldo que no resuelva en el kit ni en su propia hoja** |
| `publicar-subasta.test.js` | Que un módulo protegido cambie sin declararlo |
| `auditoria-visual.spec.js` | Las 8 medidas, en 155 combinaciones, y que el kit no cargue |
| `acuse.test.js` | Animar algo que no cambió · que la información viva solo en el movimiento |
