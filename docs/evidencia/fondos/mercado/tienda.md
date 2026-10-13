# Contraste de «tienda» sobre la escena «mercado»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`tienda-con-catalogo`) | 10 | Productos a la venta (`h2.solo-lectores`) | 8.36 | 4.5 | cumple |
| 1366 | con datos (`tienda-con-catalogo`) | 10 | 5 productos a la venta. (`p.resultado-tienda`) | 7.32 | 4.5 | cumple |
| movil | con datos (`tienda-con-catalogo`) | 10 | Los precios en USD no están disponibles por ahor (`p.selector-moneda__nota`) | 9.99 | 4.5 | cumple |

Capturas: `tienda-<ancho>.jpg` en esta carpeta.
