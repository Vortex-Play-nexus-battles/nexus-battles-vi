## ANIMATION RESEARCH — NEXUS BATTLES VI

Fecha: 11-oct-2026 · Base: `develop` @ `0afb570c` (los 11 fondos, 26 pantallas con escena, admin sin fondos, ~3 650 Jest, laboratorio visual, E2E, AWS DEV). Nada de lo que sigue toca lógica de combate ni vuelve al frontend autoridad de vida/poder/daño: todo se dibuja a partir de lo que el servidor ya manda por el canal (`partida.accion.resuelta`, `partida.turno.cambiado`, `partida.finalizada`, `sala.partida.iniciada`).

### 1. Estado actual

**Arquitectura.** HTML5 + CSS3 + ES2022 sin framework, sin bundler y sin paso de compilación; una vista = un `.html` + un `.js`. **El frontend no tiene ninguna dependencia JavaScript de tiempo de ejecución** (hasta el cliente STOMP está escrito a mano en `src/comun/transporte-stomp.js`: «agregar una librería es decisión de equipo»). La CSP del borde (`borde-dev.conf`) es `script-src 'self' 'nonce-…'`: nada se carga de un CDN; una biblioteca tendría que vendorizarse en el repositorio. Jest corre sobre jsdom, que **no implementa `Element.animate()`**.

**Lo que ya se mueve (inventario real).** `tokens.css` ya define un sistema de movimiento: `--duracion-instantaneo 80 / rapido 120 / normal 200 / pausado 320 / turno 600 ms`, `--curva-salida/entrada/estandar/impulso` y `--transicion`; bajo `prefers-reduced-motion: reduce` las duraciones valen `0ms` y una regla global deja toda animación CSS en `0.01ms` (un solo ciclo). `componentes.css` tiene 17 `@keyframes` y 26 transiciones; las vistas, 8 keyframes más (pujas ×4, tema-cuentas ×3, torneos ×1).

| Dónde | Hoy | Observación |
|---|---|---|
| Botones (kit) | hover `translateY(-1px) scale(1.02)`, pressed `scale(0.98)`, luz de borde 900 ms en primarios | bien; se conserva |
| Diálogo, menú de cabecera, turno, resultado | entran con fade + 6–12 px (`entrar-dialogo`, `entrar-desenlace`, `aparecer-resultado`) | `.panel-resultado` tiene **dos** animaciones de entrada declaradas (l. 5706 y 5835): gana la segunda; se unifica |
| Acuse de estado (`comun/ui/acuse.js`, UX-R2.10) | `acuse--dano` (sacudida 2 px), `--curacion` (brillo), `--equipar` (encaje), `--puja`; cifra `.acuse__cifra` con `role=status` | es el embrión del FloatingCombatText; su regla («el texto es el dato, la animación el adorno») se mantiene |
| Campo de combate (`campo.js`, `.campo__puesto`) | **estático**: el héroe no respira, no anticipa, no se lanza, no recibe el golpe | P0 |
| Cifra del golpe (`impactoEnCampo`, `.impacto`, `impacto-sube` 1,6 s) | sube y se desvanece; «−4 VIDA», «CRÍTICO», «Evade» | buena base; falta nacer **en el instante del impacto** y variar por semántica |
| Anuncio central (`combate__anuncio`, 2 s) | subtítulo grande sobre la barra de mando | se conserva |
| Turno | `latido-turno` **infinito** en el punto de «Es tu turno»; `data-turno` en barra y puesto | el directive pide halo y pulso único, no infinito |
| Cuenta atrás (`presentacion.js`) | 5…1 con `cuenta-atras-a/b` (scale 1.5→1, 1 s) y «¡COMBATE!» 800 ms; retiene el canal (`retenerHasta`) | falta desenfoque→nitidez y flash final |
| IA pensando | solo texto «Turno de la IA · nombre»; la pausa perceptual es `PAUSA_ENTRE_ACCIONES_MS = 2200` en `ritmo-de-combate.js` | sin señal visual en el campo |
| Barras de vida/poder | `width` con transición de 120 ms; valor numérico inmediato (RF-JUE-009) | pasa a `transform: scaleX()` + estela del daño; el texto sigue siendo la verdad del servidor al instante |
| Victoria/derrota/empate | panel entra 320 ms; icono trofeo/escudo; créditos fila a fila | falta celebración contenida / sobriedad |
| Subastas (`pujas.css`) | `pulso-urgente` y `latido` **infinitos**, toast, spinner | pulsos infinitos → acuses únicos |
| Torneos | `torneo-hito-actual` infinito 2,4 s (box-shadow) | pasa a halo estático + pulso único al cambiar |
| Misiones | banner rotativo en JS (pausa con reduce); 1 transición | sin movimiento en tarjetas ni progreso |
| Inventario | acuse `equipar` (200 ms) tras el OK del backend | se amplía a morph de ranura y profundidad del héroe |
| Portal (`tema-cuentas.css`) | `flotar1/flotar2`: dos círculos de 480 px con `blur(60px)` moviéndose **para siempre** (14–16 s) sobre `.pagina-auth` | **código muerto**: ninguna vista usa `.pagina-auth`; se retira |
| Chatbot | halo que gira 8 s infinito (sutil) | se conserva: es ambiental y pequeño |

**Pruebas que vigilan el movimiento.** `acuse.test.js`, `presentacion.test.js`, `combate.test.js` (anuncio, impacto), `clases-del-kit.test.js` (orden de hojas), `tokens-sin-sombra.test.js` (nada de colores fuera del kit), laboratorio visual (`campo-de-combate.spec.js` > 80 %, `axe`, contraste), E2E con partida real (`sala-de-batalla.e2e.spec.js`, `combate-sin-autodano.e2e.spec.js`).

### 2. Qué partes se sienten estáticas

Matriz de las 26 pantallas del jugador (`matriz-acceso.js`, armazón `publico`/`jugador`). Cada animación propuesta responde a: ¿qué ocurrió? ¿quién actuó? ¿a quién afectó? ¿qué cambió? ¿dónde debe mirar el jugador?

| Pantalla | Movimiento actual | Oportunidad | Tipo | Prioridad | Técnica |
|---|---|---|---|---|---|
| sala-batalla | presentación, cuenta atrás, cifra, anuncio, panel final; héroes inmóviles | idle (respiración 1–3 px), anticipa→lunge→impacto→recoil→regreso, flash localizado, FloatingCombatText por semántica, hit-stop 60 ms, curación/buff/debuff/escudo/crítico/esquiva, halo de turno único, IA pensando, cuenta atrás con desenfoque→nitidez, VICTORIA/DERROTA/EMPATE | GAME FEEDBACK + CINEMÁTICA CORTA | **P0** | CSS (idle, halo) + WAAPI (secuencias) |
| misiones | banner rotativo | entrada escalonada de MissionCard (una vez), EN PROGRESO, progreso que se llena al valor del servidor, recompensa reclamada (reward), jefe final con halo, MissionStepper fade/lateral | GAME FEEDBACK | P1 | CSS + WAAPI |
| torneos | hito infinito | resaltar el camino del jugador en el árbol, encuentro resuelto (acuse), hito actual con halo estático + pulso único | GAME FEEDBACK | P1 | CSS |
| inventario | acuse equipar | morph/FLIP de la ranura tras el OK, profundidad del héroe en hover, stagger de estadísticas al seleccionar | GAME FEEDBACK | P1 | WAAPI (FLIP) + CSS |
| mis-cofres | — | recompensa revelada (reward) | GAME FEEDBACK | P1 | WAAPI |
| tienda | — | pulso al añadir al carrito + badge 0→1, compra → reward → enlace a inventario | MICROINTERACCIÓN | P1 | CSS + WAAPI |
| subastas (vitrina) | cuenta atrás por color | precio nuevo resaltado (highlight único), VT vitrina→pujas | MICROINTERACCIÓN | P1 | CSS + VT |
| pujas | pulsos infinitos, toast, spinner | precio nuevo + contador, «te superaron» (aviso), ganada (success), sin pulsos infinitos; nada mueve la tabla entera | GAME FEEDBACK | P1 | CSS |
| notificaciones | — | badge 0→1 con un solo pulso, notificación nueva entra con fade | MICROINTERACCIÓN | P1 | CSS |
| home (index) | hover kit | entrada escalonada corta de las tarjetas de módulo (una vez), VT Inicio→módulo | TRANSICIÓN | P2 | CSS + VT |
| batallas | tarjeta bloqueada | sala nueva aparece (fade), cambio de estado (acuse), VT listado→sala | TRANSICIÓN | P2 | CSS + VT |
| portada | hover kit | entrada única del lema y del héroe; nada ambiental permanente | MICROINTERACCIÓN | P2 | CSS |
| login | — | entrada de la tarjeta (fade + 6 px), campo inválido: sacudida 2 px + texto | MICROINTERACCIÓN | P2 | CSS |
| chat | — | mensaje nuevo entra (fade 120 ms), badge de no leídos | MICROINTERACCIÓN | P3 | CSS |
| registro | — | paso a paso con fade | MICROINTERACCIÓN | P3 | CSS |
| verificar-cuenta | — | acuse de éxito (sello) | MICROINTERACCIÓN | P3 | CSS |
| preparando | pasos del onboarding | el paso que pasa a «Listo» se acusa | MICROINTERACCIÓN | P3 | CSS |
| validacion-heroe | — | sello de verificación (acuse) | MICROINTERACCIÓN | P3 | CSS |
| crear-sala | — | acuse al crear + VT a la sala | TRANSICIÓN | P3 | VT |
| publicar-subasta | — | acuse al publicar | MICROINTERACCIÓN | P3 | CSS |
| publicar-comentario | — | acuse al publicar | MICROINTERACCIÓN | P3 | CSS |
| perfil | — | acuse al guardar | SIN MOVIMIENTO NECESARIO | P3 | kit |
| historial-transacciones | — | — | SIN MOVIMIENTO NECESARIO | — | kit |
| mis-sanciones | — | — | SIN MOVIMIENTO NECESARIO | — | kit |
| restablecer-solicitar | — | — | SIN MOVIMIENTO NECESARIO | — | kit |
| restablecer-confirmar | — | — | SIN MOVIMIENTO NECESARIO | — | kit |

Transversal (todas): hover/focus/pressed del kit (ya), menú y diálogo (ya), transición de página con View Transition API como mejora progresiva, badge de la campana.

### 3. Investigación externa

Versiones y licencias leídas hoy del registro npm (`npm view … version license`, 11-oct-2026); tamaños medidos sobre el archivo que habría que vendorizar (`gzip -9`).

| Tecnología | Licencia | Uso | Pros | Contras | Recomendada |
|---|---|---|---|---|---|
| CSS transitions / `@keyframes` | plataforma | hover, focus, pressed, pulsos únicos, fades, entradas, idle | 0 KB; ya es el lenguaje del kit (tokens + 17 keyframes); compositor; reduce-motion resuelto en `tokens.css` | sin secuencias encadenadas con promesa | **Sí (base)** |
| Web Animations API (`Element.animate`) | plataforma (Baseline 2020) | secuencias anticipa→lunge→impacto→recoil→regreso, cifras, confeti contenido, FLIP | 0 KB; `finished` como promesa; `composite: 'add'` (idle + golpe a la vez); acelerada por compositor; cancelable | jsdom no la trae (stub en Jest); **ignora el apagado global de `tokens.css`** → el sistema debe leer `matchMedia` | **Sí (base)** |
| View Transition API | plataforma | Inicio→módulo, listado→detalle, lobby→batalla, batalla→resultado | nativa; sin soporte = navegación normal. **Nota de MOTION-4:** la entre documentos se retiró (Chrome deja «Transition was skipped» como error no capturado al saltarla y la prueba del profesor lo trata como fallo); queda `transicionDePagina()` dentro de una página | mismo documento: Chrome/Edge 111+, Safari 18+, Firefox 144+; entre documentos (`@view-transition`, nuestro caso MPA): «disponibilidad limitada, no Baseline» según MDN al 11-oct-2026 (Chrome/Edge y Safari sí; Firefox no) → progresiva | **Sí (progresiva)** |
| Motion 14.1.0 (motion.dev) | MIT | secuencias, springs | `animate(sequence)`, acelera por WAAPI | sin bundler: 145 KB UMD (48 KB gz) o 55,6 KB / **20,5 KB gz** tras un esbuild que la pila no tiene; POC: +500 ms de script por 20 golpes a 6× frente a 100 ms; sería la primera dependencia JS del frontend | No |
| Anime.js 4.5.0 | MIT | timelines, SVG, stagger | timeline con posiciones absolutas, módulo `waapi` | 118,7 KB / **40,8 KB gz** el ESM publicado (37,9 / 14 KB gz subconjunto con esbuild); motor JS por frame; POC: +400–500 ms script, hitches de 50–67 ms a 6× | No |
| tsParticles 4.4.0 (`@tsparticles/confetti`) | MIT | VICTORIA | listo | 132 KB / **38 KB gz** para 1,5 s de confeti; 60 partículas DOM+WAAPI dieron 0 frames lentos y 0 KB | No |
| canvas-confetti 1.9.4 | ISC | VICTORIA | 25 KB / 6,9 KB gz | long task de 55 ms al cargar; innecesaria | No |
| Lottie (lottie-web 5.13.0) | MIT | iconos animados | — | no hay `.json` propios ni con licencia clara; ~60 KB gz de runtime | No (sin assets) |
| PixiJS 8.22.0 | MIT | BattleRenderer aislado | WebGL | el DOM ya da 60 fps con 6 combatientes y 0 long tasks; duplicaría `campo.js`, el laboratorio y axe | No |
| Phaser 4.2.1 | MIT | motor completo | — | migración fuera de alcance; solo POC futuro si cambia el producto | No |

Fuentes: motion.dev/docs/animate (mini 2,3 KB, hybrid 18 KB con bundler) · github.com/juliangarnier/anime (MIT) · developer.chrome.com/blog/view-transitions-in-2025 · developer.mozilla.org/…/Element/animate (Baseline) · web.dev/articles/animations-guide (transform/opacity en compositor; `will-change` solo puntual) · registro npm.

### 4. Recursos gratuitos encontrados

| Recurso | Licencia | Para qué serviría | Decisión |
|---|---|---|---|
| Kenney · Particle Pack (80 assets, 512×512) y UI Pack — kenney.nl/assets | CC0 («attribution is not required») | sprites de chispas/humo para ÉPICA, si algún día un efecto necesita textura | **no se usa en esta fase**: todo sale de CSS/WAAPI y de los 30 iconos del kit |
| OpenGameArt — opengameart.org | por asset: CC0, CC-BY 3.0/4.0, CC-BY-SA, OGA-BY, GPL | FX puntuales | solo CC0 o CC-BY con registro `nombre · autor · URL · licencia · fecha`; nada ambiguo; **no se usa en esta fase** |
| Referencias de game feel: «Juice it or lose it» (Jonasson & Purho, 2012), «The Art of Screenshake» (Nijman, 2013), eastondev.com «Where Does Game Feel Come From» (2026), rpgplayground.com «Making a juicy game» | — | anticipación–impacto–recuperación, flash, hit-stop, números flotantes, sacudida **localizada** | aplicadas al Battle Motion Language (§8) |

Como no entra ningún asset de terceros, **no hace falta `THIRD_PARTY_ASSETS.md` todavía**; se crea el día que entre el primero con atribución.

### 5. CSS/WAAPI vs Motion vs Anime.js

| Opción | Bundle | Vanilla JS | Timelines | SVG | Rendimiento | Reduced motion | Complejidad | Riesgo |
|---|---|---|---|---|---|---|---|---|
| CSS + WAAPI (+ `motion.js` propio ≈ 200 líneas) | 0 KB extra | nativo | con promesas `finished` + anclas (`delay`) | sí (transform/opacity; `d` no) | compositor; POC a 6×: 100 ms script / 0 hitches | lo hace el sistema (matchMedia + `data-test-motion`) | baja: mismo vocabulario que el kit | mínimo; jsdom requiere stub |
| Motion 14.1.0 | 20,5 KB gz (con esbuild) o 48 KB gz UMD | sí | sí (`at`) | sí | WAAPI por debajo; 500–600 ms script / hitch 50 ms a 6× | no la gestiona | media (API grande) | primera dependencia + paso de build |
| Anime.js 4.5.0 | 14 KB gz (subconjunto con esbuild) o 40,8 KB gz | sí | sí | sí (morph, path) | rAF propio; 400–500 ms script / hitch 67 ms a 6× | no la gestiona | media | primera dependencia + paso de build |

**POC** (scratchpad, Chromium headless 1366×768, 20 golpes, misma coreografía en las tres: anticipa 120 → lunge 150 → impacto con flash + recoil + cifra → hit-stop 60 → regreso 220 ms; CDP `Performance.getMetrics`, `PerformanceObserver` longtask/layout-shift; CPU ×4 y ×6 para emular un móvil medio):

| variante | CPU × | fps | p95 ms | máx ms | frames >33 ms | long tasks | CLS | script ms | layouts | heap MB |
|---|---|---|---|---|---|---|---|---|---|---|
| waapi · 2 comb. | 1 | 60 | 16.8 | 16.8 | 0 | 0 | 0.0008 | 0 | 80 | 2.1 |
| motion · 2 comb. | 1 | 60 | 16.8 | 16.8 | 0 | 0 | 0.0009 | 200 | 40 | 3.1 |
| anime · 2 comb. | 1 | 60 | 16.7 | 16.8 | 0 | 0 | 0.0008 | 100 | 45 | 2.6 |
| waapi · 6 comb. | 6 | 60 | 16.7 | 16.8 | 0 | 0 | 0 | 100 | 81 | 2.1 |
| motion · 6 comb. | 6 | 60 | 16.7 | 49.9 | 1 | 0 | 0 | 600 | 41 | 3.1 |
| anime · 6 comb. | 6 | 60 | 16.7 | 66.7 | 1 | 0 | 0 | 500 | 50 | 2.7 |
| waapi + confeti DOM (60 partículas WAAPI) | 4 | 60 | 16.7 | 16.8 | 0 | 0 | 0.0008 | 100 | 83 | 2.2 |
| waapi + confeti canvas propio (120) | 4 | 60 | 16.7 | 16.8 | 0 | 0 | 0.0008 | 200 | 83 | 1.8 |
| waapi + canvas-confetti | 4 | 60 | 16.7 | 50 | 1 | 1 (55 ms) | 0.0008 | 300 | 84 | 1.9 |
| waapi · reduce | 1 | 60 | 16.8 | 16.8 | 0 | 0 | 0.0008 | 0 | 80 | 2.1 |

Lectura: las tres mantienen 60 fps, pero WAAPI cuesta 5–6× menos hilo principal, no produce hitches y no pesa. Hallazgo importante del caso «reduce»: **WAAPI no obedece la regla `0.01ms` de `tokens.css`** (duró lo mismo), así que el sistema central tiene que leer la preferencia él mismo. Tablas completas y método en `docs/motion/poc/`; el vídeo del POC va adjunto al PR-1.

**Decisión (DECISIÓN TÉCNICA): CSS + WAAPI con un módulo propio `motion.js`, sin biblioteca.** La hipótesis «CSS + WAAPI + (Motion o Anime)» se valida a medias: la parte «+ librería» no aporta nada medible y sí añade la primera dependencia JS, un paso de build que la pila prohíbe y 14–48 KB. Se deja documentado cómo entraría Motion si un día hiciera falta un spring físico o un timeline con scrub (que hoy no se usa).

### 6. ¿Necesitamos PixiJS/Phaser?

No. El campo tiene 2–6 héroes, sin colisiones ni física; el DOM animado por el compositor rinde 60 fps con 6 combatientes a CPU ×6 y 0 long tasks. Un `BattleRenderer` en canvas duplicaría la colocación estable de `campo.js`, dejaría fuera a `axe`, al laboratorio de contraste y a los fondos (`arena.webp` vive en `.combate__campo`), y obligaría a reescribir las pruebas. Phaser queda, como pide la especificación, solo como POC futuro.

### 7. Arquitectura propuesta

```
shared/ui-kit/css/tokens.css      + Nexus Motion System: --motion-instant/fast/normal/slow/turn,
                                    --ease-standard/enter/exit/impact (alias de --duracion-*/--curva-*,
                                    una sola fuente de valores) y [data-test-motion='off'] → 0 ms
shared/ui-kit/css/componentes.css + .motion-* de batalla: idle, halo de turno, flash, hit, estela de la
                                    barra, cifra por semántica, celebración/sobriedad, pensando
frontend/app-web/src/comun/ui/motion.js   ← ÚNICO punto de entrada JS (sobre acuse.js)
   motion.permitido()        lee prefers-reduced-motion + data-test-motion + matchMedia en caliente
   motion.secuencia(pasos)   WAAPI encadenada con promesas; cancelable; devuelve `finished`
   motion.enter/exit/hover/press/damage/heal/critical/reward/shield/buff/debuff
   motion.cifra(...)         FloatingCombatText (texto + icono + color, aria-hidden; el registro lo dice)
   motion.transicionDePagina(fn)  View Transition API si existe, si no fn()
frontend/app-web/src/comun/ui/juego/fx.js ← diccionario FX: (prototipo, acción, categoría, efectos) → semántica
frontend/app-web/src/plataforma/salas-partidas/combate.js ← solo llama a motion.* donde hoy anota/anuncia
docs/motion/NEXUS-MOTION-SYSTEM.md  ← lenguaje, reglas, cómo añadir una animación
```

Reglas: una animación nueva entra por `motion.js` o no entra; nunca el único canal de información; transform/opacity/filter únicamente; nada permanente salvo idle y halo; assets de batalla cargados en `sala-batalla` (no engordan login/home); la lógica de combate y el ritmo (`ritmo-de-combate.js`) no cambian: el golpe se dibuja cuando llega el aviso que ya llegaba.

### 8. Battle Motion Language

Inventario de las acciones de los ocho héroes (Tabla 7 y Tabla 20 del `Reglamento.java` del motor; el aviso trae `accion.tipo`, `esEpica`, la categoría del golpe en `accion.nombre` y los efectos con su `tipo`):

| Héroe | Acción | Tipo | Target | FX visual |
|---|---|---|---|---|
| Guerrero Tanque | Ataque básico · Golpe con escudo | ATAQUE | rival | PHYSICAL_HIT |
| Guerrero Tanque | Mano de piedra (+12 def) · Defensa feroz (inmune físico, −3d6 mágico) | DEFENSA | sí mismo | SHIELD |
| Guerrero Tanque | Golpe de defensa (épica) | ATAQUE | rival | EPIC + PHYSICAL_HIT |
| Guerrero Armas | Ataque básico · Embate sangriento · Lanza de los dioses · Golpe de tormenta | ATAQUE | rival | PHYSICAL_HIT |
| Guerrero Armas | Segundo impulso (épica: recupera vida, +crítico) | SANACION | sí mismo | EPIC + HEAL |
| Mago Fuego | Ataque básico · Misiles de magma · Vulcano · Pare de fuego (devuelve daño) | ATAQUE | rival | MAGIC_FIRE |
| Mago Fuego | Luz cegadora (épica) | ATAQUE/SANACION | rival/sí | EPIC + MAGIC_FIRE |
| Mago Hielo | Ataque básico · Lluvia de hielo | ATAQUE | rival | MAGIC_ICE |
| Mago Hielo | Cono de hielo (PENALIZA_ATAQUE 2 t) · Bola de hielo (PENALIZA_DANO) | ATAQUE + efecto | rival | MAGIC_ICE + DEBUFF |
| Mago Hielo | Frío concentrado (épica: −1 poder rival / INMUNE_TOTAL) | APOYO | rival/sí | EPIC + DEBUFF / SHIELD |
| Pícaro Veneno | Ataque básico · Flor de loto · Agonía | ATAQUE | rival | POISON |
| Pícaro Veneno | Piquete (BONO_ATAQUE propio) | ATAQUE + buff | rival + sí | POISON + BUFF |
| Pícaro Veneno | Toma y lleva (épica: REFLEJA_MITAD) | ATAQUE | rival | EPIC + SHIELD |
| Pícaro Machete | Ataque básico · Machetazo · Planazo | ATAQUE | rival | PHYSICAL_HIT |
| Pícaro Machete | Cortada (BONO_DANO propio) | ATAQUE + buff | rival + sí | PHYSICAL_HIT + BUFF |
| Pícaro Machete | Intimidación sangrienta (épica) | ATAQUE | rival | EPIC + PHYSICAL_HIT |
| Chamán | Sanación básica · Toque de la Vida · Vínculo Natural (BONO_SANACION) | SANACION | aliado/sí | HEAL (+BUFF) |
| Chamán | Canto del Bosque (grupo, SANACION_POR_TURNO) · Té changua (épica, grupo) | SANACION_GRUPAL | grupo | HEAL ×n / EPIC + HEAL |
| Médico | Sanación básica · Curación Directa · Neutralización de Efectos | SANACION | aliado/sí | HEAL |
| Médico | Reanimación (todo el poder, 100 % vida) · Reanimador 3000 (épica, VINCULO_REANIMACION) | REANIMACION | compañero | EPIC + HEAL |
| Cualquiera | Efecto por turno (DANO_POR_TURNO / SANACION_POR_TURNO) | EFECTO | portador | POISON tick / HEAL tick |
| Cualquiera | categoría del motor: CRÍTICO / EVADE / RESISTE / ESCAPA / SIN EFECTO | — | objetivo | CRITICAL / DODGE / BLOCK / RESIST / MISS |

**Diccionario FX (semántica → qué se ve).** Regla: *1 acción importante → 1 foco visual dominante*.

| Clave | Actor | Objetivo | Cifra (FloatingCombatText) | Color/icono (nunca solo color) |
|---|---|---|---|---|
| PHYSICAL_HIT | anticipa 120 → lunge 150 → hit-stop 60 → regreso 220 | flash brightness 180 ms + recoil 14 px | «−X VIDA» | error + espada |
| MAGIC_FIRE | anticipa + brazo (scale 1.04) sin lunge largo; destello cálido | flash + recoil corto | «−X VIDA» | error + fuego |
| MAGIC_ICE | igual, destello frío | flash + «congelado» (saturate↓ 300 ms) | «−X VIDA» | info + copo |
| POISON | lunge corto | tinte verdoso 300 ms | «−X VIDA» · ticks «−X» pequeños | advertencia + gota |
| HEAL | brillo propio | halo ascendente 400 ms | «+X VIDA» | éxito + cruz |
| SHIELD | encaje (scale 0.96→1) | — | «ESCUDO» / «INMUNE» | info + escudo |
| BUFF | — | chip de efecto entra con pulso único | «+ATQ» / «+DAÑO» | éxito + flecha |
| DEBUFF | — | chip entra, retrato saturate↓ breve | «−ATQ» / «−DAÑO» | advertencia + flecha |
| CRITICAL | lunge más largo (56 px) + hit-stop 90 | flash más fuerte, recoil 20 px, sacudida **local** 2 px | «CRÍTICO −X» a 2,5 rem | error + rayo |
| DODGE / BLOCK / RESIST | lunge normal | paso lateral 10 px / encaje / flash tenue | «ESQUIVADO» / «BLOQUEADO» / «RESISTIDO −X» | info + escudo |
| MISS | lunge normal | nada | «SIN EFECTO» | texto-3 + cerrar |
| EPIC | halo del actor 600 ms antes del golpe + «ÉPICA» | según el FX base | según base | acento + rayo |
| CAÍDA | — | desaturación + opacidad 0.6 (ya `campo__puesto--caido`) | «CAE» | — |
| TURNO | halo (outline) en el héroe activo + pulso único; HUD «TU TURNO» entra | — | — | enlace-sobre-cromo |
| IA PENSANDO | tres puntos que laten junto al nombre + halo tenue durante la pausa | — | — | — |
| VICTORIA | — | — | «VICTORIA» entra con scale + rayos + confeti contenido (60 partículas DOM, 1,5 s) + créditos contados | trofeo |
| DERROTA / EMPATE | desaturación ligera del campo + fade sobrio / neutro | — | — | escudo |

Tiempos: toda la secuencia del actor cabe en 550–900 ms, dentro de los 2 200 ms de la pausa perceptual que ya existe; la cifra vive 1,2 s; nada se solapa con el anuncio central (que sigue en 2 s).

### 9. Animaciones por módulo

- **Batalla (P0):** todo lo del §8; barras con `scaleX` + estela del daño (la cifra textual cambia al instante); cuenta atrás 5…1 con desenfoque→nitidez y flash en «¡COMBATE!» sin tocar `retenerHasta`.
- **Héroes (P1):** tarjeta con profundidad en hover (sombra + 2 px), estadísticas con stagger de 40 ms al seleccionar, sin 3D.
- **Misiones (P1):** MissionCard entra escalonada (una vez, 40 ms), EN PROGRESO con halo estático, progreso que se llena hasta el valor del servidor, recompensa `motion.reward`, jefe final con halo, MissionStepper fade.
- **Torneos (P1):** camino del jugador resaltado; encuentro resuelto acusado; sin animar el árbol entero.
- **Inventario (P1):** equipar = FLIP de la ranura **después** del OK del backend; des-equipar al revés.
- **Tienda (P1):** añadir al carrito → pulso del botón + badge 0→1; compra → reward → enlace al inventario.
- **Subastas (P1):** precio nuevo resaltado + contador que cuenta; superado = aviso con icono; ganada = success; se retiran los pulsos infinitos (quedan color + icono + texto).
- **Notificaciones (P1):** badge 0→1 con un pulso; entrada de la notificación nueva.
- **Navegación (P2):** View Transition API entre documentos (`@view-transition { navigation: auto }`) con fade de 180 ms y `view-transition-name` en cabecera; sin soporte, navegación normal.
- **Ambiental:** solo el halo del chatbot (ya) y un barrido de luz único al entrar al campo. Nada permanente, nada de vídeo ni GIF; se retiran los blobs muertos de `tema-cuentas.css`.

### 10. Performance budget

| Métrica | Objetivo | Cómo se mide |
|---|---|---|
| FPS en batalla | 60 escritorio · ≥ 50 móvil medio (CPU ×4) | banco Playwright + CDP (como el POC) sobre `sala-batalla` con `combate-seis` |
| Long tasks por golpe | 0 | `PerformanceObserver('longtask')` |
| CLS | ≈ 0 (< 0,01) | `layout-shift` |
| Propiedades animadas | solo transform / opacity / filter | prueba estática: ningún `@keyframes` nuevo anima width/height/left/top/margin |
| Peso añadido | 0 KB de dependencias; CSS +≤ 6 KB gz; `motion.js` ≤ 4 KB gz | tamaño en el PR |
| Login/home | no cargan nada de batalla | `fx.js` y confeti se importan en `sala-batalla` |
| Memoria | sin crecimiento tras 50 golpes (las animaciones se cancelan y los nodos se retiran) | heap antes/después en el banco |

### 11. Accesibilidad

- `prefers-reduced-motion: reduce` **o** `data-test-motion="off"`: sin lunge, sin sacudida, sin parallax, sin partículas, sin escalados grandes; queda el feedback inmediato (estado, texto, color + icono, cifra con `role=status` en el registro). `motion.js` lo decide en un solo sitio y lo re-evalúa si la preferencia cambia en caliente.
- La animación nunca es la única información: el registro de combate (`aria-live`) y el anuncio central ya lo dicen; la cifra flotante lleva texto, no solo color (XAG 102, RNF-ACC-002).
- `forced-colors: active`: sin filtros ni halos; `prefers-contrast: more`: sin desaturaciones.
- Foco siempre visible; nada que mueva el foco ni robe clics (`pointer-events: none` en toda capa decorativa).
- `axe` del laboratorio sigue corriendo sobre las vistas animadas con `data-test-motion="off"`.

### 12. Plan MOTION-0 → MOTION-4

| Fase | Entregable | PR |
|---|---|---|
| MOTION-0 (hecho) | auditoría, POC, benchmark, decisión: CSS + WAAPI sin biblioteca | — (este informe, `docs/motion/`) |
| MOTION-1 Foundation | tokens `--motion-*`/`--ease-*`, `motion.js` (permitido, secuencia, enter/exit/hover/press/damage/heal/critical/reward…), `data-test-motion`, reduced motion, View Transition progresiva, retirada de infinitos y del CSS muerto, pruebas Jest | PR-1 `feat/motion-system-foundation` |
| MOTION-2 Battle Game Feel | idle, ataque, impacto, recoil, FloatingCombatText por FX, curación, buff/debuff, escudo, crítico, esquiva, turno, IA pensando, cuenta atrás, barras con estela, VICTORIA/DERROTA/EMPATE; `fx.js` con los 8 héroes; E2E de comportamiento | PR-2 `feat/battle-game-feel` |
| MOTION-3 Game World | misiones, torneos, inventario, tienda, subastas, notificaciones, héroes | PR-3 `feat/game-world-motion` |
| MOTION-4 Performance + QA | banco de rendimiento (FPS, long tasks, CLS) en el laboratorio, modo determinista en capturas, axe, pruebas de reduced motion, evidencia antes/después (WebM), validación en AWS DEV, trazabilidad en issues y Project | PR-4 `test/motion-performance-regression` |

La solución elegida no tiene coste, su licencia es la de la plataforma y no reescribe la arquitectura: **se continúa automáticamente con MOTION-1 → MOTION-4.**
