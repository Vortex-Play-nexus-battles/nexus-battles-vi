-- V3__crear_tabla_operaciones.sql
-- Historial de transacciones de débito y reversa para ms-subastas

CREATE TABLE transaccion_credito (
                                   id              UUID            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
                                   ref_id          VARCHAR(128)    NOT NULL UNIQUE,
                                   jugador_uid     VARCHAR(64)     NOT NULL,
                                   monto           NUMERIC(15,2)   NOT NULL,
                                   concepto        VARCHAR(128)    NOT NULL,
                                   tipo            VARCHAR(32)     NOT NULL,
                                   estado          VARCHAR(32)     NOT NULL DEFAULT 'PROCESADO',
                                   creado          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transaccion_ref_id ON transaccion_credito (ref_id);
