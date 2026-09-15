-- V2__crear_tablas_creditos.sql
-- Modelo de créditos para HU-PAG-001

CREATE TABLE cuenta_credito (
                              jugador_uid     VARCHAR(64)     NOT NULL PRIMARY KEY,
                              saldo_bruto     NUMERIC(15,2)   NOT NULL DEFAULT 0.00 CHECK (saldo_bruto >= 0),
                              saldo_reservado NUMERIC(15,2)   NOT NULL DEFAULT 0.00 CHECK (saldo_reservado >= 0),
                              version         BIGINT          NOT NULL DEFAULT 0,
                              creado          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                              actualizado     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                              CONSTRAINT chk_reserva_no_mayor CHECK (saldo_reservado <= saldo_bruto)
);

CREATE TABLE reserva_credito (
                               id              UUID            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
                               jugador_uid     VARCHAR(64)     NOT NULL REFERENCES cuenta_credito(jugador_uid),
                               monto           NUMERIC(15,2)   NOT NULL CHECK (monto > 0),
                               concepto        VARCHAR(128)    NOT NULL,
                               referencia_id   VARCHAR(128)    NOT NULL,
                               idempotency_key VARCHAR(128)    NOT NULL UNIQUE,
                               estado          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVA',
                               creado          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                               expira_en       TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_reserva_jugador_estado ON reserva_credito (jugador_uid, estado);
