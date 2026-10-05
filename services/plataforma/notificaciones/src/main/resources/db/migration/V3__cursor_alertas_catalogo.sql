-- HU-NOT-001 (#532): hasta donde vio cada jugador los cambios del catalogo.
--
-- Notificaciones lee de productos los cambios del catalogo
-- (GET /api/v1/productos/alertas/cambios, productos.yaml 1.6.0), que es de
-- solo lectura: el punto de lectura de cada jugador vive aqui.
--   hasta          la marca del ultimo lote incorporado; se envia como `desde`
--                  en la siguiente consulta. Solo avanza (GREATEST en la
--                  escritura), aunque dos sesiones escriban a la vez.
--   consultado_en  cuando se pregunto por ultima vez a productos: no se
--                  vuelve a preguntar antes del intervalo minimo configurado.
CREATE TABLE IF NOT EXISTS cursor_alertas_catalogo (
    usuario_id    VARCHAR(64) PRIMARY KEY,
    hasta         TIMESTAMPTZ NOT NULL,
    consultado_en TIMESTAMPTZ NOT NULL
);
