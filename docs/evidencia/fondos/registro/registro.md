# Contraste de «registro» sobre la escena «registro»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`entrada-crear-cuenta`) | 8 | Crea tu cuenta y empieza con créditos para tu pr (`p.entrada__lema`) | 8.22 | 4.5 | cumple |
| 1366 | con datos (`entrada-crear-cuenta`) | 8 | Tu héroe equipado decide con qué entras al comba (`span`) | 5.97 | 4.5 | cumple |
| movil | con datos (`entrada-crear-cuenta`) | 1 | NB VI (`span.cabecera__marca-corta`) | 18.00 | 3 | cumple |

Capturas: `registro-<ancho>.jpg` en esta carpeta.
