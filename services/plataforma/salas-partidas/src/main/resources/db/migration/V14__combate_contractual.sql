-- =============================================================================
-- V14 — Combate contractual (B7, BACKEND-09). Aditiva: ninguna columna ni
-- restriccion existente cambia, y todo lo nuevo admite NULL o trae valor por
-- omision, asi que las partidas y salas anteriores se siguen leyendo igual.
--
-- (V13 la usa B6 para los mensajes directos: esta migracion no depende de ella.)
-- =============================================================================

-- Bloqueo optimista de la partida: dos acciones del mismo turno —un doble clic,
-- dos pestanas— no se aplican las dos. Mismo patron que salas.version (V4).
ALTER TABLE partidas
    ADD COLUMN version        BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN semilla_orden  BIGINT,
    ADD COLUMN finalizada_en  TIMESTAMPTZ,
    ADD COLUMN turno_vence_en TIMESTAMPTZ;

COMMENT ON COLUMN partidas.version IS
    'Marca de concurrencia optimista (JPA @Version). B7.';
COMMENT ON COLUMN partidas.semilla_orden IS
    'Semilla (SecureRandom) con la que se sorteo el orden de turnos al empezar (seccion 6.1.3). NULL en partidas anteriores a V14, que se jugaron en orden de entrada.';
COMMENT ON COLUMN partidas.finalizada_en IS
    'Cuando termino la partida. NULL en curso y en partidas terminadas antes de V14.';
COMMENT ON COLUMN partidas.turno_vence_en IS
    'Cuando se agota el turno en curso. NULL sin limite: el parametro salas.partidas.segundos-por-turno nace sin valor (D-B7-14).';

-- El perfil de combate del heroe (nivel, estadisticas con el equipo, nombres del
-- equipamiento y epicas) y el estado de combate que devuelve el motor (poder,
-- cargas, efectos, acciones disponibles). En JSON: este servicio los guarda y
-- los devuelve, pero quien los interpreta es el motor de combate.
ALTER TABLE partida_participantes
    ADD COLUMN heroe_perfil   TEXT,
    ADD COLUMN estado_combate TEXT;

ALTER TABLE participantes_de_sala
    ADD COLUMN heroe_perfil TEXT;

COMMENT ON COLUMN partida_participantes.heroe_perfil IS
    'JSON: nivel, estadisticas en su nivel con el equipo, equipamiento y epicas. NULL antes de V14.';
COMMENT ON COLUMN partida_participantes.estado_combate IS
    'JSON: estado de combate tal como lo devolvio motor-combate (poder, cargas, efectos, acciones). NULL hasta la primera respuesta del motor.';
COMMENT ON COLUMN participantes_de_sala.heroe_perfil IS
    'JSON: el perfil de combate capturado al entrar, con la misma consulta a inventario que abre la puerta (SCRUM-1074).';

-- Historial de partidas del jugador (GET /partidas/mias).
CREATE INDEX ix_partida_participantes_jugador ON partida_participantes (id_jugador);

-- Busqueda de turnos agotados, solo entre las partidas en curso con limite.
CREATE INDEX ix_partidas_turno_vencido
    ON partidas (turno_vence_en)
    WHERE estado = 'EN_CURSO' AND turno_vence_en IS NOT NULL;
