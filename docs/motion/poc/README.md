# POC de MOTION-0 — la misma coreografía con CSS/WAAPI, Motion y Anime.js

Medido el 11-oct-2026 en Chromium (Playwright 1.56, headless, 1366×768), 20
golpes por variante con la coreografía del Battle Motion Language:
anticipa 120 ms → lunge 150 ms (44 px) → impacto (flash `brightness(1.9)`
180 ms + recoil 14 px + cifra flotante 800 ms) → hit-stop 60 ms → regreso
220 ms. El HUD recibe el valor del servidor al instante y el relleno
interpola con `transform: scaleX()`.

| Implementación | Qué se vendorizaría | Tamaño (min / gzip) |
|---|---|---|
| CSS + WAAPI | nada | 0 KB |
| Motion 14.1.0 (`animate` + secuencias, esbuild `--bundle --minify`) | `motion-hybrid.min.js` | 55,6 KB / 20,5 KB |
| Motion 14.1.0 mini (sin secuencias) | `motion-mini.min.js` | 8,4 KB / 3,4 KB |
| Motion 14.1.0 UMD publicado | `dist/motion.js` | 144,9 KB / 47,9 KB |
| Anime.js 4.5.0 ESM publicado | `anime.esm.min.js` | 118,7 KB / 40,8 KB |
| Anime.js 4.5.0 (`animate` + `createTimeline`, esbuild) | `anime-core.min.js` | 37,9 KB / 14,0 KB |
| @tsparticles/confetti 4.4.0 | `tsparticles.confetti.bundle.min.js` | 132,3 KB / 38,2 KB |
| canvas-confetti 1.9.4 | `confetti.module.mjs` | 24,9 KB / 6,9 KB |

Métricas: `requestAnimationFrame` (frames, p50/p95/máx, frames > 33 ms),
`PerformanceObserver` (`longtask`, `layout-shift`), CDP
`Performance.getMetrics` (ScriptDuration, LayoutDuration,
RecalcStyleDuration, LayoutCount, JSHeapUsedSize) y
`Emulation.setCPUThrottlingRate` ×4 y ×6 para emular un móvil medio.
Resultados completos en `resultados.md` / `resultados.json`.

Reproducir: `npm view motion animejs @tsparticles/confetti canvas-confetti
version license` para versiones y licencias; instalar en una carpeta aparte
(`npm i motion@14.1.0 animejs@4.5.0 esbuild`), `npx esbuild entry.js
--bundle --format=esm --minify` con `export { animate } from 'motion'` (y
`export { animate, createTimeline } from 'animejs'`), y medir con un
`chromium.launch()` de Playwright sobre una página estática que corra la
secuencia (`page.evaluate`) con `context.newCDPSession(page)`.
