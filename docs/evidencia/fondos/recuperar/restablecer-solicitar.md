# Contraste de «restablecer-solicitar» sobre la escena «recuperar»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | con datos (`entrada-recuperar`) | 7 | Recuperar el acceso al Nexo son tres pasos: pide (`p.entrada__lema`) | 10.36 | 4.5 | cumple |
| 1366 | con datos (`entrada-recuperar`) | 7 | ¿Ya tienes cuenta? (`span.cabecera__invitacion`) | 7.09 | 4.5 | cumple |
| movil | con datos (`entrada-recuperar`) | 5 | Recuperar el acceso al Nexo son tres pasos: pide (`p.entrada__lema`) | 10.47 | 4.5 | cumple |

Capturas: `restablecer-solicitar-<ancho>.jpg` en esta carpeta.
