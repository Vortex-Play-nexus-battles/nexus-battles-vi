# Contraste de «verificar-cuenta» sobre la escena «registro»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | sin servicios | 9 | Antes de entrar al Nexo comprobamos que el corre (`p.entrada__lema`) | 7.58 | 4.5 | cumple |
| 1366 | sin servicios | 9 | Antes de entrar al Nexo comprobamos que el corre (`p.entrada__lema`) | 8.19 | 4.5 | cumple |
| movil | sin servicios | 1 | NB VI (`span.cabecera__marca-corta`) | 18.00 | 3 | cumple |

Capturas: `verificar-cuenta-<ancho>.jpg` en esta carpeta.
