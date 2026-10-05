-- ms-ecommerce V2: el carrito apunta al producto del CATALOGO MAESTRO.
--
-- Hasta V1 cada linea del carrito apuntaba (producto_id, BIGINT) a la tabla
-- local `productos`, que nace vacia y no esta conectada a nada: en AWS la
-- tienda no tenia nada que vender. Ahora la tienda proyecta el catalogo del
-- servicio productos (ids UUID, RF-CAR-001 / RF-CAR-010) y cada linea guarda
-- una instantanea de lo que se agrego:
--
--   producto_ref     id del producto en el catalogo maestro (UUID en texto)
--   producto_nombre  nombre del producto cuando se agrego
--   moneda           moneda de precio_unitario (hoy siempre COP)
--
-- Lo que esta migracion NO hace, a proposito:
--   * No borra ninguna tabla ni columna. `productos` sigue sirviendo al
--     endpoint legado GET /api/v1/productos, y `producto_id` sigue siendo el
--     unico vinculo de las lineas que ya existian. La entidad deja de mapear
--     `producto_id`, y Hibernate en `validate` ignora las columnas que no mapea.
--   * No inserta ningun producto: el catalogo es del servicio productos.
--   * No rellena producto_ref en las lineas legadas: su producto_id es un id
--     de la tabla local, no un id del catalogo maestro, y mezclarlos haria
--     pasar por referencia valida algo que el catalogo no conoce.

ALTER TABLE items_carrito ADD COLUMN producto_ref    VARCHAR(64);
ALTER TABLE items_carrito ADD COLUMN producto_nombre VARCHAR(255);
ALTER TABLE items_carrito ADD COLUMN moneda          VARCHAR(3);

-- Lineas legadas: el nombre sale de la fila local a la que apuntaban.
UPDATE items_carrito AS item
   SET producto_nombre = producto.nombre
  FROM productos AS producto
 WHERE item.producto_id = producto.id;

-- Su precio unitario salia de productos.precio_base_cop: estaba en COP.
UPDATE items_carrito
   SET moneda = 'COP'
 WHERE precio_unitario IS NOT NULL;
