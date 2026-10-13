# Contraste de «sala-batalla» sobre la escena «jugar»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`sala-en-espera`) | 0 | — | — | — | cumple |
| 1366 | con datos (`sala-en-espera`) | 0 | — | — | — | cumple |
| movil | con datos (`sala-en-espera`) | 0 | — | — | — | cumple |

Capturas: `sala-batalla-<ancho>.jpg` en esta carpeta.
