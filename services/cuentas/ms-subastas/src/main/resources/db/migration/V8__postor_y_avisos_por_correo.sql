-- B8 (7.7.8 y 7.7.9 del documento del curso): quien pujo, y avisos por la
-- bandeja Y por correo.

-- 1. El apodo del postor en el momento de pujar (claim `sub` del token).
--    «Alerta de nueva puja recibida con monto y usuario» (7.7.8) y «Usuario
--    que realizo cada puja (anonimizado parcialmente)» (7.7.9): sin guardarlo
--    no hay forma de decir quien pujo sin consultar otro dominio en cada
--    linea. Se guarda completo y se anonimiza al mostrarlo. Las pujas
--    automaticas no tienen token al emitirse: toman el apodo guardado al
--    configurarlas.
ALTER TABLE pujas ADD COLUMN apodo_postor VARCHAR(60);
ALTER TABLE pujas_automaticas ADD COLUMN apodo_jugador VARCHAR(60);

-- 2. Los avisos que exige 7.7.8. Mismo patron que V6: se sustituye la
--    restriccion por la lista completa.
ALTER TABLE notificaciones_pendientes
    DROP CONSTRAINT chk_notificaciones_tipo;

ALTER TABLE notificaciones_pendientes
    ADD CONSTRAINT chk_notificaciones_tipo
        CHECK (tipo IN (
            'SUBASTA_CERRADA_POR_COMPRA_INMEDIATA',
            'LIMITE_AUTOMATICO_ALCANZADO',
            'AUTOMATICA_SIN_SALDO',
            'SUBASTA_PUBLICADA',
            'NUEVA_PUJA',
            'PUJA_REGISTRADA',
            'PUJA_SUPERADA',
            'SUBASTA_GANADA',
            'SUBASTA_VENDIDA',
            'SUBASTA_SIN_OFERTAS',
            'SUBASTA_FINALIZADA',
            'COMPRA_INMEDIATA_EJECUTADA',
            'COMPRA_INMEDIATA_EXITOSA',
            'SUBASTA_CANCELADA',
            'CAMBIO_EN_SUBASTA_SEGUIDA',
            'RECORDATORIO_CIERRE',
            'PRODUCTO_RECOGIDO',
            'PENDIENTE_VENCIDO',
            'PRODUCTO_DEVUELTO'
        ));

-- 3. Cada fila del outbox tiene ahora dos salidas independientes: la bandeja
--    (enviada_en, como hasta ahora) y, si el tipo lo pide, el correo
--    (POST /correos/subasta). Un fallo de una no retiene a la otra.
--
--    El id de la fila deja de ser aleatorio: se deriva de la clave del evento
--    (UUID v3 de «tipo:subasta:destinatario:discriminante»), asi que encolar
--    dos veces el mismo hecho es un INSERT ... ON CONFLICT DO NOTHING, y el id
--    sirve de identificador estable del aviso para notificaciones (409 =
--    ya entregado) y de Idempotency-Key para correo.
--
--    fallida_en: la bandeja rechazo el aviso por algo que reintentar no
--    arregla (4xx distinto de 409). Hasta aqui una fila asi bloqueaba la cola
--    para siempre: el drenador cortaba el lote en el primer fallo.
ALTER TABLE notificaciones_pendientes
    ADD COLUMN titulo                    VARCHAR(200),
    ADD COLUMN fallida_en                TIMESTAMPTZ,
    ADD COLUMN con_correo                BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN asunto                    VARCHAR(200),
    ADD COLUMN correo_estado             VARCHAR(20),
    ADD COLUMN correo_intentos           INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN correo_proximo_intento_en TIMESTAMPTZ,
    ADD COLUMN correo_enviado_en         TIMESTAMPTZ;

ALTER TABLE notificaciones_pendientes
    ADD CONSTRAINT chk_notificaciones_correo_estado
        CHECK (correo_estado IS NULL
               OR correo_estado IN ('PENDIENTE', 'ENVIADO', 'DESCARTADO', 'FALLIDO'));

-- El drenador de correo busca lo pendiente cuyo proximo intento ya llego.
CREATE INDEX idx_notificaciones_correo_pendiente
    ON notificaciones_pendientes (correo_proximo_intento_en, creada_en)
    WHERE con_correo AND correo_estado = 'PENDIENTE';
