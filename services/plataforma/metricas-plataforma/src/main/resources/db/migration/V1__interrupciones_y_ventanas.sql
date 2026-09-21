-- HU-DIS-001: lo unico que hace falta guardar para el informe de disponibilidad.
-- Una fila por caida (no por sondeo) y una por ventana de mantenimiento (DEC-01).
-- Esquema propio del servicio dentro de la instancia compartida (regla 7).

CREATE SCHEMA IF NOT EXISTS metricas;

CREATE TABLE metricas.interrupciones (
    id        BIGSERIAL PRIMARY KEY,
    servicio  VARCHAR(64)  NOT NULL,
    inicio    TIMESTAMPTZ  NOT NULL,
    fin       TIMESTAMPTZ  NULL,
    detalle   TEXT         NOT NULL DEFAULT '',
    CONSTRAINT interrupciones_fin_posterior CHECK (fin IS NULL OR fin >= inicio)
);

-- El informe filtra por servicio y periodo; las abiertas se buscan al arrancar.
CREATE INDEX interrupciones_servicio_inicio ON metricas.interrupciones (servicio, inicio);

CREATE TABLE metricas.ventanas_mantenimiento (
    id      BIGSERIAL PRIMARY KEY,
    inicio  TIMESTAMPTZ NOT NULL,
    fin     TIMESTAMPTZ NOT NULL,
    motivo  TEXT        NOT NULL DEFAULT '',
    CONSTRAINT ventanas_fin_posterior CHECK (fin > inicio)
);
