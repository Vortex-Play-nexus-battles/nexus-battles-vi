# ADR-007 — La bitácora de auditoría no se purga nunca; su copia fuera del host espera un presupuesto

- **Estado:** aceptada (la parte técnica); la copia fuera del host queda **bloqueada por coste**
- **Fecha:** 14 de octubre de 2026
- **Ámbito:** `services/cuentas/ms-cumplimiento` (bitácora central) y `infrastructure/respaldo-dr`
- **Relacionada con:** §7.3.5 del documento del curso, HU-AUD-001, HU-AUD-002, SA-5 (#968), SA-17
- **Clasificación:** REQUISITO PDF (§7.3.5) · DECISIÓN TÉCNICA (cómo se cumple) · COSTE (la copia fuera del host)

## Contexto

§7.3.5 pide que el registro de auditoría sea **inmutable** («no puede ser
modificado o eliminado»), **accesible solo para Super Administradores**,
**exportable** y **conservado por un período mínimo de 5 años**.

Lo que había en `develop` antes de esta decisión:

- La bitácora vive en la tabla `audit_log` de la base propia de
  ms-cumplimiento (regla 7: ningún otro servicio la toca; los demás le envían
  eventos por `POST /api/v1/admin/auditoria/eventos`).
- V1 y V2 bloquean `UPDATE` y `DELETE` fila a fila con un disparador que deja
  el incidente en el log de PostgreSQL y aborta. **`TRUNCATE` no estaba
  cerrado**: los disparadores por fila no lo ven, y la nota del final de V1 lo
  dejaba como pendiente.
- Ningún código del repositorio borra entradas: no hay purga, ni tarea
  programada de retención, ni ruta de borrado en la API.
- Los respaldos diarios (`infrastructure/respaldo-dr/respaldar.sh`) incluyen
  `cumplimiento-db`, pero se guardan **en el mismo disco** del host y se
  conservan **7 días** (`RESPALDO_RETENCION_DIAS`). Protegen de un error
  lógico, no de perder la instancia.

## Decisión

1. **La bitácora es append-only de verdad.** V4 (`V4__bitacora_sin_truncate.sql`)
   añade un disparador por sentencia `BEFORE TRUNCATE` que aborta igual que
   los de `UPDATE` y `DELETE`. `BitacoraInmutableTest` lo comprueba contra
   PostgreSQL: los tres intentos se rechazan y la entrada sigue igual.
2. **No hay purga.** Ninguna entrada se borra por antigüedad: el mínimo de
   cinco años se cumple conservándolo todo, sin límite superior, mientras la
   base exista. Una purga futura (si el volumen lo exigiera) solo podría borrar
   lo que tenga **más** de cinco años y tendría que ser una decisión nueva,
   con su propia migración, porque los disparadores lo impiden.
3. **La copia fuera del host no se hace todavía.** Cinco años de conservación
   frente a la pérdida de la instancia exigen una copia que no viva en el
   mismo disco: un bucket S3 con versión y bloqueo de objetos (Object Lock en
   modo *compliance*) y una regla de ciclo de vida a Glacier. Eso es gasto
   recurrente fuera del plan gratuito y crear el bucket es un permiso de la
   cuenta AWS que este equipo no ejerce sin aprobación.

## Lo que queda bloqueado y la acción humana exacta

| Bloqueo | Tipo | Acción humana exacta |
|---|---|---|
| Copia de la bitácora fuera del host durante 5 años | COSTE / PERMISO AWS | Aprobar el gasto y crear en la cuenta AWS del proyecto un bucket S3 con versionado y Object Lock (modo *compliance*, retención 5 años) y una regla de ciclo de vida a Glacier; dar al rol del host de plataforma permiso `s3:PutObject` solo sobre ese bucket. Con eso, `respaldar.sh` añade la subida del volcado de `cumplimiento-db` (un cambio de código pequeño, que se hará en cuanto exista el bucket). |

Mientras no exista ese bucket, la bitácora se conserva **íntegra y sin
purga** en la base del host, con respaldos diarios de 7 días en el mismo
disco. Lo que no está cubierto es la pérdida de la instancia de DEV.

## Consecuencias

- Un `TRUNCATE audit_log` ejecutado por cualquiera —también por un
  despliegue o un script de mantenimiento— falla con
  `audit_log es append-only: TRUNCATE no permitido` y queda el incidente en el
  log del servidor. Ningún script del repositorio lo usa (comprobado).
- Restaurar un respaldo sobre una base vacía sigue funcionando: los
  disparadores no impiden `INSERT` ni `CREATE`.
- El informe de cierre del Super Administrador declara este punto como
  **PARCIAL**: inmutable, accesible solo para Super Administradores y
  exportable, sí; cinco años frente a la pérdida de la instancia, no hasta que
  se resuelva el bloqueo de coste.
