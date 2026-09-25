-- ms-ecommerce V3 (B5): un carrito por jugador, una linea por producto y la
-- lista de deseos sobre el catalogo maestro.
--
-- 1. UN CARRITO POR JUGADOR. `carritos.usuario_id` no era unico: dos primeras
--    peticiones simultaneas del mismo jugador podian crearle dos carritos, y
--    `findByUsuarioId` reventaba desde entonces con "query did not return a
--    unique result". Antes de poner la restriccion se FUSIONAN los que haya:
--    se conserva el carrito mas antiguo (menor id) y se le pasan las lineas de
--    los demas, que se quedan vacios y se borran. No se pierde ninguna linea.
--
-- 2. UNA LINEA POR PRODUCTO DEL CATALOGO dentro de un carrito. Dos lineas del
--    mismo producto se funden en la mas antigua sumando cantidades, y la
--    cantidad queda en el tope de 20 por linea que fija el contrato 1.3.0
--    (`PUT /carrito/items/{itemId}/cantidad`). Las lineas legadas sin
--    `producto_ref` (anteriores a V2) no se tocan: no se puede saber de que
--    producto del catalogo son.
--
-- 3. LA LISTA DE DESEOS apuntaba (`producto_id`, BIGINT) a la tabla local
--    `productos`, que nace vacia y no es el catalogo: la entidad no podia
--    guardar ningun producto que la tienda vende. Gana la referencia al
--    catalogo maestro, el nombre del producto cuando se guardo (por si el
--    catalogo no responde al listarla) y la fecha, que ordena la lista.
--    Mismo criterio que V2 con el carrito: `producto_id` y su clave foranea se
--    quedan, sin uso, y ninguna fila legada se inventa una referencia.
--
-- Nada de DROP: los unicos DELETE son los carritos y lineas que la fusion deja
-- vacios o repetidos, despues de haber movido su contenido.

-- 1. Carritos repetidos: sus lineas pasan al mas antiguo del jugador.
UPDATE items_carrito AS item
   SET carrito_id = duplicado.conservado
  FROM (SELECT id, MIN(id) OVER (PARTITION BY usuario_id) AS conservado
          FROM carritos) AS duplicado
 WHERE item.carrito_id = duplicado.id
   AND duplicado.id <> duplicado.conservado;

DELETE FROM carritos AS carrito
 USING (SELECT id, MIN(id) OVER (PARTITION BY usuario_id) AS conservado
          FROM carritos) AS duplicado
 WHERE carrito.id = duplicado.id
   AND duplicado.id <> duplicado.conservado;

ALTER TABLE carritos ADD CONSTRAINT carritos_usuario_unico UNIQUE (usuario_id);

-- 2. Lineas repetidas del mismo producto: se suman en la mas antigua.
UPDATE items_carrito AS item
   SET cantidad = repetida.total
  FROM (SELECT carrito_id, producto_ref, MIN(id) AS conservada, SUM(COALESCE(cantidad, 0)) AS total
          FROM items_carrito
         WHERE producto_ref IS NOT NULL
         GROUP BY carrito_id, producto_ref
        HAVING COUNT(*) > 1) AS repetida
 WHERE item.id = repetida.conservada;

DELETE FROM items_carrito AS item
 USING (SELECT carrito_id, producto_ref, MIN(id) AS conservada
          FROM items_carrito
         WHERE producto_ref IS NOT NULL
         GROUP BY carrito_id, producto_ref
        HAVING COUNT(*) > 1) AS repetida
 WHERE item.carrito_id = repetida.carrito_id
   AND item.producto_ref = repetida.producto_ref
   AND item.id <> repetida.conservada;

UPDATE items_carrito
   SET cantidad = 20
 WHERE producto_ref IS NOT NULL
   AND cantidad > 20;

-- El subtotal y el total que quedan son instantaneas; el servidor los
-- recalcula con el catalogo en cada respuesta (contrato 1.4.0).
UPDATE items_carrito
   SET subtotal = precio_unitario * cantidad
 WHERE precio_unitario IS NOT NULL
   AND cantidad IS NOT NULL;

UPDATE carritos AS carrito
   SET total = COALESCE((SELECT SUM(item.subtotal)
                           FROM items_carrito AS item
                          WHERE item.carrito_id = carrito.id), 0);

CREATE UNIQUE INDEX items_carrito_producto_unico
    ON items_carrito (carrito_id, producto_ref)
 WHERE producto_ref IS NOT NULL;

-- 3. Lista de deseos sobre el catalogo maestro.
ALTER TABLE lista_deseos ADD COLUMN producto_ref    VARCHAR(64);
ALTER TABLE lista_deseos ADD COLUMN producto_nombre VARCHAR(255);
ALTER TABLE lista_deseos ADD COLUMN agregado_en     TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX lista_deseos_usuario_producto_unico
    ON lista_deseos (usuario_id, producto_ref)
 WHERE producto_ref IS NOT NULL;

CREATE INDEX lista_deseos_usuario_idx ON lista_deseos (usuario_id, agregado_en DESC);
