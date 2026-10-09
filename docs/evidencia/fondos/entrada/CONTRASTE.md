# Contraste sobre la escena «entrada»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). Para cada vista y ancho, el texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). La columna «peor texto» es la caja con el percentil 1 más bajo de la vista.

| Vista | Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|---|
| login | 1366 | con datos (`entrada-sin-tienda`) | 7 | ¿Primera vez en el Nexo? (`span.cabecera__invitacion`) | 7.18 | 4.5 | cumple |
| login | 1920 | con datos (`entrada-sin-tienda`) | 7 | ¿Primera vez en el Nexo? (`span.cabecera__invitacion`) | 8.20 | 4.5 | cumple |
| login | movil | con datos (`entrada-sin-tienda`) | 5 | Equipa a tu héroe, entra a una sala y decide el  (`p.entrada__lema`) | 12.12 | 4.5 | cumple |
| portada | 1366 | con datos (`portada-con-tienda`) | 10 | ¿Ya tienes cuenta? (`span.cabecera__invitacion`) | 7.06 | 4.5 | cumple |
| portada | 1920 | con datos (`portada-con-tienda`) | 11 | Equipa a tu héroe, entra a una sala y decide el  (`p.entrada__lema`) | 8.27 | 4.5 | cumple |
| portada | movil | con datos (`portada-con-tienda`) | 9 | Equipa a tu héroe, entra a una sala y decide el  (`p.entrada__lema`) | 7.45 | 4.5 | cumple |

Capturas: `<vista>-<ancho>.jpg` en esta carpeta.
