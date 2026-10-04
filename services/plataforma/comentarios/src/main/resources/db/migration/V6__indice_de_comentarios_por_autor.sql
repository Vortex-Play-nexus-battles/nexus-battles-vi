-- HU-COM-005 (#519) — historial de comentarios de un autor para el moderador,
-- contrato comentarios 1.7.0.
--
-- Aditivo: un indice nuevo y nada mas. V1..V5 ya estan aplicadas en el servidor
-- de desarrollo y no se tocan (cambiarlas romperia su checksum).
--
-- Ningun indice anterior empieza por autor_id: idx_comentarios_producto y
-- idx_comentarios_hilo empiezan por producto_id, y uk_calificacion_unica_por_autor
-- por producto_id tambien. Sin este, pedir los comentarios de un autor recorreria
-- toda la tabla y la ordenaria en cada pagina.
--
-- Las columnas siguen el orden de la consulta —del mas reciente al mas antiguo
-- y, a igual fecha, por id— que es el mismo criterio estable de idx_comentarios_hilo:
-- asi PostgreSQL sirve cada pagina leyendo el indice, sin ordenar.
CREATE INDEX IF NOT EXISTS idx_comentarios_por_autor
    ON comentarios (autor_id, fecha_publicacion DESC, id DESC);
