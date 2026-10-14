-- SA-11 (§7.3.7, RF-NOT-005): hasta donde vio cada jugador los cambios en las
-- politicas de uso.
--
-- Notificaciones lee de admin-parametros lo publicado
-- (GET /api/v1/parametros/politicas/cambios/posteriores,
-- admin-parametros.yaml 1.1.0); el punto de lectura de cada jugador vive aqui,
-- igual que el del catalogo (V3):
--   hasta          la marca del ultimo lote incorporado. Solo avanza (GREATEST
--                  en la escritura), aunque dos sesiones escriban a la vez.
--   consultado_en  cuando se pregunto por ultima vez: no se vuelve a preguntar
--                  antes del intervalo minimo configurado.
CREATE TABLE IF NOT EXISTS cursor_cambios_politica (
    usuario_id    VARCHAR(64) PRIMARY KEY,
    hasta         TIMESTAMPTZ NOT NULL,
    consultado_en TIMESTAMPTZ NOT NULL
);
