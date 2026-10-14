-- SA-5 (§7.3.5) — dos columnas opcionales para los servicios que registran
-- desde una cola propia (moderacion-sanciones, comentarios, admin-parametros):
--
--   ocurrido_en  cuando ocurrio la accion en el servicio de origen. fecha_hora
--                sigue siendo cuando quedo registrada aqui y la fija este
--                servicio: si la entrega llega tarde porque este servicio
--                estuvo caido, la hora exacta de la accion no se pierde.
--   referencia   la clave del evento en el servicio de origen. Una entrega
--                repetida (el origen no supo si la anterior entro) devuelve la
--                entrada que ya existe en vez de crear otra.
--
-- ADD COLUMN sin valor por omision no reescribe ni toca ninguna fila: los
-- disparadores de V1/V2 siguen bloqueando UPDATE y DELETE sobre cada entrada.
ALTER TABLE audit_log ADD COLUMN ocurrido_en TIMESTAMPTZ;
ALTER TABLE audit_log ADD COLUMN referencia  VARCHAR(120);

CREATE UNIQUE INDEX uq_audit_log_referencia ON audit_log (referencia) WHERE referencia IS NOT NULL;
CREATE INDEX idx_audit_log_afectado ON audit_log (afectado);
