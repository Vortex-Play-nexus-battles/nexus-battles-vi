# Contraste de «mis-cofres» sobre la escena «inventario»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`cofres-ganados`) | 3 | Los cofres que has ganado acumulando créditos en (`p.t-meta`) | 10.05 | 4.5 | cumple |
| 1366 | con datos (`cofres-ganados`) | 3 | Los cofres que has ganado acumulando créditos en (`p.t-meta`) | 9.94 | 4.5 | cumple |
| movil | con datos (`cofres-ganados`) | 5 | Los cofres que has ganado acumulando créditos en (`p.t-meta`) | 11.66 | 4.5 | cumple |

Capturas: `mis-cofres-<ancho>.jpg` en esta carpeta.
