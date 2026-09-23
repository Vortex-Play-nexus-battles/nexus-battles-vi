-- HU-TOR-004 (CA-04): una sala puede ser la partida de un encuentro de
-- torneo. Va aparte de `salas` a proposito: es un vinculo opcional entre dos
-- modulos y no una propiedad de la sala. Al terminar la partida, salas-partidas
-- informa el ganador a torneos por API (nunca a mano).
CREATE TABLE encuentros_de_torneo (
    sala_id        UUID PRIMARY KEY REFERENCES salas (id),
    torneo_id      UUID        NOT NULL,
    numero         INTEGER     NOT NULL CHECK (numero BETWEEN 1 AND 14),
    vinculado_por  UUID        NOT NULL,
    vinculado_en   TIMESTAMPTZ NOT NULL,
    informado_en   TIMESTAMPTZ,
    ultimo_fallo   VARCHAR(500)
);
