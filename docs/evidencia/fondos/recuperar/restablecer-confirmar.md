# Contraste de «restablecer-confirmar» sobre la escena «recuperar»

Generado por `tests/visual/contraste-fondos.spec.js` (HU-UX-002). El texto que cae directamente sobre la escena (sin superficie propia), medido píxel a píxel contra la escena con su velo. Se exige AA en el percentil 1: 4,5:1 (3:1 en texto grande). «Peor texto» es la caja con el percentil 1 más bajo en ese ancho.

| Ancho | Estado | Cajas | Peor texto | p1 | Umbral | Resultado |
|---|---|---|---|---|---|---|
| 1920 | sin servicios | 8 | El código llega por correo, dura poco y solo sir (`p.entrada__lema`) | 10.20 | 4.5 | cumple |
| 1366 | sin servicios | 8 | ¿Ya tienes cuenta? (`span.cabecera__invitacion`) | 7.09 | 4.5 | cumple |
| movil | sin servicios | 2 | El código llega por correo, dura poco y solo sir (`p.entrada__lema`) | 12.25 | 4.5 | cumple |

Capturas: `restablecer-confirmar-<ancho>.jpg` en esta carpeta.
