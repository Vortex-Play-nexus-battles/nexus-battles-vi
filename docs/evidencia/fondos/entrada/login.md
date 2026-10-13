# Contraste de «login» sobre la escena «entrada»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`entrada-sin-tienda`) | 7 | ¿Primera vez en el Nexo? (`span.cabecera__invitacion`) | 8.20 | 4.5 | cumple |
| 1366 | con datos (`entrada-sin-tienda`) | 7 | ¿Primera vez en el Nexo? (`span.cabecera__invitacion`) | 7.18 | 4.5 | cumple |
| movil | con datos (`entrada-sin-tienda`) | 5 | Equipa a tu héroe, entra a una sala y decide el  (`p.entrada__lema`) | 12.12 | 4.5 | cumple |

Capturas: `login-<ancho>.jpg` en esta carpeta.
