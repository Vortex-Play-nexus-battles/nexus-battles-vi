# Contraste de «misiones» sobre la escena «misiones»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`misiones-sin-abrir`) | 10 | Tablón (`span.pestana__cara`) | 8.39 | 4.5 | cumple |
| 1366 | con datos (`misiones-sin-abrir`) | 10 | Historial (`span.pestana__cara`) | 4.84 | 4.5 | cumple |
| movil | con datos (`misiones-sin-abrir`) | 7 | Tablón (`span.pestana__cara`) | 9.82 | 4.5 | cumple |

Capturas: `misiones-<ancho>.jpg` en esta carpeta.
