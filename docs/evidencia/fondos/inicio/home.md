# Contraste de «home» sobre la escena «inicio»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`home-poblada`) | 6 | Explora el Nexo (`p.home__sobretitulo`) | 10.01 | 4.5 | cumple |
| 1366 | con datos (`home-poblada`) | 3 | Tus créditos (`h2.t-subtitulo`) | 7.48 | 4.5 | cumple |
| movil | con datos (`home-poblada`) | 0 | — | — | — | cumple |

Capturas: `home-<ancho>.jpg` en esta carpeta.
