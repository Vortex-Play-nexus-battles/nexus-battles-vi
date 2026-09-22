-- HU-JUE-014 (RF-JUE-014) — Apuesta de creditos en la batalla.
--
-- Regla 8 de plataforma: los cambios de esquema van en una migracion nueva.
-- V1 a V8 no se tocan. Sin prefijo de esquema, igual que ellas: lo fija
-- spring.flyway.schemas, no cada sentencia.
--
-- POR QUE
-- Hasta hoy solo el anfitrion comprometia creditos (salas.id_reserva_creditos,
-- V5), y ni siquiera eso funcionaba de verdad: no habia adaptador contra el
-- libro de creditos y toda sala con recompensa respondia 503. La HU dice que
-- CADA participante pone en juego la recompensa al crear O AL ENTRAR, que la
-- recupera si se va antes de empezar, y que al terminar el ganador se lleva lo
-- de todos. Para devolver o cobrar hay que saber que reserva es de quien.
--
-- 1) La reserva de cada participante viaja en su ficha (misma fila que su
--    heroe, V7): nace en el mismo momento -al entrar- y muere con el -al
--    salir-. La del anfitrion se queda donde estaba (V5); Sala.reservasDeCreditos
--    las junta.
ALTER TABLE participantes_de_sala
    ADD COLUMN id_reserva_creditos uuid;

COMMENT ON COLUMN participantes_de_sala.id_reserva_creditos IS
    'Reserva emitida por el libro de creditos (ms-finanzas) al entrar a una sala con recompensa. Nula si la sala no apuesta o si entro antes de HU-JUE-014. La del anfitrion vive en salas.id_reserva_creditos (V5).';

-- Una reserva pertenece a un solo participante de una sola sala: dos filas que
-- apuntaran a la misma la liberarian o cobrarian dos veces.
CREATE UNIQUE INDEX IF NOT EXISTS ux_participantes_sala_reserva
    ON participantes_de_sala (id_reserva_creditos)
 WHERE id_reserva_creditos IS NOT NULL;

-- 2) Memoria de la deuda. Cuando la partida termina hay que cobrar y pagar en
--    el libro de creditos, que puede no responder en ese instante. La partida
--    ya termino -el ultimo golpe se dio- y no se deshace; lo que no puede pasar
--    es que la liquidacion se olvide (CA-06). Una fila por partida: nace
--    PENDIENTE si el libro fallo, LIQUIDADA cuando se cierra. Nunca se borra.
CREATE TABLE liquidaciones_de_apuesta (
    id_partida      uuid         PRIMARY KEY,
    id_sala         uuid         NOT NULL,
    estado          varchar(20)  NOT NULL,
    intentos        integer      NOT NULL DEFAULT 0,
    ultimo_error    varchar(500),
    creada_en       timestamptz  NOT NULL,
    actualizada_en  timestamptz  NOT NULL,

    CONSTRAINT ck_liquidaciones_estado
        CHECK (estado IN ('PENDIENTE', 'LIQUIDADA')),
    CONSTRAINT ck_liquidaciones_intentos_no_negativos
        CHECK (intentos >= 0),
    CONSTRAINT fk_liquidaciones_partida
        FOREIGN KEY (id_partida) REFERENCES partidas (id),
    CONSTRAINT fk_liquidaciones_sala
        FOREIGN KEY (id_sala) REFERENCES salas (id)
);

-- El reintento periodico lee solo las pendientes, de la mas vieja a la mas nueva.
CREATE INDEX IF NOT EXISTS ix_liquidaciones_pendientes
    ON liquidaciones_de_apuesta (creada_en)
 WHERE estado = 'PENDIENTE';

COMMENT ON TABLE liquidaciones_de_apuesta IS
    'Estado de la liquidacion de la apuesta de cada partida (HU-JUE-014). PENDIENTE = el libro de creditos no respondio y se reintenta; LIQUIDADA = cobrada/pagada o devuelta.';
