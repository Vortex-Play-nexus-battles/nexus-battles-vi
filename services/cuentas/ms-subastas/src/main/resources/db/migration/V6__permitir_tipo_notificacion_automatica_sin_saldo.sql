-- HU-SUB-004: cuando una puja automatica no puede emitirse porque el jugador
-- agoto su saldo disponible despues de configurarla, se detiene y se le notifica
-- con su propio tipo de aviso, distinguiendolo de haber alcanzado el limite.

ALTER TABLE notificaciones_pendientes
    DROP CONSTRAINT chk_notificaciones_tipo;

ALTER TABLE notificaciones_pendientes
    ADD CONSTRAINT chk_notificaciones_tipo
        CHECK (tipo IN (
            'SUBASTA_CERRADA_POR_COMPRA_INMEDIATA',
            'LIMITE_AUTOMATICO_ALCANZADO',
            'AUTOMATICA_SIN_SALDO'
        ));
