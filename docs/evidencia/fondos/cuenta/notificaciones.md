# Contraste de «notificaciones» sobre la escena «cuenta»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | sin servicios | 2 | Lo que te pasa en el juego, en todas tus sesione (`p.t-cuerpo`) | 12.50 | 4.5 | cumple |
| 1366 | sin servicios | 2 | Lo que te pasa en el juego, en todas tus sesione (`p.t-cuerpo`) | 11.98 | 4.5 | cumple |
| movil | sin servicios | 5 | Lo que te pasa en el juego, en todas tus sesione (`p.t-cuerpo`) | 18.32 | 4.5 | cumple |

Capturas: `notificaciones-<ancho>.jpg` en esta carpeta.
