# Rendimiento del combate — HU-UX-003 (MOTION-4)

Generado por `tests/visual/motion-rendimiento.spec.js`: `combate-seis` con una ráfaga de cuatro golpes (uno propio y tres rivales), Chromium headless 1366×768, CPU ×4 (móvil medio emulado).

| medida | valor |
|---|---|
| cuadros | 439 |
| duracionMs | 7933 |
| fpsMedio | 55 |
| frameP50 | 16.7 |
| frameP95 | 33.3 |
| frameMax | 83.3 |
| cuadrosLentos | 12 |
| longTasks | 2 |
| longTasksMs | 115 |
| cls | 0 |
| heapMB | 9.5 |
| animacionesVivas | 9 |
| figurasConGolpe | 0 |

Presupuesto: CLS < 0.02, long tasks ≤ 3, p95 ≤ 40 ms, cuadros > 33 ms ≤ 10 %.
