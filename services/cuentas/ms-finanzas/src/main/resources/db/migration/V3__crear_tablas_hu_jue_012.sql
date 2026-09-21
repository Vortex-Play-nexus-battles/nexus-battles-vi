-- HU-JUE-012 · Economía de créditos por partidas.
--
-- Tres tablas nuevas que soportan las dos piezas del criterio:
--   1) idempotencia del resultado de partida (procesar dos veces la misma
--      partida no debe duplicar créditos ni cofres);
--   2) contador semanal de créditos ganados por jugador, para saber cuándo
--      entregar un cofre y respetar el tope de 2 por semana;
--   3) historial de cofres entregados, para la pantalla "Mis cofres" y
--      para la trazabilidad.
--
-- Semana: se guarda como ISO 8601 (YYYY-Www, por ejemplo 2026-W38). La
-- semana empieza el lunes, según el estándar. El día exacto de "reinicio
-- de la semana" que menciona el SRS RF-JUE-013 queda por confirmar con el
-- Product Owner; hasta entonces, lunes ISO es el default sensato.
--
-- El esquema "finanzas" se aplica por default-schema del application.properties,
-- así que no se prefijan las tablas.

CREATE TABLE partida_procesada (
    partida_id   VARCHAR(128) PRIMARY KEY,
    procesado_en TIMESTAMPTZ  NOT NULL
);

-- Contador de créditos ganados por (jugador, semana). Se reinicia SOLO al
-- entregarse un cofre (no al cambiar de semana), porque la ventana de
-- 20 créditos = 1 cofre es intra-semana, y la HU dice que el jugador puede
-- ganar hasta 2 cofres por semana — o sea que después de entregar el
-- primer cofre, el contador vuelve a 0 y puede acumular otros 20 para el
-- segundo cofre. Al llegar a 2 cofres en la semana, no se entrega más
-- aunque el jugador siga acumulando.
CREATE TABLE contador_semanal_cofre (
    uid_jugador       VARCHAR(64)  NOT NULL,
    semana_iso        VARCHAR(10)  NOT NULL,
    creditos_ganados  INT          NOT NULL DEFAULT 0 CHECK (creditos_ganados >= 0),
    cofres_entregados INT          NOT NULL DEFAULT 0 CHECK (cofres_entregados >= 0 AND cofres_entregados <= 2),
    PRIMARY KEY (uid_jugador, semana_iso)
);

CREATE INDEX idx_contador_uid ON contador_semanal_cofre (uid_jugador);

-- Historial. La consulta más común es "los cofres del jugador X, más
-- recientes primero" (pantalla Mis Cofres), por lo que el índice apunta a
-- (uid_jugador, entregado_en DESC).
CREATE TABLE cofre_entregado (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    uid_jugador    VARCHAR(64)  NOT NULL,
    semana_iso     VARCHAR(10)  NOT NULL,
    contenido      VARCHAR(64)  NOT NULL,
    entregado_en   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cofre_uid_entregado_desc ON cofre_entregado (uid_jugador, entregado_en DESC);
