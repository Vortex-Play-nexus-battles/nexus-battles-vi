-- HU-SUB-001: referencia obligatoria al elemento concreto del inventario.
-- producto_id conserva su tipo UUID y su obligatoriedad: identifica el producto.
-- elemento_inventario_id es una referencia String independiente a su instancia.
-- Requiere una tabla vacia; si hay subastas previas, se necesita una
-- migracion de datos con sus identificadores reales antes de aplicar NOT NULL.
ALTER TABLE subastas
    ADD COLUMN elemento_inventario_id VARCHAR(255) NOT NULL;

-- Una unidad solo puede tener una subasta activa, incluso ante escrituras
-- concurrentes. Las subastas finalizadas quedan fuera del indice.
CREATE UNIQUE INDEX uq_subastas_elemento_inventario_activa
    ON subastas (elemento_inventario_id)
    WHERE estado = 'ACTIVA';
