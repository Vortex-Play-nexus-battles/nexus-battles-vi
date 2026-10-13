-- Auditoria de DEV del 30-sep (notificaciones.yaml 1.2.0).
--
-- El identificador del aviso lo pone el modulo que lo emite, y el contrato no
-- le fijaba largo. La columna era de 64 y torneos manda identificadores de 101
-- a 106 caracteres (torneo-<uuid>-jugador-<uuid>-aviso-<hito>): el insert
-- fallaba y el aviso se perdia. Se amplia a 200 en las dos tablas que lo
-- guardan; la restriccion unica y los indices siguen siendo los mismos.
ALTER TABLE notificaciones ALTER COLUMN aviso_id TYPE VARCHAR(200);
ALTER TABLE notificacion_entregas ALTER COLUMN aviso_id TYPE VARCHAR(200);
