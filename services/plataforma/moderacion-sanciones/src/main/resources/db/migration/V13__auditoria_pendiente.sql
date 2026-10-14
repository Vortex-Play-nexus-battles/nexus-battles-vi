-- SA-5 (§7.3.5): las acciones administrativas de este servicio camino de la
-- bitacora central de ms-cumplimiento. Cada fila se escribe en la misma
-- transaccion que la accion (emitir, levantar, resolver una apelacion,
-- cambiar la lista negra) y la entrega despues EntregaDeAuditoria, con
-- reintentos. Las filas entregadas se quedan: son la constancia local de que
-- la entrada salio.
CREATE TABLE auditoria_pendiente (
    id                 UUID          PRIMARY KEY,
    tipo_accion        VARCHAR(40)   NOT NULL,
    administrador_id   VARCHAR(64)   NOT NULL,
    afectado           VARCHAR(255)  NOT NULL,
    valor_anterior     VARCHAR(2000),
    valor_nuevo        VARCHAR(2000),
    motivo             VARCHAR(500)  NOT NULL,
    ip_origen          VARCHAR(45)   NOT NULL,
    ocurrido_en        TIMESTAMPTZ   NOT NULL,
    entregado_en       TIMESTAMPTZ,
    intentos           INTEGER       NOT NULL DEFAULT 0,
    ultimo_error       VARCHAR(500),
    proximo_intento_en TIMESTAMPTZ
);

CREATE INDEX idx_auditoria_pendiente_por_entregar
    ON auditoria_pendiente (ocurrido_en)
    WHERE entregado_en IS NULL;
