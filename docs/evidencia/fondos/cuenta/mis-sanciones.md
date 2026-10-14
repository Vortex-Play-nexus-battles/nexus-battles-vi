# Contraste de «mis-sanciones» sobre la escena «cuenta»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`mis-sanciones-suspendida`) | 2 | Tu historial disciplinario. Una sanción vigente  (`p.t-meta`) | 8.28 | 4.5 | cumple |
| 1366 | con datos (`mis-sanciones-suspendida`) | 2 | Tu historial disciplinario. Una sanción vigente  (`p.t-meta`) | 7.99 | 4.5 | cumple |
| movil | con datos (`mis-sanciones-suspendida`) | 5 | Tu historial disciplinario. Una sanción vigente  (`p.t-meta`) | 11.77 | 4.5 | cumple |

Capturas: `mis-sanciones-<ancho>.jpg` en esta carpeta.
