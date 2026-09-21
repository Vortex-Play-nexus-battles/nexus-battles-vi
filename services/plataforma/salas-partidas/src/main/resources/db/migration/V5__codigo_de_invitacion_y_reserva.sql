-- Dos datos que le faltaban a la sala para que dos operaciones del contrato
-- dejaran de ser imposibles: entrar a una sala privada y cancelar una sala
-- devolviendo los creditos.
--
-- Regla 8 de plataforma: los cambios de esquema van en una migracion nueva.
-- V1 a V4 no se tocan. Sin prefijo de esquema, igual que ellas: lo fija
-- spring.flyway.schemas, no cada sentencia.
--
-- POR QUE
-- Hasta hoy Sala.unirse rechazaba TODA sala privada, y lo decia por escrito:
-- no habia forma de que nadie demostrara estar invitado, porque el codigo no
-- existia en ninguna parte -- ni en el modelo, ni en la tabla. El contrato si
-- lo exigia (IngresoRequest.codigoInvitacion), pero ninguna operacion lo
-- entregaba. Resultado: una sala privada no la podia abrir nadie, ni siquiera
-- quien la creo.
--
-- Y cancelarSala tiene que devolver los creditos comprometidos (RF-JUE-014),
-- pero CrearSala descartaba el comprobante de la reserva en cuanto guardaba la
-- sala. Sin saber que reserva liberar, cancelar habria dejado los creditos del
-- anfitrion retenidos para siempre.
--
-- Ver docs/gobierno/ADR-003-codigo-de-invitacion-y-salida-de-sala.md.

ALTER TABLE salas
    ADD COLUMN IF NOT EXISTS codigo_invitacion   varchar(20),
    ADD COLUMN IF NOT EXISTS id_reserva_creditos uuid;

COMMENT ON COLUMN salas.codigo_invitacion IS
    'Llave de acceso de una sala privada. Nula en las publicas. La genera el servidor al crear la sala y solo se le devuelve al anfitrion.';

COMMENT ON COLUMN salas.id_reserva_creditos IS
    'Reserva emitida por el modulo de creditos (RF-JUE-014). Nula si la sala no compromete creditos. Se libera al cancelar la sala.';

-- Las salas privadas que ya existian se quedarian fuera de la restriccion de
-- abajo, asi que primero se les genera un codigo. md5(random()) no es
-- criptografico, pero estas son salas de desarrollo que hoy NO se pueden abrir
-- de ninguna forma: cualquier codigo las mejora. Las nuevas las genera
-- Sala.generarCodigoDeInvitacion con SecureRandom.
UPDATE salas
   SET codigo_invitacion = upper(substr(md5(random()::text || id::text), 1, 4))
                           || '-'
                           || upper(substr(md5(random()::text || id::text), 5, 4))
 WHERE privada AND codigo_invitacion IS NULL;

-- Una sala privada sin codigo seria inaccesible incluso para su anfitrion, y
-- una publica con codigo seria un secreto que no protege nada. Las dos son
-- estados que el modelo no sabe representar, asi que la base los impide.
ALTER TABLE salas
    ADD CONSTRAINT ck_salas_codigo_solo_si_privada
    CHECK ((privada AND codigo_invitacion IS NOT NULL)
        OR (NOT privada AND codigo_invitacion IS NULL));

-- Una reserva pertenece a una sola sala: dos salas que apuntaran a la misma la
-- liberarian dos veces al cancelarse, y el jugador recuperaria creditos que
-- nunca comprometio.
CREATE UNIQUE INDEX IF NOT EXISTS ux_salas_reserva_creditos
    ON salas (id_reserva_creditos)
 WHERE id_reserva_creditos IS NOT NULL;
