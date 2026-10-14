# Contraste de «sala-batalla-campo» sobre la escena «arena»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`combate-seis`) | 9 | Aquiles de la Ceniza (`span.campo__nombre`) | 7.75 | 4.5 | cumple |
| 1366 | con datos (`combate-seis`) | 9 | Aquiles de la Ceniza (`span.campo__nombre`) | 10.59 | 4.5 | cumple |

Capturas: `sala-batalla-campo-<ancho>.jpg` en esta carpeta.
