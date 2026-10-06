-- HU-COM-007 (#521) CA-01 — por que el filtro automatico retuvo un comentario,
-- contrato comentarios 1.10.0.
--
-- Aditivo: una tabla nueva y nada mas. V1..V6 ya estan aplicadas en el servidor
-- de desarrollo y no se tocan (cambiarlas romperia su checksum).
--
-- Una fila por comentario retenido por el filtro, escrita en la misma
-- transaccion que el comentario. La clave es el propio comentario: la cola
-- (RF-COM-005) las lee en lote por esa clave y el detalle de una en una, sin
-- indice aparte. Los comentarios que llegan a la cola por reportes no tienen
-- fila.
--
-- Nunca se guarda el texto del comentario ni los terminos coincidentes: las
-- reglas van por su id (los `reglas` de moderacion-lista-negra 2.1.0) y el
-- motivo es el mensaje generico de la lista negra o, si no respondio, la razon
-- de la retencion (servicio_no_disponible).
CREATE TABLE IF NOT EXISTS comentario_detecciones (
    comentario_id           VARCHAR(36)   PRIMARY KEY REFERENCES comentarios (id),
    fecha                   TIMESTAMPTZ   NOT NULL,
    reglas                  BIGINT[]      NOT NULL DEFAULT '{}',
    categoria               VARCHAR(32),
    motivo                  VARCHAR(500),
    servicio_no_disponible  BOOLEAN       NOT NULL DEFAULT FALSE
);
