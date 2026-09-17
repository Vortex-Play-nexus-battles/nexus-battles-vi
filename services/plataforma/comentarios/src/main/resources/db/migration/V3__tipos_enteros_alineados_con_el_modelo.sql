-- Alinea el tipo de comentario_imagenes.orden con lo que mapea JPA.
--
-- RegistroDeComentario.java:54 declara @OrderColumn(name = "orden") sobre la
-- coleccion de imagenes, y Hibernate espera que esa columna sea integer.
-- V1 la creo como SMALLINT, asi que el esquema y el mapeo no coincidian:
-- arrancar con spring.jpa.hibernate.ddl-auto=validate fallaba con
--   "wrong column type encountered in column [orden] in table
--    [comentario_imagenes]; found [int2 (Types#SMALLINT)],
--    but expecting [integer (Types#INTEGER)]"
-- En ejecucion no rompia (el servicio arranca sin validate y PostgreSQL
-- convierte el valor), pero deja el esquema fuera de contrato con el modelo
-- y anula el valor de la verificacion. Lo detecto ArranqueDeLaAplicacionIT.
--
-- Migracion nueva, nunca editar V1: esa version ya esta aplicada en el
-- servidor de desarrollo (Flyway reporto "Current version of schema
-- comentarios: 2") y cambiarla romperia su checksum.
--
-- smallint -> integer es una ampliacion de rango: no pierde datos y
-- PostgreSQL reconstruye solo el indice de la clave primaria
-- (comentario_id, orden).
ALTER TABLE comentario_imagenes
    ALTER COLUMN orden TYPE integer;

-- Mismo desajuste en la calificacion: RegistroDeComentario.java:58 la declara
-- Integer y V1 la creo SMALLINT. La restriccion CHECK (estrellas BETWEEN 1
-- AND 5) sigue vigente: cambiar el tipo de la columna no la toca, y es la que
-- de verdad protege el rango de la calificacion (RF-COM-002).
ALTER TABLE comentarios
    ALTER COLUMN estrellas TYPE integer;
