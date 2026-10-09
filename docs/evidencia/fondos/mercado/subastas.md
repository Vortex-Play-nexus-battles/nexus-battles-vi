# Contraste de «subastas» sobre la escena «mercado»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`subastas-mercado-con-paginas`) | 2 | Compra y vende objetos con el resto de jugadores (`p.t-cuerpo`) | 16.65 | 4.5 | cumple |
| 1366 | con datos (`subastas-mercado-con-paginas`) | 2 | Compra y vende objetos con el resto de jugadores (`p.t-cuerpo`) | 16.09 | 4.5 | cumple |
| movil | con datos (`subastas-mercado-con-paginas`) | 3 | Compra y vende objetos con el resto de jugadores (`p.t-cuerpo`) | 15.09 | 4.5 | cumple |

Capturas: `subastas-<ancho>.jpg` en esta carpeta.
