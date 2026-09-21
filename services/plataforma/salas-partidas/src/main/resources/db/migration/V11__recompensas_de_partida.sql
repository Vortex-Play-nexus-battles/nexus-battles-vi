-- HU-JUE-012 (RF-JUE-012) — Recompensa por jugar: 2 creditos al ganador de un
-- uno contra uno, 4 a cada ganador de una grupal, 1 por participar. La cifra la
-- acredita ms-finanzas (POST /partidas/resultado); aqui se recuerda si ya se le
-- informo la partida, por si el libro no respondio al terminar.
--
-- Regla 8 de plataforma: los cambios de esquema van en una migracion nueva.
-- V1 a V10 no se tocan. Sin prefijo de esquema, igual que ellas.
--
-- Gemela de liquidaciones_de_apuesta (V9) y separada de ella a proposito
-- (CA-03): la apuesta y la recompensa son dos movimientos distintos en el
-- libro y en el historial del jugador, y uno puede quedar pendiente sin el
-- otro. Una fila por partida; nunca se borra.
CREATE TABLE recompensas_de_partida (
    id_partida     uuid PRIMARY KEY,
    id_sala        uuid NOT NULL,
    estado         varchar(20) NOT NULL,
    intentos       integer NOT NULL DEFAULT 0,
    ultimo_error   varchar(500),
    creada_en      timestamptz NOT NULL,
    actualizada_en timestamptz NOT NULL,
    CONSTRAINT ck_recompensas_estado CHECK (estado IN ('PENDIENTE', 'ACREDITADA')),
    CONSTRAINT fk_recompensas_partida FOREIGN KEY (id_partida) REFERENCES partidas (id),
    CONSTRAINT fk_recompensas_sala FOREIGN KEY (id_sala) REFERENCES salas (id)
);

COMMENT ON TABLE recompensas_de_partida IS
    'HU-JUE-012: si el resultado de la partida ya se informo al libro de creditos. PENDIENTE = el libro no respondio y se reintenta; ACREDITADA = entro (o el libro ya la tenia).';

-- El reintento programado solo mira las pendientes, de la mas vieja a la mas nueva.
CREATE INDEX ix_recompensas_pendientes
    ON recompensas_de_partida (creada_en)
    WHERE estado = 'PENDIENTE';
