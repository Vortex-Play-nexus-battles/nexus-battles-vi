# Contraste de «portada» sobre la escena «entrada»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`portada-con-tienda`) | 11 | Equipa a tu héroe, entra a una sala y decide el  (`p.entrada__lema`) | 8.27 | 4.5 | cumple |
| 1366 | con datos (`portada-con-tienda`) | 10 | ¿Ya tienes cuenta? (`span.cabecera__invitacion`) | 7.06 | 4.5 | cumple |
| movil | con datos (`portada-con-tienda`) | 9 | Equipa a tu héroe, entra a una sala y decide el  (`p.entrada__lema`) | 7.45 | 4.5 | cumple |

Capturas: `portada-<ancho>.jpg` en esta carpeta.
