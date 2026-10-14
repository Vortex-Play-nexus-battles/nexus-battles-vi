-- SA-5 (§7.3.5): cada cambio de parametro llega a la bitacora central de
-- ms-cumplimiento desde su propia version, con la IP de quien lo hizo. La
-- version ya se guarda en la transaccion del cambio; EnvioDeAuditoria la
-- entrega despues, con reintentos, y marca auditado_en.
--
-- Hasta aqui el evento salia sin IP y ms-cumplimiento lo rechazaba: ningun
-- cambio anterior llego. Esas versiones siguen en el historial de cada
-- parametro y se marcan con su propia fecha para no enviarlas ahora, sin la
-- IP que la bitacora central exige.
ALTER TABLE versiones ADD COLUMN ip_origen VARCHAR(45);
ALTER TABLE versiones ADD COLUMN auditado_en TIMESTAMPTZ;
ALTER TABLE versiones ADD COLUMN intentos_de_auditoria INTEGER NOT NULL DEFAULT 0;
ALTER TABLE versiones ADD COLUMN error_de_auditoria VARCHAR(300);
ALTER TABLE versiones ADD COLUMN proximo_intento_de_auditoria TIMESTAMPTZ;

UPDATE versiones SET auditado_en = cambiado_en;

CREATE INDEX versiones_por_auditar_idx ON versiones (cambiado_en) WHERE auditado_en IS NULL;
