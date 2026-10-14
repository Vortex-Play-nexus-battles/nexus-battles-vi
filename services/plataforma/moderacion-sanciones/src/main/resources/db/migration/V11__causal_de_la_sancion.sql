-- SA-4 — §7.3.2 «Causales de sancion»: el sistema documenta por cual de las
-- ocho causales del documento oficial se aplica cada sancion.
--
-- Migracion ADITIVA (regla 8): una columna nula. Las sanciones anteriores no
-- tienen causal y no se les inventa ninguna: la interfaz dice «sin causal
-- documentada». Las nuevas la llevan cuando quien sanciona la elige (la
-- consola la exige); el motivo sigue siendo el texto con el hecho concreto.
ALTER TABLE sanciones ADD COLUMN IF NOT EXISTS causal varchar(40);

ALTER TABLE sanciones ADD CONSTRAINT ck_sanciones_causal CHECK (
    causal IS NULL OR causal IN (
        'LENGUAJE_OFENSIVO',
        'ACOSO',
        'FRAUDE',
        'SOFTWARE_NO_AUTORIZADO',
        'SUPLANTACION_DE_IDENTIDAD',
        'VIOLACION_DE_PRIVACIDAD',
        'INCUMPLIMIENTO_DE_TERMINOS',
        'TERMINACION_DE_RELACION_LEGAL'
    )
);

-- El listado global de la consola filtra por causal y por fecha de emision.
CREATE INDEX IF NOT EXISTS ix_sanciones_emitida ON sanciones (emitida_en DESC);
