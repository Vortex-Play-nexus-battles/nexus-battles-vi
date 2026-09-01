-- HU-SAL-002 · quienes estan dentro de cada sala.
--
-- Regla 8 de plataforma: los cambios de esquema van en una migracion nueva.
-- V1 no se toca: ya viaja en la rama de HU-SAL-001 y su propia cabecera lo dice.
--
-- POR QUE UNA TABLA Y NO UNA COLUMNA MAS
-- V1 guardaba solo `ocupacion`, un contador. Con eso el aforo cuadra pero se
-- pierde QUIEN esta dentro, y en cuanto la sala se rehidrata desde PostgreSQL
-- las reglas de RF-JUE-002 dejan de poder aplicarse: no hay contra que comparar
-- para saber si alguien ya entro. La consecuencia era real y estaba probada: un
-- mismo jugador podia ocupar dos cupos.

CREATE TABLE IF NOT EXISTS participantes_de_sala (
    id_sala    UUID NOT NULL,
    id_jugador UUID NOT NULL,

    -- La clave compuesta hace imposible el duplicado tambien en la base, no
    -- solo en el dominio. Si algun dia otro camino escribe aqui saltandose
    -- Sala.unirse, PostgreSQL lo rechaza igual.
    CONSTRAINT participantes_de_sala_pk
        PRIMARY KEY (id_sala, id_jugador),

    -- Regla 7: esta tabla es de este servicio y de nadie mas. Al borrar la
    -- sala se van sus participantes; no tiene sentido conservarlos huerfanos.
    CONSTRAINT participantes_de_sala_fk_sala
        FOREIGN KEY (id_sala) REFERENCES salas (id) ON DELETE CASCADE
);

-- El listado de RF-JUE-002 recorre salas, no jugadores, pero la consulta por
-- jugador hara falta para "en que sala estoy": se indexa ahora que la tabla
-- esta vacia y no cuando duela.
CREATE INDEX IF NOT EXISTS participantes_de_sala_idx_jugador
    ON participantes_de_sala (id_jugador);

-- Las salas creadas antes de esta migracion tienen ocupacion pero ningun
-- participante registrado. De todas ellas se conoce con certeza a una persona:
-- su anfitrion, que esta en la propia fila de la sala. Se siembra ese dato para
-- que ninguna sala antigua quede sin participantes.
--
-- No se inventa el resto: si una sala antigua tenia ocupacion 3, los otros dos
-- jugadores son irrecuperables porque nunca se guardaron. La columna
-- `ocupacion` se recalcula abajo para que no afirme algo que no se puede
-- sostener.
INSERT INTO participantes_de_sala (id_sala, id_jugador)
SELECT id, id_anfitrion FROM salas
ON CONFLICT DO NOTHING;

UPDATE salas s
SET ocupacion = (
    SELECT COUNT(*) FROM participantes_de_sala p WHERE p.id_sala = s.id
);

COMMENT ON TABLE participantes_de_sala IS
    'Jugadores dentro de cada sala. HU-SAL-002, RF-JUE-002.';
COMMENT ON COLUMN salas.ocupacion IS
    'Numero de participantes. Derivado de participantes_de_sala; se conserva para listar sin join.';
