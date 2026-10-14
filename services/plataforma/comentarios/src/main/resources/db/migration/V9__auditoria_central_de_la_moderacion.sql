-- SA-5 (§7.3.5): cada decision de moderacion llega a la bitacora central de
-- ms-cumplimiento desde su propio asiento. El asiento ya se guarda en la
-- transaccion de la decision; EnvioDeAuditoria lo entrega despues, con
-- reintentos, y marca auditado_en.
--
-- Las decisiones anteriores a esta version nunca llegaron (el evento no tenia
-- la forma de ms-cumplimiento) y se quedan en el historial de moderacion de
-- este servicio: se marcan con su propia fecha para que no se envien ahora,
-- sin IP muchas de ellas, a un registro que no se puede corregir.
ALTER TABLE comentario_moderacion ADD COLUMN auditado_en TIMESTAMPTZ;
ALTER TABLE comentario_moderacion ADD COLUMN intentos_de_auditoria INTEGER NOT NULL DEFAULT 0;
ALTER TABLE comentario_moderacion ADD COLUMN error_de_auditoria VARCHAR(300);
ALTER TABLE comentario_moderacion ADD COLUMN proximo_intento_de_auditoria TIMESTAMPTZ;

UPDATE comentario_moderacion SET auditado_en = fecha;

CREATE INDEX idx_comentario_moderacion_por_auditar
    ON comentario_moderacion (fecha)
    WHERE auditado_en IS NULL;
