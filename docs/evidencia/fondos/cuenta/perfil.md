# Contraste de «perfil» sobre la escena «cuenta»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`perfil-estado-de-cuenta`) | 8 | Resumen (`span.pestana__cara`) | 7.18 | 4.5 | cumple |
| 1366 | con datos (`perfil-estado-de-cuenta`) | 8 | Perfil (`span.pestana__cara`) | 8.10 | 4.5 | cumple |
| movil | con datos (`perfil-estado-de-cuenta`) | 9 | Resumen (`span.pestana__cara`) | 10.09 | 4.5 | cumple |

Capturas: `perfil-<ancho>.jpg` en esta carpeta.
