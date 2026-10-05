-- HU-NOT-003 (RF-NOT-003; 7.7.8 vendedor: «Confirmacion de transferencia de
-- creditos recibidos»): el aviso CREDITOS_RECIBIDOS. Mismo patron que V6 y V8:
-- se sustituye la restriccion por la lista completa.
--
-- Sin esta migracion el cierre con ganador y la compra inmediata fallarian al
-- encolar el aviso, porque la restriccion lo rechazaria dentro de la misma
-- transaccion. RestriccionDeTiposDeAvisoTest lo vigila sin necesitar Docker.

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
            'PRODUCTO_DEVUELTO',
            'CREDITOS_RECIBIDOS'
        ));
