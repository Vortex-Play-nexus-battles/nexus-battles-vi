# Contraste de «pujas» sobre la escena «mercado»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`pujas-activas`) | 2 | Ordenadas por tiempo restante · Selecciona una p (`span.texto-pista`) | 8.14 | 4.5 | cumple |
| 1366 | con datos (`pujas-activas`) | 2 | Ordenadas por tiempo restante · Selecciona una p (`span.texto-pista`) | 9.39 | 4.5 | cumple |
| movil | con datos (`pujas-activas`) | 6 | Ordenadas por tiempo restante · Selecciona una p (`span.texto-pista`) | 8.17 | 4.5 | cumple |

Capturas: `pujas-<ancho>.jpg` en esta carpeta.
