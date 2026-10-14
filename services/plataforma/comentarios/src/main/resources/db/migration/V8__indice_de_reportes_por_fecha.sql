-- SA-4 (§7.3.8, comentarios.yaml 1.15.0) — la frecuencia de reportes de un
-- periodo se lee por fecha: `where fecha >= :desde and fecha < :hasta`.
--
-- Los dos indices de V4 empiezan por el comentario o por el reportante, asi
-- que ninguno sirve un rango solo de fechas: sin este, cada apertura del
-- tablero de moderacion recorreria la tabla entera de reportes (HU-REN-003,
-- busquedas indexadas). Migracion nueva: V1..V7 ya estan aplicadas.
CREATE INDEX IF NOT EXISTS idx_reportes_por_fecha
    ON comentario_reportes (fecha);
