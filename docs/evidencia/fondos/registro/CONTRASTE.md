# Contraste sobre la escena «registro»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). Para cada vista y ancho, el texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). La columna «peor texto» es la caja con el percentil 1 más bajo de la vista.

| Vista | Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|---|
| preparando | 1366 | sin servicios | 6 | Tu cuenta ya existe. Mientras la dejamos lista p (`p.entrada__lema`) | 8.77 | 4.5 | cumple |
| preparando | 1920 | sin servicios | 6 | Créditos de bienvenida para tus primeras batalla (`span`) | 6.15 | 4.5 | cumple |
| preparando | movil | sin servicios | 7 | Tu cuenta ya existe. Mientras la dejamos lista p (`p.entrada__lema`) | 10.17 | 4.5 | cumple |
| registro | 1366 | con datos (`entrada-crear-cuenta`) | 8 | Tu héroe equipado decide con qué entras al comba (`span`) | 5.97 | 4.5 | cumple |
| registro | 1920 | con datos (`entrada-crear-cuenta`) | 8 | Crea tu cuenta y empieza con créditos para tu pr (`p.entrada__lema`) | 8.22 | 4.5 | cumple |
| registro | movil | con datos (`entrada-crear-cuenta`) | 1 | NB VI (`span.cabecera__marca-corta`) | 18.00 | 3 | cumple |
| verificar-cuenta | 1366 | sin servicios | 9 | Antes de entrar al Nexo comprobamos que el corre (`p.entrada__lema`) | 8.19 | 4.5 | cumple |
| verificar-cuenta | 1920 | sin servicios | 9 | Antes de entrar al Nexo comprobamos que el corre (`p.entrada__lema`) | 7.58 | 4.5 | cumple |
| verificar-cuenta | movil | sin servicios | 1 | NB VI (`span.cabecera__marca-corta`) | 18.00 | 3 | cumple |

Capturas: `<vista>-<ancho>.jpg` en esta carpeta.
