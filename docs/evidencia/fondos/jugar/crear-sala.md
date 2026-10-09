# Contraste de «crear-sala» sobre la escena «jugar»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`crear-sala-maquinas-de-mas`) | 2 | Configura la partida a tu medida. Los campos inv (`p.t-cuerpo`) | 18.08 | 4.5 | cumple |
| 1366 | con datos (`crear-sala-maquinas-de-mas`) | 2 | Configura la partida a tu medida. Los campos inv (`p.t-cuerpo`) | 16.48 | 4.5 | cumple |
| movil | con datos (`crear-sala-maquinas-de-mas`) | 4 | Configura la partida a tu medida. Los campos inv (`p.t-cuerpo`) | 17.10 | 4.5 | cumple |

Capturas: `crear-sala-<ancho>.jpg` en esta carpeta.
