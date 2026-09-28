-- =============================================================================
-- V5 — El cofre de verdad (B7, BACKEND-09, cofres.yaml 1.1.0). Aditiva: nada
-- existente se borra ni cambia de significado.
--
-- §7.6: «Cuando un jugador logre acumular veinte (20) créditos en juegos
-- ganados recibirá un cofre con una recompensa aleatoria cada vez que se cumpla
-- la cuota. Este beneficio solo podrá obtenerse dos veces por semana y el
-- contador se reinicia cada vez que complete los créditos».
-- =============================================================================

-- 1) El contador deja de ser semanal: uno por jugador, con lo acumulado hacia
--    el siguiente cofre, y con bloqueo optimista para que dos resultados a la
--    vez no completen dos veces la misma cuota.
--
--    contador_semanal_cofre (V4) se conserva pero ya no se escribe: sumaba
--    también los créditos por participar, que el documento no cuenta, así que
--    su valor no se traslada. Cada jugador empieza su cuenta en cero con B7.
CREATE TABLE contador_de_cofres (
    uid_jugador      VARCHAR(64)  PRIMARY KEY,
    creditos_ganados INT          NOT NULL DEFAULT 0 CHECK (creditos_ganados >= 0),
    version          BIGINT       NOT NULL DEFAULT 0,
    actualizado_en   TIMESTAMPTZ
);

COMMENT ON TABLE contador_de_cofres IS
    'Créditos ganados acumulados hacia el próximo cofre (B7). No es semanal: el tope de dos por semana ISO se cuenta en cofre_entregado.';
COMMENT ON TABLE contador_semanal_cofre IS
    'LEGADO (V4): ya no se escribe desde V5. Mezclaba créditos por participar; ver contador_de_cofres.';

-- 2) El cofre gana su contenido real, el sorteo que lo produjo y el estado de
--    su entrega al inventario. Los cofres anteriores no tenían contenido:
--    quedan como SIN_CONTENIDO, que es la verdad.
ALTER TABLE cofre_entregado
    ADD COLUMN estado_entrega     VARCHAR(20),
    ADD COLUMN entrega_id         VARCHAR(64),
    ADD COLUMN semilla            BIGINT,
    ADD COLUMN tabla_version      VARCHAR(40),
    ADD COLUMN intentos_entrega   INT         NOT NULL DEFAULT 0,
    ADD COLUMN proximo_intento_en TIMESTAMPTZ,
    ADD COLUMN ultimo_error       VARCHAR(300),
    ADD COLUMN version            BIGINT      NOT NULL DEFAULT 0;

UPDATE cofre_entregado SET estado_entrega = 'SIN_CONTENIDO' WHERE estado_entrega IS NULL;

ALTER TABLE cofre_entregado
    ALTER COLUMN estado_entrega SET NOT NULL,
    ADD CONSTRAINT ck_cofre_estado_entrega
        CHECK (estado_entrega IN ('PENDIENTE', 'ENTREGADO', 'SIN_CONTENIDO'));

COMMENT ON COLUMN cofre_entregado.semilla IS
    'Semilla (SecureRandom) del sorteo del contenido: con ella y tabla_version el sorteo se repite.';
COMMENT ON COLUMN cofre_entregado.tabla_version IS
    'Versión de la tabla de contenido con la que se sorteó. PROVISIONAL-DEV-* mientras el PO no fije la definitiva (D-B7-18).';

-- 3) Lo que trae cada cofre: productos del catálogo y cuántas unidades.
CREATE TABLE cofre_premio (
    cofre_id    UUID        NOT NULL REFERENCES cofre_entregado (id),
    orden       INT         NOT NULL,
    producto_id VARCHAR(64) NOT NULL,
    cantidad    INT         NOT NULL CHECK (cantidad >= 1),
    PRIMARY KEY (cofre_id, orden)
);

-- 4) El tope semanal se cuenta por jugador y semana, y las entregas
--    pendientes se buscan por su próximo intento.
CREATE INDEX idx_cofre_uid_semana ON cofre_entregado (uid_jugador, semana_iso);
CREATE INDEX idx_cofre_entrega_pendiente ON cofre_entregado (proximo_intento_en)
    WHERE estado_entrega = 'PENDIENTE';
