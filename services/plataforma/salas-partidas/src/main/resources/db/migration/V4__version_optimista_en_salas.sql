-- HU-SAL-002 · control de concurrencia del ultimo cupo.
--
-- Regla 8 de plataforma: los cambios de esquema van en una migracion nueva.
-- V1, V2 (chat) y V3 (participantes) no se tocan.
--
-- POR QUE
-- Dos jugadores que pulsan «Entrar» a la vez sobre una sala con un solo cupo
-- leen la misma sala, los dos la ven con sitio y los dos la guardan. Sin esta
-- columna la segunda escritura pisa a la primera (Hibernate reescribe la
-- coleccion de participantes completa) y uno de los dos recibe un 200 —y un
-- aviso por el canal— por una entrada que la base de datos no conserva.
--
-- Con la version, cada UPDATE de salas lleva `WHERE version = :leida`. La
-- segunda escritura no encuentra la fila con la version que leyo, Hibernate
-- lanza OptimisticLockException, y el caso de uso vuelve a leer la sala, que ya
-- esta LLENA, y responde 409 con el motivo. Nadie pierde una escritura y nadie
-- recibe un aviso por una entrada que no ocurrio.
--
-- Bloqueo optimista y no pesimista a proposito: la contienda es rara (dos
-- personas en el mismo milisegundo sobre la misma sala) y una fila bloqueada
-- por cada lectura del listado seria pagar siempre por un caso que casi nunca
-- pasa. Ver RepositorioSalasJpa.guardar e IngresarASala.

ALTER TABLE salas
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN salas.version IS
    'Marca de concurrencia optimista (JPA @Version). HU-SAL-002.';
