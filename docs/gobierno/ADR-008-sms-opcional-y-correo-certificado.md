# ADR-008 — Sin SMS ni correo certificado: las sanciones se avisan por correo y en la bandeja

- **Estado:** aceptada (SMS); el correo certificado queda **bloqueado por coste**
- **Fecha:** 14 de octubre de 2026
- **Ámbito:** `services/plataforma/moderacion-sanciones`, `services/plataforma/correo`, `services/plataforma/notificaciones`
- **Relacionada con:** §7.3.7 del documento del curso, RF-NOT-005, SA-4 (#962), SA-12
- **Clasificación:** REQUISITO PDF (§7.3.7) · DECISIÓN TÉCNICA (SMS opcional) · COSTE (correo certificado)

## Contexto

§7.3.7 enumera cómo se avisa de cada sanción:

- «Advertencias recibidas (email + notificación in-app).»
- «Suspensiones temporales con fecha de finalización (email + **SMS opcional**).»
- «Baneo definitivo con motivo y proceso de apelación (**email certificado**).»

Lo que hay en `develop`: moderacion-sanciones emite, por cada sanción, un aviso
en la bandeja (notificaciones) y un correo sobre la plantilla corporativa
(correo, `CorreoSancionRequest`) con la acción, la causal, el motivo, la
política violada, la fecha de fin y el plazo y el camino para apelar. SA-12
prueba que esos correos llegan de verdad al buzón del banco (Mailpit), no a un
doble.

## Decisión

1. **No se envían SMS.** El documento los marca como opcionales, y hacerlo
   exigiría dos cosas que hoy no existen:
   - **un número de teléfono de cada jugador**: el registro no lo pide y la
     cuenta no lo guarda. Pedirlo solo para esto iría contra la minimización
     de datos que pide §7.3.6;
   - **un proveedor de SMS**, que cobra por mensaje (no hay plan gratuito
     utilizable en Colombia para producción).

   No se implementa un SMS simulado: un aviso que no sale no es un aviso, y
   presentarlo como hecho sería falso. La suspensión se avisa por correo y en
   la bandeja, con su fecha de fin.
2. **El baneo sale por correo ordinario, no certificado.** El correo
   certificado (con acuse y valor probatorio) solo lo dan proveedores de pago;
   el SRS lo registra como INC-20 en las observaciones de RF-NOT-005. El correo
   del baneo ya lleva el motivo, la causal y el proceso de apelación, y la
   sanción queda en la bitácora inmutable (§7.3.5) con su motivo.

## Lo que queda bloqueado y la acción humana exacta

| Bloqueo | Tipo | Acción humana exacta |
|---|---|---|
| Correo certificado del baneo | COSTE | Que el Product Owner apruebe contratar un proveedor de correo certificado y su presupuesto. Con el proveedor elegido, el servicio de correo añade un canal (una ruta nueva de `correo.yaml`, aditiva) y moderacion-sanciones lo usa solo para `BANEO`. |
| SMS de la suspensión (opcional) | DECISIÓN TÉCNICA, revisable por el PO | Si el Product Owner lo quiere, decidir pedir el teléfono en el registro (con su consentimiento, RF-PRV-003) y aprobar el coste del proveedor de SMS. Sin esas dos decisiones no se implementa. |

## Consecuencias

- El informe de cierre declara la suspensión como **DONE** (correo con fecha
  de fin y aviso en la bandeja; el SMS es opcional y está decidido aquí) y el
  baneo como **BLOQUEADO por coste** en lo que toca a «certificado».
- Ninguna pantalla dice que se envió un SMS ni un correo certificado.
