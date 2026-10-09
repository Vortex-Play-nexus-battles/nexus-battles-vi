# Contraste de «publicar-subasta» sobre la escena «mercado»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`publicar-con-nombre-del-catalogo`) | 1 | ← Volver a Subastas (`a`) | 10.03 | 4.5 | cumple |
| 1366 | con datos (`publicar-con-nombre-del-catalogo`) | 1 | ← Volver a Subastas (`a`) | 10.09 | 4.5 | cumple |
| movil | con datos (`publicar-con-nombre-del-catalogo`) | 0 | — | — | — | cumple |

Capturas: `publicar-subasta-<ancho>.jpg` en esta carpeta.
