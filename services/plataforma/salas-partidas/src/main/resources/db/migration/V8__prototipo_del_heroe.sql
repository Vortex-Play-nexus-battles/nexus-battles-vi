-- El prototipo del heroe, que es lo que el motor de combate sabe buscar.
--
-- El defecto que cierra, destapado por el E2E del corte vertical: a
-- `motor-combate` se le mandaba el NOMBRE PROPIO del heroe -el que le puso su
-- dueno, «Aquiles»- y el motor lo busca en el catalogo de heroes, que indexa
-- por PROTOTIPO -«Guerrero Tanque»-. El catalogo devolvia 404 y ningun ataque
-- se resolvia. Peor aun: el error salia por la cola privada del jugador, que
-- la vista no escuchaba, asi que el combate se quedaba quieto sin decir nada.
--
-- El prototipo se resuelve al construir la ficha, contra
-- `GET /api/v1/productos/{id}`, que ya lo publica. No se pidio ningun cambio a
-- contenido: se consume un campo que ya existia.
--
-- Anulable a proposito, por dos razones distintas y las dos ciertas:
--   1. Las filas anteriores a esta migracion no lo guardaron. De esas no se
--      sabe, y NULL es la verdad; rellenarlas con un prototipo inventado
--      cambiaria el dano de partidas ya jugadas.
--   2. Productos puede no contestar. Sin prototipo el combate degrada, pero la
--      entrada a la sala no: la ficha sigue completa para la barra de vida.
--
-- Por eso NO entra en las restricciones de «ficha completa» de V6 y V7: no es
-- parte de lo que hace pintable a un heroe, es parte de lo que lo hace
-- combatible.

ALTER TABLE partida_participantes
    ADD COLUMN heroe_prototipo varchar(120);

ALTER TABLE participantes_de_sala
    ADD COLUMN heroe_prototipo varchar(120);

COMMENT ON COLUMN partida_participantes.heroe_prototipo IS
    'Prototipo del catalogo de heroes, lo unico que motor-combate sabe buscar. NULL en filas anteriores a V8.';
COMMENT ON COLUMN participantes_de_sala.heroe_prototipo IS
    'Prototipo del catalogo de heroes. Distinto del nombre propio: ese lo eligio el jugador.';
