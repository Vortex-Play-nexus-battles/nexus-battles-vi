-- HU-SUB-004, criterios 2 y 4: los avisos que la historia exige
-- ("notificando a quienes hubieran pujado" y "se detiene notificando al
-- alcanzar el limite").
--
-- Outbox transaccional: la intencion del aviso se escribe en la misma
-- transaccion que el cambio de negocio, de modo que un aviso no se pierde si el
-- proceso muere justo despues de cerrar una subasta. El envio real lo hara el
-- microservicio de notificaciones (grupo de Simon), que no entra en el Sprint 2.

CREATE TABLE notificaciones_pendientes (
    id              UUID         NOT NULL PRIMARY KEY,
    tipo            VARCHAR(48)  NOT NULL,
    destinatario_id UUID         NOT NULL,
    subasta_id      UUID         NOT NULL REFERENCES subastas (id),
    detalle         TEXT,
    creada_en       TIMESTAMPTZ  NOT NULL,
    enviada_en      TIMESTAMPTZ,

    CONSTRAINT chk_notificaciones_tipo
        CHECK (tipo IN ('SUBASTA_CERRADA_POR_COMPRA_INMEDIATA', 'LIMITE_AUTOMATICO_ALCANZADO'))
);

-- El drenador busca siempre lo no enviado, en orden de llegada. Indice parcial
-- porque la cola pendiente es una fraccion minima del historial acumulado.
CREATE INDEX idx_notificaciones_pendientes_por_enviar
    ON notificaciones_pendientes (creada_en)
    WHERE enviada_en IS NULL;
