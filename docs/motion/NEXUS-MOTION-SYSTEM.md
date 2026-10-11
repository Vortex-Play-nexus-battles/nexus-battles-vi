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
`document.startViewTransition` si existe; entre documentos, `base.css`
declara `@view-transition { navigation: auto }` (Chrome 126, Edge 126,
Safari 18.2; en Firefox la navegación es la de siempre).

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
`sala-batalla` y no en el login ni en Inicio. Se mide con el banco de
`tests/visual` (MOTION-4) sobre el escenario `combate-seis`.
