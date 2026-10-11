# Nexus Motion System

Cómo se mueve The Nexus Battles VI y por qué. Es la referencia viva del
movimiento: el informe que lo decidió está al lado
(`ANIMATION-RESEARCH.md`, MOTION-0) y el código en `src/comun/ui/motion.js`.

## Principios

1. **Una animación cuenta un cambio de estado.** Responde a: ¿qué ocurrió?,
   ¿quién actuó?, ¿a quién afectó?, ¿qué cambió?, ¿dónde debe mirar el
   jugador? Si no responde a ninguna, no se añade (hereda la regla de
   UX-R2.10, `acuse.js`).
2. **Nunca es la única información.** El dato (cifra, texto del registro,
   `aria-live`, color + icono) se escribe antes de pedir el movimiento. Con
   `prefers-reduced-motion` puesto, el dato está y el movimiento no.
3. **Solo transform, opacity y filter.** Nada de width/height/left/top/margin
   en un `@keyframes` ni en un cuadro de WAAPI (`motion-tokens.test.js` y
   `motion.js` lo rechazan). Se componen fuera del hilo principal y no
   empujan lo de alrededor.
4. **Nada permanente salvo lo decidido.** Un indicador de carga, el halo del
   asistente y la respiración de un héroe. Todo lo demás ocurre una vez.
   `INFINITAS_PERMITIDAS` en `motion-tokens.test.js` es la lista.
5. **1 acción importante → 1 foco visual dominante.** No se sacude la página:
   se sacude lo golpeado, dos píxeles.
6. **Sin biblioteca.** CSS para lo que CSS hace bien (hover, foco, entradas,
   idle) y la Web Animations API nativa para las secuencias. El POC de
   MOTION-0 midió Motion y Anime.js: 5–6× más hilo principal por la misma
   coreografía, 14–48 KB y una dependencia JS que el frontend no tiene.

## Tokens (tokens.css)

| Nombre | Alias de | Valor | Para |
|---|---|---|---|
| `--motion-instant` | `--duracion-instantaneo` | 80 ms | respuesta al pulsar |
| `--motion-fast` | `--duracion-rapido` | 120 ms | hover, foco, salidas |
| `--motion-normal` | `--duracion-normal` | 200 ms | entradas, pulsos, barras |
| `--motion-slow` | `--duracion-pausado` | 320 ms | golpe, curación, diálogo |
| `--motion-turn` | `--duracion-turno` | 600 ms | crítico, turno, desenlace |
| `--ease-standard` | `--curva-estandar` | `cubic-bezier(0.4, 0, 0.2, 1)` | lo que cambia en su sitio |
| `--ease-enter` | `--curva-salida` | `cubic-bezier(0, 0, 0.2, 1)` | lo que entra (frena) |
| `--ease-exit` | `--curva-entrada` | `cubic-bezier(0.4, 0, 1, 1)` | lo que sale (acelera) |
| `--ease-impact` | `--curva-impacto` | `cubic-bezier(0.2, 0.9, 0.3, 1)` | el golpe: dispara y frena en seco |
| `--ease-spring` | `--curva-impulso` | `cubic-bezier(0.34, 1.56, 0.64, 1)` | encaje, recompensa |

Los valores viven en `--duracion-*` / `--curva-*`; con
`prefers-reduced-motion: reduce` valen `0ms` todos a la vez. `motion.js`
lee `--motion-*` del CSS y, sin hoja (jsdom), usa los mismos números.

## Modos

- **Movimiento reducido** (`prefers-reduced-motion: reduce`): tokens a cero,
  toda animación CSS en un cuadro, `motion.js` no arranca nada por WAAPI.
  Queda: estado, texto, color + icono, cifra legible.
- **Modo determinista** (`<html data-test-motion="off">`): lo mismo, para
  capturas y pruebas. El laboratorio lo pone con `tests/visual/movimiento.js`
  (`sinMovimiento(contexto)`). No sustituye a probar el movimiento: eso se
  hace con pruebas de comportamiento (el actor pasa a su estado, el
  objetivo al suyo, la cifra aparece, el HUD acaba en el valor del
  servidor), nunca con milisegundos exactos.

## El módulo (`src/comun/ui/motion.js`)

```js
import { motion, secuencia, animar, permitido, transicionDePagina } from '../../comun/ui/motion.js';

motion.enter(el)                     // aparece: fundido + 6 px
motion.exit(el)                      // se va (quien llama lo retira al terminar)
motion.hover(el) · motion.press(el)  // tarjetas y fichas que no son botones
motion.pulse(el)                     // un pulso, uno solo (insignia 0→1, turno)
motion.damage(el, { direccion })     // flash + retroceso 14 px, sumado al idle
motion.heal(el) · motion.buff(el) · motion.debuff(el) · motion.shield(el)
motion.critical(el, { direccion })   // más fuerte + sacudida local de 2 px
motion.reward(el)                    // encaje con rebote y brillo
motion.float(cifra)                  // el texto flotante sube y se retira

await secuencia([
  () => animar(actor, [...], { duracion: 'fast', curva: 'exit' }),   // anticipa
  () => animar(actor, [...], { duracion: 150, curva: 'impact' }),    // lunge
  [() => motion.damage(objetivo, { direccion }), () => motion.float(cifra)],
  60,                                                                 // hit-stop
  () => animar(actor, [...], { duracion: 'normal', curva: 'spring' }),// regresa
]);
```

`permitido()` se consulta en cada llamada: la preferencia del sistema puede
cambiar con la página abierta. `transicionDePagina(fn)` usa
`document.startViewTransition` si existe (dentro de una misma página) y
gestiona sus promesas.

**Entre documentos no hay View Transition.** MOTION-1 declaró
`@view-transition { navigation: auto }` en `base.css` y MOTION-4 lo retiró:
cuando Chrome salta una transición entre documentos —navegación rápida,
pestaña oculta, página que tarda en pintar— rechaza las promesas del
`ViewTransition` y queda como error no capturado («Transition was skipped»)
en cada página; la prueba del profesor (`tests/e2e`) trata todo error de
página como fallo, y un fundido de 200 ms no vale un error de consola por
navegación. Si un día se quiere, hay que gestionar `pageswap`/`pagereveal`
en un módulo que carguen las 42 vistas, que hoy no existe.

## Batalla (MOTION-2)

`plataforma/salas-partidas/coreografia-de-combate.js` dibuja cada aviso del
canal con el sistema; `comun/ui/juego/fx.js` dice QUÉ fue (semántica) sin
calcular nada: el tipo de acción, la categoría del golpe, si fue épica y los
efectos vienen en el aviso; el prototipo del ejecutor, en los participantes.

| Clave FX | Cuándo | Qué se ve |
|---|---|---|
| PHYSICAL_HIT | ataque de Guerrero Tanque, Guerrero Armas, Pícaro Machete | anticipa 120 → lunge 44 px → flash + recoil 14 px + cifra (espada) → hit-stop 60 → regreso |
| MAGIC_FIRE / MAGIC_ICE | ataque de Mago Fuego / Mago Hielo | lo mismo, con icono de fuego / copo |
| POISON | ataque de Pícaro Veneno; tick de DANO_POR_TURNO | recoil más suave (8 px), gota; el tick no mueve a quien lo causó |
| HEAL | sanación, reanimación, tick de SANACION_POR_TURNO | brillo en quien cura y en quien recibe; «+X VIDA» con cruz |
| SHIELD | DEFENSA (Mano de piedra, Defensa feroz) y efectos de protección | encaje (scale 0,96 → 1), anillo |
| BUFF / DEBUFF | APOYO sobre compañero / rival; efecto nuevo que mejora / perjudica | se eleva y brilla / pierde color un instante |
| EPIC | `esEpica` | halo dorado del actor antes del golpe + `data-epica` |
| CRÍTICO | categoría CAUSAR_DANO_CRITICO | lunge 56 px, hit-stop 90, flash fuerte, sacudida local 2 px, cifra grande con rayo |
| EVADE / RESISTE / ESCAPA / SIN EFECTO | las otras categorías | paso de lado / encaje / nada; la cifra lo dice con escudo o cerrar |

Estados que deja en el DOM (los pinta el CSS también sin movimiento y los
afirman las pruebas): `data-accion="ataca|cura|defiende|apoya"`,
`data-fx`, `data-epica` en quien actúa; `data-golpe="<resultado>"` en quien
recibe; `data-pensando="si"` en la IA mientras dura su pausa;
`data-desenlace="victoria|derrota|empate"` en el campo. El HUD recibe el
valor del servidor al instante; el relleno de la barra lo alcanza con
`transform: scaleX()` y la estela del daño (`estelaDeVida`) se encoge.
VICTORIA: la palabra encaja y 60 partículas DOM+WAAPI, una vez; DERROTA:
el campo pierde color; EMPATE: nada.

## Mundo de juego (MOTION-3)

Microinteracciones con el mismo sistema, una vez cada una y nunca como
única información:

| Dónde | Qué | Cómo |
|---|---|---|
| Misiones | las tarjetas del tablón entran escalonadas (40 ms); EN PROGRESO lleva halo quieto; hover de 2 px | CSS (`misiones.css`) con los tokens |
| Torneos | el hito actual late dos veces al aparecer y se queda con su halo; el camino del jugador ya va resaltado (`.encuentro--mio`) | CSS (`torneos.css`) |
| Inventario / héroes | la carta del héroe se acerca 2 px al pasar; las estadísticas de la carta propia entran escalonadas; equipar sigue acusando la ranura (UX-R2.10) | CSS del kit |
| Mis cofres | los cofres entran escalonados | CSS (`mis-cofres.css`) |
| Tienda | la insignia del carrito pulsa una vez al añadir; la compra entregada entra y el título encaja (`reward`) | `motion.pulse`, `motion.enter`, `motion.reward` |
| Subastas | el importe de la subasta que acaba de cambiar se acusa una vez (`acuse--puja`); la cuenta atrás urgente y «Ir ahora» laten tres veces, no para siempre | `acusar` + CSS (`pujas.css`) |
| Notificaciones | la campana pulsa una vez cuando suben las no leídas, nunca al bajar | `motion.pulse` |
| Navegación | sin fundido entre documentos (retirado en MOTION-4, ver arriba); `transicionDePagina()` para cambios dentro de una página | `motion.js` |

Con movimiento reducido todo esto queda quieto: los estados (halo, sello,
color + icono, número) siguen diciéndolo.

## Cómo añadir una animación

1. Escribe primero el dato (texto, `aria-live`, color + icono).
2. Si es hover/foco/entrada de un componente del kit: CSS en
   `componentes.css`, con los tokens, dentro de
   `@media (prefers-reduced-motion: no-preference)` si desplaza algo.
3. Si es una secuencia o nace con un aviso del canal: un verbo de
   `motion.*` o `secuencia([...])`. Si falta el verbo, se añade a `motion.js`
   con su prueba, no se llama a `element.animate` desde la vista.
4. Solo transform / opacity / filter. Nada infinito.
5. Comprueba el comportamiento en Jest (con `el.animate` de mentira) y, si
   se ve en una captura, que el laboratorio sigue estable con
   `data-test-motion="off"`.

## Presupuesto de rendimiento

60 fps en escritorio y ≥ 50 en móvil medio (CPU ×4) en batalla; 0 long
tasks por golpe; CLS ≈ 0; 0 KB de dependencias; lo de batalla se importa en
`sala-batalla` y no en el login ni en Inicio.

Lo mide `tests/visual/motion-rendimiento.spec.js` (MOTION-4, job `motion`
del laboratorio) sobre `combate-seis` con una ráfaga de cuatro golpes a
CPU ×4, y deja las cifras en `docs/evidencia/motion/rendimiento.md`.
Compuerta: CLS < 0,02 · long tasks ≤ 3 · p95 ≤ 40 ms · cuadros > 33 ms
≤ 10 %. Medido al cerrar MOTION-4 (Chromium headless, 11-oct-2026): 55 fps
de media, p50 16,7 ms, p95 16,8–33,3 ms, 0–2 long tasks por ráfaga (≤ 115 ms en total), CLS 0, 2–4 % de
cuadros por encima de 33 ms, 9,5 MB de heap. El login y la portada no
cargan ningún módulo de batalla (`coreografia-de-combate.js`, `fx.js`).
