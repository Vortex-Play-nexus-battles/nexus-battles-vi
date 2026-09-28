-- B10 (torneos.yaml 1.2.0): cobro y devolucion idempotentes, premio y avisos,
-- con el estado de cada uno persistido por participante.
--
-- Por que una tabla y no llamadas directas: hasta la 1.1.0, iniciar y cancelar
-- recorrian los equipos llamando al libro de creditos DENTRO de la transaccion
-- que cambiaba el estado del torneo. Si el libro fallaba a mitad, la
-- transaccion se deshacia pero los cobros ya hechos no: el torneo seguia en
-- inscripciones con equipos cobrados, y cancelarlo despues «liberaba» reservas
-- ya consumidas sin devolver nada. Ahora el cambio de estado y la operacion que
-- queda por hacer se guardan juntos; la llamada al proveedor va despues, fuera
-- de la transaccion, y se reintenta hasta terminar.
--
-- Aditiva: columnas nuevas nulas y una tabla nueva. No toca nada existente.

-- Premio anunciado al crear el torneo (RF-TOR-007, D-24 provisional). Nulo en
-- los torneos anteriores a esta version: para esos manda la configuracion
-- vigente cuando se juega la final.
ALTER TABLE torneos
    ADD COLUMN premio_creditos_por_integrante INTEGER CHECK (premio_creditos_por_integrante >= 0),
    ADD COLUMN premio_epica_producto_id       VARCHAR(100);

-- Una fila por cosa que hay que hacer para un participante fuera de la base:
-- cobrar o devolver su inscripcion, entregarle su premio o avisarle de un hito.
--
-- `clave` es la clave estable (torneo-<id>-jugador-<uid>-<que>) y es UNICA: dos
-- inicios simultaneos, un reintento o una caida a mitad nunca crean dos cobros
-- del mismo participante. Es tambien la clave de idempotencia que se manda al
-- proveedor cuando este la admite (refId del libro, Idempotency-Key de
-- inventario y de correo, id del aviso en notificaciones).
CREATE TABLE operaciones (
    id                   UUID         PRIMARY KEY,
    torneo_id            UUID         NOT NULL REFERENCES torneos (id),
    equipo_id            UUID         REFERENCES equipos (id),
    jugador_uid          UUID         NOT NULL,
    tipo                 VARCHAR(30)  NOT NULL,
    clave                VARCHAR(150) NOT NULL,
    estado               VARCHAR(20)  NOT NULL,
    -- Cobro y devolucion: la reserva del libro y su monto.
    reserva_id           UUID,
    monto                INTEGER      CHECK (monto >= 0),
    -- Premio: la epica que se entrega y en que va cada parte.
    producto_id          VARCHAR(100),
    sancion_verificada   VARCHAR(20),
    creditos_entregados  BOOLEAN      NOT NULL DEFAULT FALSE,
    epica_entregada      BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Avisos: lo que se le dice al jugador.
    titulo               VARCHAR(200),
    cuerpo               VARCHAR(1000),
    -- Reintentos: cuantos van, cuando toca el siguiente y hasta cuando la
    -- tiene reservada quien la esta ejecutando (si muere a mitad, vence y
    -- otro la retoma).
    intentos             INTEGER      NOT NULL DEFAULT 0,
    proximo_intento      TIMESTAMPTZ  NOT NULL,
    bloqueada_hasta      TIMESTAMPTZ,
    ultimo_error         VARCHAR(500),
    resultado            VARCHAR(200),
    creada_en            TIMESTAMPTZ  NOT NULL,
    actualizada_en       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT operaciones_clave_unica UNIQUE (clave),
    CONSTRAINT operaciones_tipo_valido CHECK (tipo IN
        ('COBRO_INSCRIPCION', 'DEVOLUCION_INSCRIPCION', 'PREMIO', 'AVISO', 'CORREO')),
    CONSTRAINT operaciones_estado_valido CHECK (estado IN
        ('PENDIENTE', 'EN_CURSO', 'REINTENTABLE', 'HECHA', 'FALLIDA', 'EXCLUIDA', 'OMITIDA'))
);

-- La tarea programada busca lo que ya toca intentar.
CREATE INDEX operaciones_por_atender_idx ON operaciones (estado, proximo_intento);
CREATE INDEX operaciones_torneo_idx ON operaciones (torneo_id);
