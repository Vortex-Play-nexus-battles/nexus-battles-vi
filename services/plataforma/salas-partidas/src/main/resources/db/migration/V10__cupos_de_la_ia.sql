-- HU-SAL-004 (RF-JUE-004) — Modalidades: contra la IA y hasta seis con
-- cualquier cupo controlado por la IA.
--
-- Regla 8 de plataforma: los cambios de esquema van en una migracion nueva.
-- V1 a V9 no se tocan. Sin prefijo de esquema, igual que ellas.
--
-- POR QUE
-- Hasta hoy la maquina era un booleano (incluir_heroe_ia, V1): una o ninguna,
-- y ademas no ocupaba cupo, asi que una sala "contra la IA" de dos cupos
-- admitia a un segundo humano y jugaban tres. RF-JUE-004 dice que en la de
-- hasta seis "cualquiera puede ser controlado por la IA": son cupos, y hay que
-- contarlos.
--
-- 1) Cuantos cupos son de la maquina. Se rellena desde el booleano: las salas
--    que ya existian con maquina tenian exactamente una.
ALTER TABLE salas
    ADD COLUMN heroes_ia SMALLINT NOT NULL DEFAULT 0;

UPDATE salas
   SET heroes_ia = CASE WHEN incluir_heroe_ia THEN 1 ELSE 0 END;

COMMENT ON COLUMN salas.heroes_ia IS
    'Cupos ocupados por la inteligencia artificial (HU-SAL-004). Cuentan en ocupacion. incluir_heroe_ia (V1) se conserva como resumen: heroes_ia > 0.';

-- Nunca mas maquinas que cupos menos el del anfitrion, que siempre juega.
ALTER TABLE salas
    ADD CONSTRAINT salas_heroes_ia_en_rango
        CHECK (heroes_ia >= 0 AND heroes_ia < maximo_participantes);

-- 2) La ocupacion pasa a contar los cupos de la maquina, como Sala.ocupacion().
--    Las filas antiguas con maquina que ya estaban a tope de humanos podrian
--    quedar por encima del maximo; se recorta al maximo, que es lo que la
--    restriccion de V1 (ocupacion <= maximo_participantes) exige, y la partida
--    -si la hubo- ya esta jugada.
UPDATE salas
   SET ocupacion = LEAST(ocupacion + heroes_ia, maximo_participantes)
 WHERE heroes_ia > 0;

COMMENT ON COLUMN salas.ocupacion IS
    'Cupos ocupados: personas dentro (participantes_de_sala) mas heroes_ia. Derivada; se recalcula en cada escritura.';
