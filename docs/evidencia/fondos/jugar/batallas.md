# Contraste de «batallas» sobre la escena «jugar»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`batallas-con-salas`) | 6 | Modalidad (`span.campo__etiqueta`) | 9.56 | 4.5 | cumple |
| 1366 | con datos (`batallas-con-salas`) | 6 | Mostrando 3 de 3 salas (`span.paginacion__info`) | 5.55 | 4.5 | cumple |
| movil | con datos (`batallas-con-salas`) | 7 | Las salas no se actualizan solas ahora mismo. El (`p.t-meta`) | 9.95 | 4.5 | cumple |

Capturas: `batallas-<ancho>.jpg` en esta carpeta.
