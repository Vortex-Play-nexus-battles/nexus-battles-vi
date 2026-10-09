# Contraste de «preparando» sobre la escena «registro»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | sin servicios | 6 | Créditos de bienvenida para tus primeras batalla (`span`) | 6.15 | 4.5 | cumple |
| 1366 | sin servicios | 6 | Tu cuenta ya existe. Mientras la dejamos lista p (`p.entrada__lema`) | 8.77 | 4.5 | cumple |
| movil | sin servicios | 7 | Tu cuenta ya existe. Mientras la dejamos lista p (`p.entrada__lema`) | 10.17 | 4.5 | cumple |

Capturas: `preparando-<ancho>.jpg` en esta carpeta.
