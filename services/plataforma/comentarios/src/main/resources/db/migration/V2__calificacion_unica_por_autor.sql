-- La regla de la calificacion unica vive en el dominio, pero el dominio decide
-- sobre el hilo que cargo, y dos solicitudes simultaneas del mismo autor cargan
-- cada una un hilo que no ve a la otra. Este indice es la red de seguridad para
-- esa carrera: la segunda calificacion choca aqui al confirmar y el servicio la
-- traduce a un 409, documentado en el contrato.
CREATE UNIQUE INDEX IF NOT EXISTS uk_calificacion_unica_por_autor
    ON comentarios (producto_id, autor_id)
    WHERE estrellas IS NOT NULL;
