-- B8 (7.7.9 del documento del curso): panel de gestion personal.

-- «Lista de seguimiento»: productos marcados para observacion. Una fila por
-- jugador y subasta; seguir dos veces no crea dos filas.
CREATE TABLE seguimientos (
    subasta_id UUID        NOT NULL REFERENCES subastas (id),
    jugador_id UUID        NOT NULL,
    creado_en  TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_seguimientos PRIMARY KEY (subasta_id, jugador_id)
);

CREATE INDEX idx_seguimientos_jugador ON seguimientos (jugador_id, creado_en DESC);

-- «Productos pendientes de recoger»: lo ganado al vencer la subasta, ya
-- pagado y ya del ganador en inventario, pero todavia bajo el bloqueo de la
-- subasta hasta que lo recoge. «Tiempo limite para reclamar (7 dias)».
-- Una subasta adjudicada tiene como mucho un pendiente: la clave es la subasta.
CREATE TABLE pendientes_de_recoger (
    subasta_id             UUID           NOT NULL PRIMARY KEY REFERENCES subastas (id),
    ganador_id             UUID           NOT NULL,
    elemento_inventario_id VARCHAR(255)   NOT NULL,
    monto_pagado           NUMERIC(19, 2) NOT NULL,
    ganada_en              TIMESTAMPTZ    NOT NULL,
    vence_en               TIMESTAMPTZ    NOT NULL,
    estado                 VARCHAR(30)    NOT NULL,
    resuelto_en            TIMESTAMPTZ,
    version                BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT chk_pendientes_estado
        CHECK (estado IN ('PENDIENTE', 'RECOGIDO', 'ENTREGADO_AL_VENCER', 'DEVUELTO_AL_VENDEDOR'))
);

CREATE INDEX idx_pendientes_ganador ON pendientes_de_recoger (ganador_id, estado, vence_en);

CREATE INDEX idx_pendientes_por_vencer
    ON pendientes_de_recoger (vence_en)
    WHERE estado = 'PENDIENTE';

-- «Estadisticas de visualizaciones» (7.7.9) y el orden por popularidad del
-- listado: visualizaciones UNICAS de jugadores con sesion. La columna
-- subastas.vistas existia desde V3 y nunca se incrementaba.
CREATE TABLE vistas_subasta (
    subasta_id UUID        NOT NULL REFERENCES subastas (id),
    jugador_id UUID        NOT NULL,
    vista_en   TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_vistas_subasta PRIMARY KEY (subasta_id, jugador_id)
);
