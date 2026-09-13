-- HU-SUB-011 (Cristian) -- campos para el listado paginado con filtros.
-- BORRADOR: nombre_producto, tipo_producto, precio_inicial y
-- fecha_publicacion se dejan nullable a proposito, pendientes del diseno
-- conjunto con Edwin (HU-SUB-001). Se endurecen a NOT NULL cuando cierre.

ALTER TABLE subastas
  ADD COLUMN nombre_producto     VARCHAR(255),
    ADD COLUMN tipo_producto       VARCHAR(20),
    ADD COLUMN rareza              VARCHAR(100),
    ADD COLUMN miniatura_url       VARCHAR(500),
    ADD COLUMN descripcion_corta   VARCHAR(500),
    ADD COLUMN habilidades         VARCHAR(1000),
    ADD COLUMN precio_inicial      NUMERIC(19,2),
    ADD COLUMN cantidad_pujas      INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN es_maestro_de_juego BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN fecha_publicacion   TIMESTAMP,
    ADD COLUMN vistas              INTEGER NOT NULL DEFAULT 0;

-- Soporta el filtro por tipo y el listado por defecto (activas, mas
-- recientes primero) que usa HU-SUB-011 en cada carga de pagina.
CREATE INDEX idx_subastas_tipo_producto
  ON subastas (tipo_producto) WHERE estado = 'ACTIVA';

CREATE INDEX idx_subastas_activas_por_fecha
  ON subastas (fecha_publicacion DESC) WHERE estado = 'ACTIVA';
