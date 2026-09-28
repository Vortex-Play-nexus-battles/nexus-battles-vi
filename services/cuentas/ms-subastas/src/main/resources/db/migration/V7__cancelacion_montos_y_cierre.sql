-- B8 (7.7 del documento del curso): cancelacion, montos cobrados y cierre.
--
-- 1. Estado CANCELADA (7.7.10, «Cancelacion de subastas»). Mismo patron que
--    V6 con el tipo de aviso: se sustituye la restriccion por una mas ancha;
--    ninguna fila existente deja de cumplirla.
ALTER TABLE subastas DROP CONSTRAINT chk_subastas_estado;

ALTER TABLE subastas
    ADD CONSTRAINT chk_subastas_estado
        CHECK (estado IN ('ACTIVA', 'ADJUDICADA', 'SIN_ADJUDICACION', 'CANCELADA'));

-- 2. Lo que se cobro y cuando termino.
--    comision_cobrada: la comision de publicacion (Tabla 25), que hasta aqui
--      solo viajaba en la respuesta de publicar. La penalizacion de cancelar
--      es el 50 % de ESTA cifra (7.7.10), asi que tiene que quedar guardada.
--    penalizacion_cobrada: lo que se debito al cancelar.
--    cerrada_en: cuando dejo de estar ACTIVA (historial, tasa de exito).
--    recordatorio_enviado_en: el aviso de 1 hora antes del cierre (7.7.8)
--      sale una sola vez por subasta.
--    apodo_vendedor: el apodo con el que publico (claim `sub` del token), para
--      la ficha de 7.7.9 («Vendedor (nombre/apodo)»). Es una foto: si el
--      vendedor cambia de apodo, la subasta conserva el de entonces.
ALTER TABLE subastas
    ADD COLUMN comision_cobrada        NUMERIC(19, 2),
    ADD COLUMN penalizacion_cobrada    NUMERIC(19, 2),
    ADD COLUMN cerrada_en              TIMESTAMPTZ,
    ADD COLUMN recordatorio_enviado_en TIMESTAMPTZ,
    ADD COLUMN apodo_vendedor          VARCHAR(60);

-- Las subastas publicadas antes de esta migracion pagaron su comision pero no
-- la guardaron. Se reconstruye de la duracion: 24 h = 1 credito, 48 h = 3, y
-- el Maestro de Juego no paga. El umbral de 36 h (y no 24) absorbe cualquier
-- desfase de zona horaria entre fecha_publicacion (TIMESTAMP) y fecha_fin
-- (TIMESTAMPTZ). Sin fecha de publicacion (filas sembradas a mano) no se sabe
-- cuanto se pago, y se deja nulo: una cancelacion no cobra lo que no consta.
UPDATE subastas
   SET comision_cobrada = CASE
           WHEN es_maestro_de_juego THEN 0
           WHEN fecha_fin - fecha_publicacion > INTERVAL '36 hours' THEN 3
           ELSE 1
       END
 WHERE comision_cobrada IS NULL
   AND fecha_publicacion IS NOT NULL;

UPDATE subastas
   SET cerrada_en = fecha_fin
 WHERE estado <> 'ACTIVA'
   AND cerrada_en IS NULL;

-- El tope de 10 subastas activas por vendedor (7.7.10) y «Mis subastas» del
-- panel (7.7.9) cuentan y listan por vendedor y estado.
CREATE INDEX idx_subastas_vendedor_estado ON subastas (vendedor_id, estado);

-- El recordatorio de 1 hora antes del cierre solo mira activas sin avisar.
CREATE INDEX idx_subastas_por_recordar
    ON subastas (fecha_fin)
    WHERE estado = 'ACTIVA' AND recordatorio_enviado_en IS NULL;
