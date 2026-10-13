# Contraste de «historial-transacciones» sobre la escena «mercado»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`historial-con-movimientos`) | 3 | Todas tus transacciones en moneda real: cargas,  (`p.t-meta`) | 11.40 | 4.5 | cumple |
| 1366 | con datos (`historial-con-movimientos`) | 3 | Página 1 de 3 (`span.paginacion__info`) | 7.19 | 4.5 | cumple |
| movil | con datos (`historial-con-movimientos`) | 4 | Todas tus transacciones en moneda real: cargas,  (`p.t-meta`) | 9.85 | 4.5 | cumple |

Capturas: `historial-transacciones-<ancho>.jpg` en esta carpeta.
